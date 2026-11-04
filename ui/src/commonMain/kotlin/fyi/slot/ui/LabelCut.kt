package fyi.slot.ui

import kotlin.math.abs

/**
 * Finds the printed label in a photo of a whole cartridge (LaunchBox "Cart - Front" images of N64, SNES and NES
 * carts), so only the label is put on slot's own drawn cart.
 *
 * The photos show the cart filling the picture. The plastic is one colour (grey, the gold Zelda carts...), so the
 * colour round the edge of the photo is taken as the shell; the label is the biggest block of rows and columns that
 * are mostly *not* that colour.
 */
object LabelCut {
    /** A rectangle as fractions of the photo's width and height. */
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /**
     * [px] are ARGB pixels of the photo (any size; around 200 px wide is plenty), [w] x [h].
     * Returns null when no clear label is found (the caller then keeps its usual art).
     */
    fun find(px: IntArray, w: Int, h: Int): Box? {
        if (w < 20 || h < 20) return null
        val shell = shellColour(px, w, h) ?: return null
        val off = BooleanArray(w * h) { i -> opaque(px[i]) && dist(px[i], shell) > 70 }
        // columns that are mostly label, over the rows the cart covers
        val cols = run(FloatArray(w) { x -> (0 until h).count { y -> off[y * w + x] } / h.toFloat() }, 0.30f) ?: return null
        val rows = run(FloatArray(h) { y -> (cols.first..cols.last).count { x -> off[y * w + x] } / (cols.last - cols.first + 1f) }, 0.45f) ?: return null
        val bw = (cols.last - cols.first + 1f) / w
        val bh = (rows.last - rows.first + 1f) / h
        // a label covers a good part of the cart; anything tiny or the whole photo is not one
        // (no label runs across nearly the whole cart: that is a shell the colour test did not separate, like the gold Zelda carts)
        if (bw < 0.25f || bh < 0.2f || bw > 0.88f || bh > 0.97f) return null
        // a hair inside, so no shell shows round the edge
        val ix = 0.012f
        val iy = 0.012f
        return Box(cols.first.toFloat() / w + ix, rows.first.toFloat() / h + iy, (cols.last + 1f) / w - ix, (rows.last + 1f) / h - iy)
    }

    private fun opaque(c: Int) = (c ushr 24) > 200

    private fun dist(a: Int, b: Int): Int =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) + abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))

    /** The most common colour (coarsely binned) in a band round the edge of the photo. */
    private fun shellColour(px: IntArray, w: Int, h: Int): Int? {
        val band = maxOf(2, minOf(w, h) / 14)
        val bins = HashMap<Int, IntArray>() // bin -> count, r, g, b sums
        fun add(c: Int) {
            if (!opaque(c)) return
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val k = (r / 24) * 10000 + (g / 24) * 100 + b / 24
            val s = bins.getOrPut(k) { IntArray(4) }
            s[0]++; s[1] += r; s[2] += g; s[3] += b
        }
        for (y in 0 until h) for (x in 0 until w) {
            if (x < band || y < band || x >= w - band || y >= h - band) add(px[y * w + x])
        }
        val top = bins.values.maxByOrNull { it[0] } ?: return null
        return (0xFF shl 24) or ((top[1] / top[0]) shl 16) or ((top[2] / top[0]) shl 8) or (top[3] / top[0])
    }

    /** The longest run of indices whose value is at least [min] (allowing small gaps of a couple of lines). */
    private fun run(v: FloatArray, min: Float): IntRange? {
        var best: IntRange? = null
        var start = -1
        var gap = 0
        val maxGap = maxOf(1, v.size / 60)
        for (i in 0..v.size) {
            val on = i < v.size && v[i] >= min
            if (on) { if (start < 0) start = i; gap = 0; continue }
            if (start >= 0 && i < v.size && gap < maxGap) { gap++; continue }
            if (start >= 0) {
                val end = i - 1 - gap
                if (best == null || end - start > best.last - best.first) best = start..end
                start = -1; gap = 0
            }
        }
        return best
    }
}
