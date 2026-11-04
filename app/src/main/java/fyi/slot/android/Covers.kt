package fyi.slot.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import fyi.slot.ui.CartShape
import fyi.slot.ui.CoverMatch
import fyi.slot.ui.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Label art for the carts. In order of preference:
 *  1. a PNG/JPG you drop into `Android/data/fyi.slot.android/files/labels/<rom name>.png`
 *     (same idea as slot's custom cart labels),
 *  2. for Game Boy, Color and Advance: a scan of the real label from slot's own art server (art.slot-cfw.fyi,
 *     matched by the ROM's CRC32), or failing that the game's logo on a label of its colour,
 *  3. the libretro thumbnail server (box art or title screen; the file list of each set is cached and the
 *     ROM name is matched loosely against it, so "Gran Turismo 4.iso" finds "Gran Turismo 4 (USA) (v1.01)"),
 *  4. the cover shown on the game's English Wikipedia page,
 *  5. nothing, and the cart gets a generated label.
 * Whatever is found is cached on disk.
 */
class Covers(private val ctx: Context) {
    private val cache = File(ctx.filesDir, "covers").also { it.mkdirs() }
    private val indexDir = File(cache, "index").also { it.mkdirs() }
    private val custom = File(ctx.getExternalFilesDir(null), "labels").also { it.mkdirs() }
    private val gate = Semaphore(3)
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val indexes = java.util.concurrent.ConcurrentHashMap<String, CoverMatch.Index>()

    suspend fun load(game: Game, allowNetwork: Boolean, server: String = ""): ImageBitmap? = withContext(Dispatchers.IO) {
        if (Apps.isApp(game)) return@withContext Apps.icon(ctx, Apps.packageOf(game))
        for (ext in listOf("png", "jpg", "jpeg", "webp")) {
            val f = File(custom, "${game.stem}.$ext")
            if (f.exists()) decode(f)?.let { return@withContext it }
        }
        // your own art server (Settings > Library > Art server): real disc and cart scans for every system
        if (server.isNotEmpty()) {
            // keyed by the server, so art from one server is never taken for another's
            val tag = Scanner.sha1(server).take(8)
            val got = File(cache, "${game.id}.srv-$tag.png")
            if (got.exists()) decode(got)?.let { return@withContext it }
            val none = File(cache, "${game.id}.srv-$tag.miss")
            if (allowNetwork && !(none.exists() && System.currentTimeMillis() - none.lastModified() < SERVER_RETRY_MS)) {
                val bytes = try {
                    fromServer(server, game, none)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.let { bmp ->
                    got.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
                    return@withContext scaled(bmp).asImageBitmap()
                }
            }
        }
        // Game Boy, Color and Advance: the real label from slot's own art server, matched by the ROM's CRC32
        if (game.system in SLOT_ART_SYSTEMS) {
            val real = File(cache, "${game.id}.label.png")
            if (real.exists()) decode(real)?.let { return@withContext it }
            val realMiss = File(cache, "${game.id}.label.miss")
            if (allowNetwork && !(realMiss.exists() && System.currentTimeMillis() - realMiss.lastModified() < RETRY_MS)) {
                val bmp = try {
                    gate.withPermit { fromSlotArt(game) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (bmp != null) {
                    real.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
                    return@withContext scaled(bmp).asImageBitmap()
                }
                if (online) realMiss.writeText("x")
            }
        }
        val hit = File(cache, "${game.id}.png")
        if (hit.exists()) return@withContext decode(hit)
        val miss = File(cache, "${game.id}.miss")
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < RETRY_MS) return@withContext null
        if (!allowNetwork) return@withContext null

        val bytes = try {
            gate.withPermit { fromLibretro(game) ?: fromWikipedia(game) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        // an empty answer may just mean no network right now: only remember it when the lookup really ran
        val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bmp == null) { if (bytes == null && online) miss.writeText("x"); return@withContext null }
        hit.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        scaled(bmp).asImageBitmap()
    }

    /** Set by the fetches: false after a failure to reach the server at all, so a dead connection isn't cached as "no cover". */
    @Volatile private var online = true

    private fun decode(f: File): ImageBitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, opts)
        var sample = 1
        while (opts.outWidth / (sample * 2) >= MAX_W) sample *= 2
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })?.let { scaled(it).asImageBitmap() }
    }.getOrNull()

    private fun scaled(b: Bitmap): Bitmap =
        if (b.width <= MAX_W) b else Bitmap.createScaledBitmap(b, MAX_W, (b.height * MAX_W.toFloat() / b.width).toInt().coerceAtLeast(1), true)

    // --- your own art server (tools/art-server) --------------------------------------------------------

    /**
     * Asks your server for the game's label: `GET <server>/label/<system>/<rom file name>?size=..&crc=..`.
     * 200 is the image, 404 means the server (and ScreenScraper behind it) has none, which is remembered in [none];
     * anything else (server down, away from home) is not remembered, so it is asked again next time.
     */
    private fun fromServer(server: String, game: Game, none: File): ByteArray? {
        val name = fileName(game) ?: return null
        // carts are small enough to checksum, which lets the server match the exact dump; discs go by name
        val crc = if (game.size in 1..MAX_CRC_BYTES) romCrc(game)?.let { "%08X".format(it) } else null
        val url = "$server/label/${enc(game.system.id)}/${enc(name)}?size=${game.size}" + (crc?.let { "&crc=$it" } ?: "")
        return try {
            (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = 4000; readTimeout = 60000
                setRequestProperty("User-Agent", AGENT)
                val code = responseCode
                when (code) {
                    200 -> inputStream.use { it.readBytes() }
                    404 -> { none.writeText("x"); null }
                    else -> null
                }.also { disconnect() }
            }
        } catch (_: java.io.IOException) {
            null
        }
    }

    /** The ROM's own file name ("Gran Turismo 4 (USA).iso"), from a file path or a folder document id. */
    private fun fileName(game: Game): String? {
        val uri = android.net.Uri.parse(game.uri)
        val last = (if (uri.scheme == "file") uri.path else uri.lastPathSegment) ?: return null
        return last.substringAfterLast('/').substringAfterLast(':').takeIf { it.isNotEmpty() }
    }

    // --- slot's art server (art.slot-cfw.fyi) --------------------------------------------------------

    /** One game in slot's art index: a scan of the real label, the game's logo, and the label colour behind it. */
    private class SlotArt(val real: String?, val wheel: String?, val ground: Int?)

    @Volatile private var slotIndex: Map<Long, SlotArt>? = null

    /** slot's index (CRC32 -> art), cached on disk and refreshed weekly with an ETag so an unchanged index is not downloaded again. */
    private fun slotIndex(): Map<Long, SlotArt> {
        slotIndex?.let { return it }
        synchronized(this) {
            slotIndex?.let { return it }
            val f = File(indexDir, "slot-art.json")
            val tag = File(indexDir, "slot-art.etag")
            if (!f.exists() || System.currentTimeMillis() - f.lastModified() > SLOT_INDEX_MS) {
                try {
                    (URL(SLOT_ART + "index.json").openConnection() as HttpURLConnection).run {
                        connectTimeout = 8000; readTimeout = 30000
                        setRequestProperty("User-Agent", AGENT)
                        if (f.exists() && tag.exists()) setRequestProperty("If-None-Match", tag.readText())
                        when (responseCode) {
                            200 -> {
                                val body = inputStream.use { it.readBytes() }
                                File(indexDir, "slot-art.part").also { it.writeBytes(body) }.renameTo(f)
                                getHeaderField("ETag")?.let { tag.writeText(it) }
                            }
                            304 -> f.setLastModified(System.currentTimeMillis())
                        }
                        disconnect()
                    }
                    online = true
                } catch (_: java.io.IOException) {
                    online = false
                }
            }
            if (!f.exists()) return emptyMap()
            val json = JSONObject(f.readText())
            val out = HashMap<Long, SlotArt>(json.length() * 2)
            for (key in json.keys()) {
                val o = json.optJSONObject(key) ?: continue
                val ground = o.optJSONObject("look")?.optString("ground")?.takeIf { it.length == 6 }?.toIntOrNull(16)
                val art = SlotArt(o.optString("support-texture").ifEmpty { null }, o.optString("wheel").ifEmpty { null }, ground)
                key.toLongOrNull(16)?.let { out[it] = art }
                o.optJSONArray("crcs")?.let { a -> for (i in 0 until a.length()) a.optString(i).toLongOrNull(16)?.let { out[it] = art } }
            }
            return out.also { slotIndex = it }
        }
    }

    private fun fromSlotArt(game: Game): Bitmap? {
        val index = slotIndex()
        if (index.isEmpty()) return null
        val art = index[romCrc(game) ?: return null] ?: return null
        // the server keeps a smaller .webp beside each .png
        fun image(path: String): Bitmap? =
            (get(SLOT_ART + path.removeSuffix(".png") + ".webp") ?: get(SLOT_ART + path))?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        art.real?.let(::image)?.let { return it }
        return art.wheel?.let(::image)?.let { logoLabel(it, art.ground, game) }
    }

    /** No scan of the label: the game's logo on a label of its own colour (what slot calls a house label). */
    private fun logoLabel(logo: Bitmap, ground: Int?, game: Game): Bitmap {
        val l = fyi.slot.ui.CartSpecs.of(game).label
        val w = MAX_W
        val h = (w * l.height / l.width).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawColor(android.graphics.Color.rgb(((ground ?: 0x2A2D33) shr 16) and 0xFF, ((ground ?: 0x2A2D33) shr 8) and 0xFF, (ground ?: 0x2A2D33) and 0xFF))
        val s = minOf(w * .82f / logo.width, h * .72f / logo.height)
        val lw = logo.width * s
        val lh = logo.height * s
        c.drawBitmap(logo, null, android.graphics.RectF((w - lw) / 2f, (h - lh) / 2f, (w + lw) / 2f, (h + lh) / 2f), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** CRC32 of the ROM itself (of the file inside a .zip), as the No-Intro sets and slot's index name it. */
    private fun romCrc(game: Game): Long? = runCatching {
        val uri = android.net.Uri.parse(game.uri)
        val input = if (uri.scheme == "file") File(uri.path!!).inputStream() else ctx.contentResolver.openInputStream(uri)
        input?.buffered(1 shl 16)?.use { raw ->
            if (game.entryName.isEmpty()) crcOf(raw) else {
                val zin = java.util.zip.ZipInputStream(raw)
                var e = zin.nextEntry
                while (e != null && e.name != game.entryName) e = zin.nextEntry
                e?.let { crcOf(zin) }
            }
        }
    }.getOrNull()

    private fun crcOf(s: java.io.InputStream): Long {
        val crc = java.util.zip.CRC32()
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = s.read(buf)
            if (n < 0) break
            crc.update(buf, 0, n)
        }
        return crc.value
    }

    // --- libretro thumbnails -----------------------------------------------------------------------

    private fun kinds(game: Game): List<String> = when {
        game.system.external && game.system.shape != CartShape.N64 -> listOf("Named_Boxarts", "Named_Titles", "Named_Snaps")
        else -> listOf("Named_Titles", "Named_Boxarts", "Named_Snaps")
    }

    private fun fromLibretro(game: Game): ByteArray? {
        val dir = game.system.thumbDir
        if (dir.isEmpty()) return null
        val plain = game.stem.map { if (it in "&*/:`<>?\\|\"") '_' else it }.joinToString("")
        // a correctly named ROM is found with one small request, without downloading a set's whole file list
        for (kind in kinds(game).take(2)) get("https://thumbnails.libretro.com/${enc(dir)}/$kind/${enc(plain)}.png")?.let { return it }
        for (kind in kinds(game)) {
            val names = index(dir, kind)
            val name = CoverMatch.best(game.stem, names)
            if (name != null) get("https://thumbnails.libretro.com/${enc(dir)}/$kind/${enc(name)}.png")?.let { return it }
        }
        return null
    }

    /** The server's file list for one set, parsed once and cached on disk for a month. Each set has its own lock, so a big download doesn't hold up the other shelves. */
    private fun index(dir: String, kind: String): CoverMatch.Index {
        val key = "$dir/$kind"
        indexes[key]?.let { return it }
        synchronized(locks.getOrPut(key) { Any() }) {
            indexes[key]?.let { return it }
            val f = File(indexDir, key.replace(NOT_ALNUM, "_") + ".txt")
            val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < INDEX_MS
            val got = if (fresh) f.readLines() else {
                val parsed = getText("https://thumbnails.libretro.com/${enc(dir)}/$kind/")?.let(CoverMatch::parseListing).orEmpty()
                if (parsed.isNotEmpty()) { f.writeText(parsed.joinToString("\n")); parsed } else if (f.exists()) f.readLines() else emptyList()
            }
            return CoverMatch.Index(got).also { if (got.isNotEmpty()) indexes[key] = it }
        }
    }

    // --- Wikipedia -----------------------------------------------------------------------------------

    /** The infobox cover of the game's article, found by searching "<title> <system> video game". */
    private fun fromWikipedia(game: Game): ByteArray? {
        val title = game.stem.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ").replace(Regex("\\s+"), " ").trim()
        if (title.length < 2) return null
        val search = getText(
            "https://en.wikipedia.org/w/api.php?action=query&list=search&srlimit=6&format=json&srsearch=" +
                enc("$title ${game.system.title} video game"),
        ) ?: return null
        val hits = JSONObject(search).optJSONObject("query")?.optJSONArray("search") ?: return null
        for (i in 0 until hits.length()) {
            val page = hits.getJSONObject(i).optString("title")
            val bare = page.replace(Regex("\\s*\\((?:[^)]*video game|[^)]*game)\\)$"), "")
            if (CoverMatch.best(title, listOf(bare)) == null) continue
            val file = infoboxImage(page) ?: continue
            val info = getText(
                "https://en.wikipedia.org/w/api.php?action=query&prop=imageinfo&iiprop=url&iiurlwidth=$MAX_W&format=json&titles=" + enc("File:$file"),
            ) ?: continue
            val url = JSONObject(info).optJSONObject("query")?.optJSONObject("pages")?.let { pages ->
                pages.keys().asSequence().mapNotNull { pages.getJSONObject(it).optJSONArray("imageinfo")?.optJSONObject(0) }.firstOrNull()
            }?.let { it.optString("thumburl").ifEmpty { it.optString("url") } }?.takeIf { it.isNotEmpty() } ?: continue
            get(url)?.let { return it }
        }
        return null
    }

    private fun infoboxImage(page: String): String? {
        val body = getText(
            "https://en.wikipedia.org/w/api.php?action=query&prop=revisions&rvprop=content&rvslots=main&rvsection=0&format=json&titles=" + enc(page),
        ) ?: return null
        val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return null
        val wikitext = pages.keys().asSequence()
            .mapNotNull { pages.getJSONObject(it).optJSONArray("revisions")?.optJSONObject(0)?.optJSONObject("slots")?.optJSONObject("main")?.optString("*") }
            .firstOrNull() ?: return null
        val raw = Regex("\\|\\s*image\\s*=\\s*([^\\n|]+)").find(wikitext)?.groupValues?.get(1)?.trim() ?: return null
        val name = raw.removePrefix("File:").removePrefix("Image:").substringBefore("[[").trim()
        return name.takeIf { it.isNotEmpty() && !it.endsWith(".svg", true) }
    }

    // --- http ------------------------------------------------------------------------------------------

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun get(url: String): ByteArray? = request(url)

    private fun getText(url: String): String? = request(url)?.toString(Charsets.UTF_8)

    private fun request(url: String): ByteArray? = fetch(url)

    private fun fetch(url: String): ByteArray? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 8000; readTimeout = 15000
            setRequestProperty("User-Agent", "slot-android/0.1 (https://github.com/nikunjsingh93/slot-ui-android)")
            val code = responseCode
            online = true
            if (code != 200) { disconnect(); null } else inputStream.use { it.readBytes() }.also { disconnect() }
        }
    } catch (e: java.io.IOException) {
        if (e is java.io.FileNotFoundException) null else { online = false; null }
    }

    companion object {
        private const val MAX_W = 640
        private const val AGENT = "slot-android/0.1 (https://github.com/nikunjsingh93/slot-ui-android)"
        /** slot's own label art (scans of the real labels, by ROM CRC32): Game Boy, Game Boy Color and Game Boy Advance. */
        private const val SLOT_ART = "https://art.slot-cfw.fyi/"
        private val SLOT_ART_SYSTEMS = setOf(fyi.slot.ui.GameSystem.GB, fyi.slot.ui.GameSystem.GBC, fyi.slot.ui.GameSystem.GBA)
        private const val SLOT_INDEX_MS = 7L * 24 * 3600 * 1000
        /** A game your art server had nothing for is asked again after this long (you may have added art since). */
        private const val SERVER_RETRY_MS = 3L * 24 * 3600 * 1000
        private const val MAX_CRC_BYTES = 64L * 1024 * 1024
        private val NOT_ALNUM = Regex("[^A-Za-z0-9]+")
        private const val RETRY_MS = 7L * 24 * 3600 * 1000
        private const val INDEX_MS = 30L * 24 * 3600 * 1000
    }
}
