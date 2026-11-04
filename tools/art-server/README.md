# slot art server

Real cart labels and disc prints for slot-android, served from your own machine (a home server, a Raspberry Pi,
an Ubuntu box) for your own devices.

- The app asks the server for a game's art; the server looks the game up on **ScreenScraper** with *your* account,
  downloads the scan of the cart label or disc print, and keeps it. ScreenScraper is asked **once per game**, ever;
  every later request, from any of your devices, comes from the server's cache.
- Your ScreenScraper login stays in `config.ini` on the server. The devices only know the server's address.
- Nothing is published anywhere: it serves your devices on your network, for your own use.
- Any image you put in `overrides/<system>/<rom name without extension>.png` (or `.jpg`, `.webp`) wins over
  ScreenScraper, e.g. `overrides/psx/Tekken 3 (USA).png`.
- It is a single Python 3 file using only the standard library.

System folder names: `gb gbc gba nes snes md sms gg pce psx ps2 psp n64 gc dc xbox x360 ps3`.

## Set up (Ubuntu)

```bash
sudo mkdir -p /opt/slot-art && sudo cp slot_art_server.py config.example.ini slot-art.service /opt/slot-art/
cd /opt/slot-art && sudo cp config.example.ini config.ini && sudo nano config.ini   # your ScreenScraper user + password
sudo useradd --system --home /opt/slot-art slot 2>/dev/null; sudo chown -R slot: /opt/slot-art && sudo chmod 600 config.ini
```

Check your login with one game before starting the server:

```bash
sudo -u slot python3 /opt/slot-art/slot_art_server.py --test psx "Tekken 3 (USA).bin"
```

- `Saved to /opt/slot-art/cache/psx/....png`: it works.
- `ScreenScraper answered 403 ... identifiants développeur`: ScreenScraper wants developer credentials as well. Ask for
  them on the ScreenScraper forum (they are free for free, non-commercial software), put them in `devid` /
  `devpassword` in `config.ini`, and run the test again.
- `401`/`login`: check `user` / `password`. `429`/`430`: too many requests or today's quota is used up; try later.

Run it as a service:

```bash
sudo cp /opt/slot-art/slot-art.service /etc/systemd/system/ && sudo systemctl daemon-reload
sudo systemctl enable --now slot-art
journalctl -u slot-art -f      # shows each game it fetches
```

Open `http://<server ip>:8787/` in a browser: it shows how many images are cached per system. If the devices can't
reach it, allow the port: `sudo ufw allow 8787/tcp`.

## In the app

**Settings > Library > Art server**, enter `http://<server ip>:8787` (e.g. `http://192.168.1.10:8787`) and Save. The
carts and discs reload with the server's art; anything the server has nothing for keeps the usual art (libretro
thumbnails, Wikipedia, slot's Game Boy labels). Away from home the app simply uses what it already cached.

Games the server found nothing for are asked again after 30 days (`retry_missing_after_days`); delete the `.miss`
files in `cache/<system>/` to retry sooner. To change which art it fetches (e.g. add `box-2D`), edit `media` in
`config.ini`.
