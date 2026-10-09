package fyi.slot.android

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import fyi.slot.ui.Sfx
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Interface sounds, all synthesised: the cart sounds are short plastic clicks and thumps (no voice),
 * the menu sounds are soft blips.
 */
class Sounds(private val ctx: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = HashMap<Sfx, Int>()
    var enabled = true
    private val noise = FloatArray(4096).also { val r = Random(7); for (i in it.indices) it[i] = r.nextFloat() * 2f - 1f }

    init {
        ids[Sfx.Insert] = pool.load(render("insert_v2", 0.30f) { t -> insertSample(t) }, 1)
        ids[Sfx.Eject] = pool.load(render("eject_v2", 0.22f) { t -> ejectSample(t) }, 1)
        ids[Sfx.Tick] = pool.load(render("tick_v2", 0.03f) { t -> blip(t, 1500f, 0.028f, .35f) }, 1)
        ids[Sfx.Select] = pool.load(render("select_v2", 0.09f) { t -> blip(t, 880f, 0.035f, .4f) + blip(t - 0.035f, 1320f, 0.05f, .4f) }, 1)
        ids[Sfx.Back] = pool.load(render("back_v2", 0.09f) { t -> blip(t, 660f, 0.035f, .4f) + blip(t - 0.035f, 440f, 0.05f, .4f) }, 1)
    }

    fun play(effect: Sfx) {
        if (!enabled) return
        val vol = when (effect) { Sfx.Insert, Sfx.Eject -> 1f; else -> .7f }
        ids[effect]?.let { pool.play(it, vol, vol, 1, 0, 1f) }
    }

    fun release() = pool.release()

    // --- synthesis -------------------------------------------------------------------------------------

    private fun blip(t: Float, hz: Float, len: Float, gain: Float): Float {
        if (t < 0f || t > len) return 0f
        val env = (1f - t / len).let { it * it }
        return (sin(2.0 * PI * hz * t) * env * gain).toFloat()
    }

    /** A short burst of filtered noise: the tick of plastic on plastic. */
    private fun click(t: Float, at: Float, gain: Float, decay: Float): Float {
        val x = t - at
        if (x < 0f) return 0f
        val env = exp(-x * decay)
        val n = noise[((x * 22050f).toInt()) % noise.size]
        return n * env * gain
    }

    /** A low thump: a sine that falls in pitch as it dies. */
    private fun thump(t: Float, at: Float, gain: Float, hz0: Float, hz1: Float): Float {
        val x = t - at
        if (x < 0f) return 0f
        val hz = hz1 + (hz0 - hz1) * exp(-x * 40f)
        return (sin(2.0 * PI * hz * x) * exp(-x * 26f) * gain).toFloat()
    }

    /** Pins finding the contacts, then the cart seating with a soft clunk. */
    private fun insertSample(t: Float): Float =
        click(t, 0.00f, .22f, 160f) + click(t, 0.03f, .12f, 220f) +
            thump(t, 0.11f, .85f, 200f, 70f) + click(t, 0.11f, .35f, 120f)

    private fun ejectSample(t: Float): Float =
        thump(t, 0.00f, .55f, 120f, 60f) + click(t, 0.01f, .3f, 110f) + click(t, 0.075f, .18f, 200f)


    private fun render(name: String, seconds: Float, f: (Float) -> Float): String {
        val file = File(ctx.cacheDir, "sfx_$name.wav")
        if (file.exists()) return file.absolutePath
        val rate = 22050
        val n = (rate * seconds).toInt()
        val pcm = ByteArrayOutputStream()
        for (i in 0 until n) {
            val v = (f(i / rate.toFloat()).coerceIn(-1f, 1f) * 32767f).toInt()
            pcm.write(v and 0xff); pcm.write((v shr 8) and 0xff)
        }
        val data = pcm.toByteArray()
        file.outputStream().use { o ->
            fun i32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            fun i16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
            o.write("RIFF".toByteArray()); o.write(i32(36 + data.size)); o.write("WAVEfmt ".toByteArray())
            o.write(i32(16)); o.write(i16(1)); o.write(i16(1)); o.write(i32(rate)); o.write(i32(rate * 2)); o.write(i16(2)); o.write(i16(16))
            o.write("data".toByteArray()); o.write(i32(data.size)); o.write(data)
        }
        return file.absolutePath
    }
}
