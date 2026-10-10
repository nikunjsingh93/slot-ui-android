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
 *  2. the libretro thumbnail server (box art or title screen; the file list of each set is cached and the
 *     ROM name is matched loosely against it, so "Gran Turismo 4.iso" finds "Gran Turismo 4 (USA) (v1.01)"),
 *  3. the cover shown on the game's English Wikipedia page,
 *  4. nothing, and the cart gets a generated label.
 * Whatever is found is cached on disk.
 */
class Covers(private val ctx: Context) {
    private val cache = File(ctx.filesDir, "covers").also { it.mkdirs() }
    private val indexDir = File(cache, "index").also { it.mkdirs() }
    private val custom = File(ctx.getExternalFilesDir(null), "labels").also { it.mkdirs() }
    private val gate = Semaphore(3)
    private val indexLock = Any()
    private val indexes = HashMap<String, List<String>>()

    suspend fun load(game: Game, allowNetwork: Boolean): ImageBitmap? = withContext(Dispatchers.IO) {
        if (Apps.isApp(game)) return@withContext Apps.icon(ctx, Apps.packageOf(game))
        for (ext in listOf("png", "jpg", "jpeg", "webp")) {
            val f = File(custom, "${game.stem}.$ext")
            if (f.exists()) decode(f)?.let { return@withContext it }
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

    /** The server's file list for one set, cached for a month. Loaders share one download rather than each fetching the list. */
    private fun index(dir: String, kind: String): List<String> = synchronized(indexLock) {
        val key = "$dir/$kind"
        indexes[key]?.let { return it }
        val f = File(indexDir, key.replace(Regex("[^A-Za-z0-9]+"), "_") + ".txt")
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < INDEX_MS
        val got = if (fresh) f.readLines() else {
            val parsed = getText("https://thumbnails.libretro.com/${enc(dir)}/$kind/")?.let(CoverMatch::parseListing).orEmpty()
            if (parsed.isNotEmpty()) { f.writeText(parsed.joinToString("\n")); parsed } else if (f.exists()) f.readLines() else emptyList()
        }
        if (got.isNotEmpty()) indexes[key] = got
        got
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

    private fun request(url: String): ByteArray? = try {
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
        private const val RETRY_MS = 7L * 24 * 3600 * 1000
        private const val INDEX_MS = 30L * 24 * 3600 * 1000
    }
}
