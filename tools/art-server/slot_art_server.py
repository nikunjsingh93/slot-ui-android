#!/usr/bin/env python3
"""
slot art server: real cart labels and disc prints for slot-android, from your own machine.

The app asks  GET /label/<system>/<rom file name>?size=<bytes>&crc=<CRC32>
and gets the image (200) or nothing (404). The first time a game is asked for, the server looks it up on
ScreenScraper with *your* account, downloads the scan of the cart label / disc print ("support-texture"),
and keeps it in its cache, so every device after that (and every later request) is served from the cache
and ScreenScraper is asked only once per game. Your login stays in config.ini on this machine.

An image you put in overrides/<system>/<rom name without extension>.png (or .jpg/.webp) always wins.

Only the Python 3 standard library is used.  Run:   python3 slot_art_server.py
Check your login and one game without starting the server:
    python3 slot_art_server.py --test psx "Tekken 3 (USA).bin"
"""

import configparser
import hashlib
import http.server
import json
import os
import re
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))

# slot-android system id -> ScreenScraper systemeid
SYSTEMS = {
    "gb": 9, "gbc": 10, "gba": 12, "nes": 3, "snes": 4, "md": 1, "sms": 2, "gg": 21, "pce": 31,
    "psx": 57, "ps2": 58, "psp": 61, "n64": 14, "gc": 13, "dc": 23, "xbox": 32, "x360": 33, "ps3": 59,
}
API = "https://api.screenscraper.fr/api2/"
IMAGE_TYPES = {"png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg", "webp": "image/webp"}


class Config:
    def __init__(self, path):
        c = configparser.ConfigParser()
        if not c.read(path):
            sys.exit(f"No config at {path}: copy config.example.ini to config.ini and fill it in.")
        ss = c["screenscraper"]
        self.devid = ss.get("devid", "")
        self.devpassword = ss.get("devpassword", "")
        self.softname = ss.get("softname", "slot-android-art-server")
        self.user = ss.get("user", "")
        self.password = ss.get("password", "")
        self.media = [m.strip() for m in ss.get("media", "support-texture, support-2D").split(",") if m.strip()]
        self.regions = [r.strip() for r in ss.get("regions", "us, wor, eu, ss, jp").split(",") if r.strip()]
        self.gap = ss.getfloat("seconds_between_requests", 1.2)
        srv = c["server"] if c.has_section("server") else {}
        self.host = srv.get("host", "0.0.0.0")
        self.port = int(srv.get("port", "8787"))
        self.cache = os.path.join(HERE, srv.get("cache", "cache"))
        self.overrides = os.path.join(HERE, srv.get("overrides", "overrides"))
        self.miss_days = float(srv.get("retry_missing_after_days", "30"))


class Busy(Exception):
    """ScreenScraper is unreachable, over quota or refused the login: nothing is cached, the app will ask again."""


def clean_title(name):
    stem = name.rsplit(".", 1)[0] if "." in name else name
    if stem.lower().endswith(".xiso"):
        stem = stem[:-5]
    stem = re.sub(r"[(\[][^)\]]*[)\]]", " ", stem)
    return re.sub(r"\s+", " ", stem).strip()


def words(s):
    return set(re.findall(r"[a-z0-9]+", s.lower())) - {"the", "a", "of", "and"}


class ScreenScraper:
    def __init__(self, cfg):
        self.cfg = cfg
        self.lock = threading.Lock()  # free accounts get one thread: one request at a time
        self.last = 0.0

    def _get(self, url, binary=False):
        with self.lock:
            wait = self.cfg.gap - (time.time() - self.last)
            if wait > 0:
                time.sleep(wait)
            try:
                req = urllib.request.Request(url, headers={"User-Agent": self.cfg.softname})
                with urllib.request.urlopen(req, timeout=60) as r:
                    body = r.read()
            except urllib.error.HTTPError as e:
                body = e.read()
                if e.code == 404:
                    return None
                text = body[:200].decode("utf-8", "replace")
                raise Busy(f"ScreenScraper answered {e.code}: {text}")
            except (urllib.error.URLError, TimeoutError, OSError) as e:
                raise Busy(f"ScreenScraper unreachable: {e}")
            finally:
                self.last = time.time()
        if binary:
            return body
        try:
            return json.loads(body.decode("utf-8", "replace"))
        except ValueError:
            text = body[:200].decode("utf-8", "replace")
            if "non trouv" in text.lower() or "not found" in text.lower():
                return None
            raise Busy(f"ScreenScraper sent something unexpected: {text}")

    def _params(self, **extra):
        p = {"devid": self.cfg.devid, "devpassword": self.cfg.devpassword, "softname": self.cfg.softname,
             "ssid": self.cfg.user, "sspassword": self.cfg.password, "output": "json"}
        p.update({k: v for k, v in extra.items() if v not in (None, "")})
        return urllib.parse.urlencode(p)

    def game(self, system_id, name, size, crc):
        """The game's record, by the ROM (checksum and/or name), then by a title search."""
        info = self._get(API + "jeuInfos.php?" + self._params(
            systemeid=system_id, romtype="rom", romnom=name, romtaille=size or None, crc=crc))
        jeu = (info or {}).get("response", {}).get("jeu")
        if jeu:
            return jeu
        title = clean_title(name)
        if len(title) < 2:
            return None
        found = self._get(API + "jeuRecherche.php?" + self._params(systemeid=system_id, recherche=title))
        want = words(title)
        for j in (found or {}).get("response", {}).get("jeux", []) or []:
            names = [n.get("text", "") for n in j.get("noms", []) or []]
            if any(want and len(want & words(n)) >= max(1, int(len(want) * 0.7)) for n in names):
                return j
        return None

    def pick(self, jeu):
        """The first wanted media type, in your preferred region order (any region if none of them)."""
        medias = jeu.get("medias", []) or []
        for kind in self.cfg.media:
            of_kind = [m for m in medias if m.get("type") == kind and m.get("url")]
            # multi-disc games list one per disc: take disc 1
            of_kind = [m for m in of_kind if str(m.get("support", m.get("supportnum", "1")) or "1") in ("1", "")] or of_kind
            if not of_kind:
                continue
            for region in self.cfg.regions:
                for m in of_kind:
                    if m.get("region") == region:
                        return m
            return of_kind[0]
        return None

    def image(self, media):
        return self._get(media["url"], binary=True)


