package fyi.slot.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import fyi.slot.ui.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Label art for the carts. In order of preference:
 *  1. a PNG/JPG you drop into `Android/data/fyi.slot.android/files/labels/<rom name>.png`
 *     (same idea as slot's custom cart labels),
 *  2. a title-screen image from the libretro thumbnails server (cached on disk),
 *  3. nothing, and the cart gets a generated label.
 */
class Covers(private val ctx: Context) {
    private val cache = File(ctx.filesDir, "covers").also { it.mkdirs() }
    private val custom = File(ctx.getExternalFilesDir(null), "labels").also { it.mkdirs() }
    private val gate = Semaphore(3)

    suspend fun load(game: Game, allowNetwork: Boolean): ImageBitmap? = withContext(Dispatchers.IO) {
        for (ext in listOf("png", "jpg", "jpeg", "webp")) {
            val f = File(custom, "${game.stem}.$ext")
            if (f.exists()) decode(f)?.let { return@withContext it }
        }
        val hit = File(cache, "${game.id}.png")
        if (hit.exists()) return@withContext decode(hit)
        val miss = File(cache, "${game.id}.none")
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < RETRY_MS) return@withContext null
        if (!allowNetwork) return@withContext null

        val bytes = gate.withPermit {
            if (game.system.external && game.system.shape != fyi.slot.ui.CartShape.N64) fetch(game, "Named_Boxarts") ?: fetch(game, "Named_Titles")
            else fetch(game, "Named_Titles") ?: fetch(game, "Named_Boxarts")
        }
        if (bytes == null) { miss.writeText("x"); return@withContext null }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run { miss.writeText("x"); return@withContext null }
        hit.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        scaled(bmp).asImageBitmap()
    }

    private fun decode(f: File): ImageBitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, opts)
        var sample = 1
        while (opts.outWidth / (sample * 2) >= MAX_W) sample *= 2
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })?.let { scaled(it).asImageBitmap() }
    }.getOrNull()

    private fun scaled(b: Bitmap): Bitmap =
        if (b.width <= MAX_W) b else Bitmap.createScaledBitmap(b, MAX_W, (b.height * MAX_W.toFloat() / b.width).toInt().coerceAtLeast(1), true)

    private fun fetch(game: Game, kind: String): ByteArray? {
        val name = game.stem.map { if (it in "&*/:`<>?\\|\"") '_' else it }.joinToString("")
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val url = "https://thumbnails.libretro.com/${enc(game.system.thumbDir)}/$kind/${enc(name)}.png"
        return runCatching {
            (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = 7000; readTimeout = 9000
                setRequestProperty("User-Agent", "slot-android/0.1")
                if (responseCode != 200) { disconnect(); return null }
                inputStream.use { it.readBytes() }.also { disconnect() }
            }
        }.getOrNull()
    }

    companion object {
        private const val MAX_W = 640
        private const val RETRY_MS = 7L * 24 * 3600 * 1000
    }
}