class Store:
    def __init__(self, cfg, ss):
        self.cfg, self.ss = cfg, ss
        self.locks = {}
        self.locks_lock = threading.Lock()

    def _lock(self, key):
        with self.locks_lock:
            return self.locks.setdefault(key, threading.Lock())

    def override(self, system, name):
        stem = name.rsplit(".", 1)[0]
        for ext in IMAGE_TYPES:
            p = os.path.join(self.cfg.overrides, system, f"{stem}.{ext}")
            if os.path.isfile(p):
                return p
        return None

    def label(self, system, name, size, crc):
        """Path of the cached image, or None when there is none. Raises Busy when ScreenScraper can't be asked."""
        o = self.override(system, name)
        if o:
            return o
        key = hashlib.sha1(f"{system}/{name.lower()}".encode()).hexdigest()
        folder = os.path.join(self.cfg.cache, system)
        os.makedirs(folder, exist_ok=True)
        with self._lock(key):
            for ext in IMAGE_TYPES:
                p = os.path.join(folder, f"{key}.{ext}")
                if os.path.isfile(p):
                    return p
            miss = os.path.join(folder, f"{key}.miss")
            if os.path.isfile(miss) and time.time() - os.path.getmtime(miss) < self.cfg.miss_days * 86400:
                return None
            jeu = self.ss.game(SYSTEMS[system], name, size, crc)
            media = self.ss.pick(jeu) if jeu else None
            data = self.ss.image(media) if media else None
            if not data:
                with open(miss, "w") as f:
                    json.dump({"name": name, "found_game": bool(jeu), "at": int(time.time())}, f)
                print(f"  none   {system}/{name}", flush=True)
                return None
            ext = (media.get("format") or "png").lower()
            ext = ext if ext in IMAGE_TYPES else "png"
            path = os.path.join(folder, f"{key}.{ext}")
            with open(path + ".part", "wb") as f:
                f.write(data)
            os.replace(path + ".part", path)
            title = next((n.get("text") for n in jeu.get("noms", []) or [] if n.get("text")), "")
            with open(os.path.join(folder, f"{key}.json"), "w") as f:
                # what this is, for you; no URLs (ScreenScraper's carry your login)
                json.dump({"name": name, "title": title, "type": media.get("type"), "region": media.get("region")}, f)
            print(f"  got    {system}/{name}  ->  {title} [{media.get('type')} {media.get('region')}]", flush=True)
            return path


def make_handler(store):
    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):
            pass

        def _send(self, code, body=b"", ctype="text/plain; charset=utf-8"):
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            u = urllib.parse.urlparse(self.path)
            parts = [urllib.parse.unquote(p) for p in u.path.split("/") if p]
            if not parts:
                counts = {}
                if os.path.isdir(store.cfg.cache):
                    for s in sorted(os.listdir(store.cfg.cache)):
                        files = os.listdir(os.path.join(store.cfg.cache, s))
                        counts[s] = {"images": sum(f.rsplit(".", 1)[-1] in IMAGE_TYPES for f in files),
                                     "not_found": sum(f.endswith(".miss") for f in files)}
                return self._send(200, json.dumps({"slot_art_server": True, "cache": counts}, indent=1).encode(),
                                  "application/json")
            if len(parts) != 3 or parts[0] != "label" or parts[1] not in SYSTEMS or "/" in parts[2] or parts[2].startswith("."):
                return self._send(404, b"not found")
            q = urllib.parse.parse_qs(u.query)
            size = (q.get("size") or [""])[0]
            crc = (q.get("crc") or [""])[0].upper()
            size = size if size.isdigit() else ""
            crc = crc if re.fullmatch(r"[0-9A-F]{8}", crc) else ""
            try:
                path = store.label(parts[1], parts[2], size, crc)
            except Busy as e:
                print(f"  busy   {parts[1]}/{parts[2]}: {e}", flush=True)
                return self._send(503, str(e).encode())
            if not path:
                return self._send(404, b"no art")
            with open(path, "rb") as f:
                body = f.read()
            self._send(200, body, IMAGE_TYPES[path.rsplit(".", 1)[-1].lower()])

    return Handler


def main():
    cfg = Config(os.environ.get("SLOT_ART_CONFIG", os.path.join(HERE, "config.ini")))
    store = Store(cfg, ScreenScraper(cfg))
    if len(sys.argv) >= 4 and sys.argv[1] == "--test":
        system, name = sys.argv[2], sys.argv[3]
        if system not in SYSTEMS:
            sys.exit(f"Unknown system {system}; one of: {', '.join(SYSTEMS)}")
        try:
            path = store.label(system, name, "", "")
        except Busy as e:
            sys.exit(f"Could not ask ScreenScraper: {e}")
        print(f"Saved to {path}" if path else "ScreenScraper has no label or disc scan for that game.")
        return
    server = http.server.ThreadingHTTPServer((cfg.host, cfg.port), make_handler(store))
    print(f"slot art server on http://{cfg.host}:{cfg.port}  (cache: {cfg.cache})", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
