package fyi.slot.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.hypot

/** A raised/recessed feature on the shell, drawn embossed (light up-left, dark down-right). */
class Relief(val path: Path, val raised: Boolean)

/** A round disc: the label is a circle, with a clear hub ring and a centre hole. */
class DiscSpec(val cx: Float, val cy: Float, val radius: Float, val labelRadius: Float, val hubRadius: Float, val holeRadius: Float, val rimText: Boolean = false)

/**
 * Cart geometry in "design units". The GB family and GBA use slot's own artwork
 * (crates/slot-ui/assets, GPL-3.0); the other systems are drawn procedurally.
 */
class CartSpec(
    val w: Float,
    val h: Float,
    val outline: Path,
    val label: Rect,
    val labelRadius: Float,
    /** Plastic rim thickness used for translucent shells. */
    val rim: Float,
    val relief: List<Relief>,
    val strokeRelief: List<Pair<Path, Float>> = emptyList(),
    /** Where the bottom of the cart's PCB edge connector sits, as fraction of height. */
    val pinsTop: Float = 0.9f,
    /** Y range (design units) for the printed lettering line. */
    val letteringY: Float = 12f,
    val disc: DiscSpec? = null,
)

private fun svg(d: String, evenOdd: Boolean = false): Path =
    PathParser().parsePathString(d).toPath().also { if (evenOdd) it.fillType = PathFillType.EvenOdd }

private fun combine(vararg ds: Pair<String, Boolean>): Path {
    val out = Path()
    out.fillType = PathFillType.EvenOdd
    for ((d, eo) in ds) {
        val p = svg(d, eo)
        // Each sub-path keeps its own winding, so merge as an even-odd union of disjoint shapes.
        out.addPath(p)
    }
    return out
}

private fun translated(p: Path, dx: Float, dy: Float): Path = Path().also { it.addPath(p, Offset(dx, dy)) }

/** Polygon with every corner rounded by [r] (quadratic corners). */
fun roundedPoly(pts: List<Offset>, r: Float): Path {
    val path = Path()
    val n = pts.size
    for (i in 0 until n) {
        val prev = pts[(i + n - 1) % n]
        val cur = pts[i]
        val next = pts[(i + 1) % n]
        val rr = minOf(r, dist(prev, cur) / 2f, dist(cur, next) / 2f)
        val a = towards(cur, prev, rr)
        val b = towards(cur, next, rr)
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quadraticTo(cur.x, cur.y, b.x, b.y)
    }
    path.close()
    return path
}

private fun dist(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)
private fun towards(from: Offset, to: Offset, d: Float): Offset {
    val len = dist(from, to)
    if (len == 0f) return from
    return Offset(from.x + (to.x - from.x) * d / len, from.y + (to.y - from.y) * d / len)
}

private fun pts(vararg xy: Float) = List(xy.size / 2) { Offset(xy[it * 2], xy[it * 2 + 1]) }

private fun rectPath(x: Float, y: Float, w: Float, h: Float) = Path().apply { addRect(Rect(x, y, x + w, y + h)) }

/** A row of `n` horizontal grip slots. */
private fun gripSlots(x: Float, y: Float, w: Float, gap: Float, thick: Float, n: Int, mirrorTo: Float? = null): Path {
    val p = Path()
    for (i in 0 until n) {
        p.addRect(Rect(x, y + i * gap, x + w, y + i * gap + thick))
        if (mirrorTo != null) p.addRect(Rect(mirrorTo - w, y + i * gap, mirrorTo, y + i * gap + thick))
    }
    return p
}

object CartSpecs {
    private val cache = HashMap<String, CartSpec>()

    fun of(game: Game): CartSpec = when (game.system.shape) {
        CartShape.GBA -> gba
        CartShape.GB, CartShape.GBC -> if (isNotchedGb(game)) gbNotched else gbRounded
        CartShape.NES -> cache.getOrPut("nes") { nes() }
        CartShape.SNES -> cache.getOrPut("snes") { snes() }
        CartShape.GENESIS -> cache.getOrPut("md") { genesis() }
        CartShape.CARD -> cache.getOrPut(if (game.system == GameSystem.GG) "gg" else "sms") { card(game.system == GameSystem.GG) }
        CartShape.HUCARD -> cache.getOrPut("pce") { hucard() }
        CartShape.N64 -> cache.getOrPut("n64") { n64() }
        CartShape.DISC_PSX -> cache.getOrPut("psx") { disc(240f, labelR = 116f, hub = 36f, hole = 13f) }
        CartShape.DISC_PS2 -> cache.getOrPut("ps2") { disc(240f, labelR = 116f, hub = 38f, hole = 13f) }
        CartShape.DISC_GC -> cache.getOrPut("gc") { disc(200f, labelR = 96f, hub = 30f, hole = 11f) }
        CartShape.DISC_DC -> cache.getOrPut("dc") { disc(240f, labelR = 116f, hub = 36f, hole = 13f) }
        CartShape.UMD -> cache.getOrPut("umd") { umd() }
        CartShape.NDS -> cache.getOrPut("nds") { ndsCard() }
        CartShape.N3DS -> cache.getOrPut("n3ds") { n3dsCard() }
        CartShape.SWITCH -> cache.getOrPut("switch") { switchCard() }
        CartShape.APP_CARD -> cache.getOrPut("app") { appCard() }
    }

    // --- GBA: crates/slot-ui/assets/cart.svg + cart_detail.svg ------------------------------

    private const val GBA_OUTLINE =
        "M 10.72 0.00 C 10.62 0.00 10.52 0.02 10.42 0.05 L 7.52 0.82 C 7.42 0.85 7.33 0.89 7.24 0.95 L 5.14 2.16 C 5.05 2.21 4.98 2.27 4.91 2.34 L 3.13 4.11 C 3.06 4.18 3.00 4.26 2.94 4.34 L 1.21 7.37 C 1.16 7.45 1.12 7.54 1.09 7.64 L 0.04 11.56 C 0.01 11.65 0.00 11.76 0.00 11.85 L 0.00 25.57 C 0.00 25.77 0.05 25.96 0.15 26.14 L 1.13 27.83 C 1.28 28.09 1.53 28.28 1.82 28.36 L 3.63 28.84 C 3.73 28.87 3.83 28.89 3.93 28.89 L 6.49 28.89 L 6.49 136.73 C 6.50 136.93 6.55 137.13 6.65 137.3 L 7.65 139.04 C 7.75 139.21 7.89 139.35 8.07 139.45 L 10.49 140.85 C 10.66 140.95 10.86 141 11.06 141 L 82.44 141 L 157.56 141 L 228.94 141 C 229.14 141 229.34 140.95 229.51 140.85 L 231.93 139.45 C 232.10 139.35 232.24 139.21 232.34 139.04 L 233.35 137.3 C 233.45 137.13 233.50 136.93 233.50 136.73 L 233.50 28.89 L 236.06 28.89 C 236.16 28.89 236.26 28.87 236.36 28.84 L 238.17 28.36 C 238.46 28.28 238.71 28.09 238.86 27.83 L 239.84 26.14 C 239.94 25.97 240.00 25.77 240.00 25.57 L 240.00 11.85 C 240.00 11.75 239.99 11.65 239.96 11.56 L 238.91 7.64 C 238.88 7.54 238.85 7.45 238.80 7.37 L 237.05 4.34 C 237.00 4.26 236.94 4.18 236.87 4.11 L 235.10 2.34 C 235.03 2.27 234.95 2.21 234.86 2.16 L 232.75 0.95 C 232.67 0.90 232.58 0.85 232.48 0.82 L 229.58 0.05 C 229.48 0.02 229.39 0.00 229.29 0.00 L 153.74 0.00 L 86.25 0.00 L 10.72 0.00 Z"
    private const val GBA_RIDGE =
        "M 115.75 7.38 C 107.02 7.43 82.33 9.89 71.84 12.22 C 58.15 15.27 46.73 19.66 33.02 22.44 C 33.02 22.44 69.23 15.81 73.49 15.09 C 98.02 10.96 119.30 11.51 119.30 11.51 L 120.59 11.51 C 120.59 11.51 141.88 10.96 166.40 15.09 C 170.67 15.81 206.87 22.44 206.87 22.44 C 193.17 19.66 181.74 15.27 168.06 12.22 C 157.22 9.81 131.27 7.28 123.35 7.38 L 119.93 7.38 C 119.93 7.38 116.03 7.38 115.75 7.38 Z"
    private const val GBA_CUT =
        "M 97.96 127.22 C 97.40 127.21 96.91 127.61 96.81 128.17 C 96.72 128.73 97.05 129.27 97.59 129.44 L 115.99 135.71 C 116.02 135.73 116.05 135.75 116.08 135.76 L 119.14 136.56 C 119.33 136.61 119.53 136.61 119.72 136.56 L 122.79 135.76 C 122.82 135.75 122.85 135.73 122.87 135.71 L 141.26 129.44 C 141.80 129.27 142.13 128.73 142.05 128.17 C 141.95 127.62 141.47 127.21 140.90 127.22 L 119.43 127.22 L 97.96 127.22 Z M 104.81 129.5 L 119.43 129.5 L 134.05 129.5 L 122.17 133.55 L 119.43 134.26 L 116.68 133.55 L 104.81 129.5 Z"

    val gba: CartSpec by lazy {
        CartSpec(
            w = 240f, h = 141f,
            outline = svg(GBA_OUTLINE),
            // label_panel(): x .135..875, y .231..882 of the box
            label = Rect(240f * .135f, 141f * .231f, 240f * .875f, 141f * .882f),
            labelRadius = 2.2f,
            rim = 4f * 240f / 240f,
            relief = listOf(
                Relief(svg(GBA_RIDGE), raised = true),
                Relief(svg(GBA_CUT, evenOdd = true), raised = false),
            ),
            pinsTop = .87f,
            letteringY = 14f,
        )
    }

    // --- Game Boy: crates/slot-ui/assets/gb_cart.svg / gbc_cart.svg -------------------------

    private const val GB_NOTCHED =
        "M 9.8 0 L 208.9 0 A 4.2 4 0 0 1 213.1 4 L 213.1 10.7 A 0.8 0.8 0 0 0 213.9 11.5 L 228.7 11.5 A 4.8 5 0 0 1 233.5 16.5 L 233.5 255.6 A 4.3 3.4 0 0 1 229.2 259 L 10.8 259 A 4.3 3.4 0 0 1 6.5 255.6 L 6.5 2.4 A 3.3 2.4 0 0 1 9.8 0 Z"
    private const val GB_ROUNDED =
        "M 24.5 0 L 215.5 0 A 18 18 0 0 1 233.5 18 L 233.5 255.6 A 4.3 3.4 0 0 1 229.2 259 L 10.8 259 A 4.3 3.4 0 0 1 6.5 255.6 L 6.5 18 A 18 18 0 0 1 24.5 0 Z"

    private val GB_CUTS = listOf(
        "M 12.82 112.42 A 1.12 1.12 0 0 1 15.07 112.42 L 15.07 256.68 A 1.12 1.12 0 0 1 12.82 256.68 Z" to false,
        "M 224.93 112.42 A 1.12 1.12 0 0 1 227.18 112.42 L 227.18 256.68 A 1.12 1.12 0 0 1 224.93 256.68 Z" to false,
        "M 101.54 230.29 L 138.46 230.29 L 120 251.76 Z M 104.59 232.49 L 135.41 232.49 L 120 249.04 Z" to true,
    )

    private fun gbRidgesNotched() = listOf(
        "M 6.5 20.7 L 35.86 20.7 L 33.52 26.65 L 6.5 26.65 Z M 8.75 22.89 L 33.61 22.89 L 31.27 24.46 L 8.75 24.46 Z",
        "M 233.5 20.7 L 204.14 20.7 L 206.48 26.65 L 233.5 26.65 Z M 231.25 22.89 L 206.39 22.89 L 208.73 24.46 L 231.25 24.46 Z",
        "M 6.5 28.48 L 30.94 28.48 L 29.79 34.43 L 6.5 34.43 Z M 8.75 30.67 L 28.69 30.67 L 27.54 32.24 L 8.75 32.24 Z",
        "M 233.5 28.48 L 209.06 28.48 L 210.21 34.43 L 233.5 34.43 Z M 231.25 30.67 L 211.31 30.67 L 212.46 32.24 L 231.25 32.24 Z",
        "M 6.5 36.25 L 29.19 36.25 L 29.19 42.2 L 6.5 42.2 Z M 8.75 38.44 L 26.94 38.44 L 26.94 40.01 L 8.75 40.01 Z",
        "M 233.5 36.25 L 210.81 36.25 L 210.81 42.2 L 233.5 42.2 Z M 231.25 38.44 L 213.06 38.44 L 213.06 40.01 L 231.25 40.01 Z",
        "M 6.5 44.03 L 29.79 44.03 L 30.94 49.98 L 6.5 49.98 Z M 8.75 46.22 L 27.54 46.22 L 28.69 47.79 L 8.75 47.79 Z",
        "M 233.5 44.03 L 210.21 44.03 L 209.06 49.98 L 233.5 49.98 Z M 231.25 46.22 L 212.46 46.22 L 211.31 47.79 L 231.25 47.79 Z",
        "M 6.5 51.8 L 33.52 51.8 L 35.86 57.75 L 6.5 57.75 Z M 8.75 53.99 L 31.27 53.99 L 33.61 55.56 L 8.75 55.56 Z",
        "M 233.5 51.8 L 206.48 51.8 L 204.14 57.75 L 233.5 57.75 Z M 231.25 53.99 L 208.73 53.99 L 206.39 55.56 L 231.25 55.56 Z",
    ).map { it to true } + ("M 57.2 16 L 182.8 16 A 24 24 0 0 1 182.8 64 L 57.2 64 A 24 24 0 0 1 57.2 16 Z M 57.2 18.2 L 182.8 18.2 A 21.8 21.8 0 0 1 182.8 61.8 L 57.2 61.8 A 21.8 21.8 0 0 1 57.2 18.2 Z" to true)

    private fun gbRidgesRounded(): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        val ys = listOf(20.7f to 26.65f, 28.48f to 34.43f, 36.25f to 42.2f, 44.03f to 49.98f, 51.8f to 57.75f)
        for ((a, b) in ys) {
            out += "M 6.5 $a L 13.5 $a L 13.5 $b L 6.5 $b Z M 8.75 ${a + 2.19f} L 11.25 ${a + 2.19f} L 11.25 ${b - 2.19f} L 8.75 ${b - 2.19f} Z" to true
            out += "M 233.5 $a L 226.5 $a L 226.5 $b L 233.5 $b Z M 231.25 ${a + 2.19f} L 228.75 ${a + 2.19f} L 228.75 ${b - 2.19f} L 231.25 ${b - 2.19f} Z" to true
        }
        out += "M 57.2 16 L 182.8 16 A 24 24 0 0 1 182.8 64 L 57.2 64 A 24 24 0 0 1 57.2 16 Z M 57.2 18.2 L 182.8 18.2 A 21.8 21.8 0 0 1 182.8 61.8 L 57.2 61.8 A 21.8 21.8 0 0 1 57.2 18.2 Z" to true
        return out
    }

    val gbNotched: CartSpec by lazy {
        CartSpec(
            w = 240f, h = 259f, outline = svg(GB_NOTCHED),
            // gb_label_panel(): x .147..854, y .291..868
            label = Rect(240f * .147f, 259f * .291f, 240f * .854f, 259f * .868f),
            labelRadius = 2.4f, rim = 7f,
            relief = listOf(
                Relief(combine(*gbRidgesNotched().toTypedArray()), raised = true),
                Relief(combine(*GB_CUTS.toTypedArray()), raised = false),
            ),
            pinsTop = .9f, letteringY = 70f,
        )
    }

    val gbRounded: CartSpec by lazy {
        CartSpec(
            w = 240f, h = 259f, outline = svg(GB_ROUNDED),
            label = Rect(240f * .147f, 259f * .291f, 240f * .854f, 259f * .868f),
            labelRadius = 2.4f, rim = 7f,
            relief = listOf(
                Relief(combine(*gbRidgesRounded().toTypedArray()), raised = true),
                Relief(combine(*GB_CUTS.toTypedArray()), raised = false),
            ),
            strokeRelief = listOf(
                svg("M 13.5 18 A 11 11 0 0 1 24.5 7 L 215.5 7 A 11 11 0 0 1 226.5 18") to 5f,
            ),
            pinsTop = .9f, letteringY = 70f,
        )
    }

    // --- Procedural carts -----------------------------------------------------------------------

    /** The NES cart from the front: the tall ridged grip down the left, a tall label on the right with an arrow under it. */
    private fun nes(): CartSpec {
        val outline = roundedPoly(pts(0f, 8f, 16f, 8f, 16f, 0f, 224f, 0f, 224f, 8f, 240f, 8f, 240f, 292f, 0f, 292f), 4f)
        return CartSpec(
            w = 240f, h = 292f, outline = outline,
            label = Rect(108f, 10f, 214f, 184f), labelRadius = 3f, rim = 6f,
            relief = listOf(
                // the ridged grip
                Relief(gripSlots(24f, 14f, 52f, 12.6f, 3.2f, 21), raised = false),
                Relief(rectPath(20f, 4f, 1.6f, 288f), raised = false),
                Relief(rectPath(80f, 4f, 1.6f, 288f), raised = false),
                // the arrow pointing into the console
                Relief(Path().apply { moveTo(150f, 204f); lineTo(172f, 204f); lineTo(161f, 220f); close() }, raised = false),
            ),
            pinsTop = .92f, letteringY = 40f,
        )
    }

    /**
     * The Super Famicom / PAL SNES cart: a wide label across the top, grip ridges on the top corners, seams down to the
     * recessed lower panel with its handle slot, and a screw in each bottom corner.
     */
    private fun snes(): CartSpec {
        val outline = roundedPoly(pts(0f, 6f, 6f, 0f, 234f, 0f, 240f, 6f, 240f, 152f, 234f, 158f, 6f, 158f, 0f, 152f), 5f)
        return CartSpec(
            w = 240f, h = 158f, outline = outline,
            label = Rect(30f, 10f, 210f, 68f), labelRadius = 3f, rim = 6f,
            relief = listOf(
                Relief(gripSlots(2f, 14f, 9f, 6f, 2f, 5, mirrorTo = 229f), raised = false),
                // seams either side of the lower panel, and its top edge
                Relief(rectPath(46f, 74f, 2f, 84f), raised = false),
                Relief(rectPath(192f, 74f, 2f, 84f), raised = false),
                Relief(rectPath(46f, 74f, 148f, 1.6f), raised = false),
                // the handle slot
                Relief(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(70f, 100f, 170f, 108f), androidx.compose.ui.geometry.CornerRadius(4f))) }, raised = false),
                // screws
                Relief(Path().apply { addOval(Rect(9f, 141f, 16f, 148f)); addOval(Rect(224f, 141f, 231f, 148f)) }, raised = false),
            ),
            pinsTop = .86f, letteringY = 18f,
        )
    }

    /** A Nintendo DS game card: dark, "NINTENDO DS" along the top, the label below, a notch low on the right. */
    private fun ndsCard(): CartSpec {
        val outline = roundedPoly(pts(0f, 0f, 200f, 0f, 200f, 176f, 194f, 182f, 194f, 214f, 0f, 214f), 9f)
        return CartSpec(
            w = 200f, h = 214f, outline = outline,
            label = Rect(12f, 34f, 188f, 180f), labelRadius = 5f, rim = 5f,
            relief = listOf(Relief(rectPath(40f, 196f, 120f, 2f), raised = false)),
            pinsTop = .9f, letteringY = 10f,
        )
    }

    /**
     * A Nintendo Switch game card: small and dark, a little tab on the top edge, the label nearly filling the front
     * (its red "NINTENDO SWITCH" band is drawn by the cart painter) and the arrow under it.
     */
    private fun switchCard(): CartSpec {
        val outline = roundedPoly(pts(0f, 6f, 70f, 6f, 76f, 0f, 104f, 0f, 110f, 6f, 180f, 6f, 180f, 262f, 0f, 262f), 10f)
        return CartSpec(
            w = 180f, h = 262f, outline = outline,
            label = Rect(12f, 20f, 168f, 232f), labelRadius = 7f, rim = 5f,
            relief = listOf(Relief(Path().apply { moveTo(80f, 240f); lineTo(100f, 240f); lineTo(90f, 252f); close() }, raised = false)),
            pinsTop = .92f, letteringY = 30f,
        )
    }

    /** A Nintendo 3DS game card: light grey, the tab on the top right (so it can't go into a DS), "NINTENDO 3DS" on top. */
    private fun n3dsCard(): CartSpec {
        val outline = roundedPoly(pts(0f, 0f, 184f, 0f, 184f, 8f, 200f, 14f, 200f, 44f, 184f, 50f, 184f, 210f, 0f, 210f), 7f)
        return CartSpec(
            w = 200f, h = 210f, outline = outline,
            label = Rect(12f, 34f, 172f, 196f), labelRadius = 5f, rim = 5f,
            relief = emptyList(),
            pinsTop = .9f, letteringY = 10f,
        )
    }

    private fun genesis(): CartSpec {
        val outline = roundedPoly(pts(0f, 10f, 10f, 0f, 230f, 0f, 240f, 10f, 240f, 150f, 224f, 168f, 224f, 196f, 16f, 196f, 16f, 168f, 0f, 150f), 6f)
        return CartSpec(
            w = 240f, h = 196f, outline = outline,
            label = Rect(30f, 56f, 210f, 138f), labelRadius = 3f, rim = 6f,
            relief = listOf(
                Relief(gripSlots(16f, 8f, 208f, 6f, 2.6f, 5), raised = true),
                Relief(rectPath(60f, 150f, 120f, 5f), raised = false),
            ),
            pinsTop = .88f, letteringY = 40f,
        )
    }

    private fun card(game: Boolean): CartSpec {
        val h = if (game) 190f else 170f
        val outline = if (game) {
            roundedPoly(pts(0f, 0f, 240f, 0f, 240f, h - 30f, 222f, h, 18f, h, 0f, h - 30f), 8f)
        } else {
            roundedPoly(pts(0f, 0f, 196f, 0f, 240f, 40f, 240f, h, 0f, h), 7f)
        }
        return CartSpec(
            w = 240f, h = h, outline = outline,
            label = Rect(16f, 40f, 224f, h - 24f), labelRadius = 3f, rim = 5f,
            relief = listOf(Relief(gripSlots(16f, 12f, 208f, 5f, 2.2f, 4), raised = false)),
            pinsTop = .9f, letteringY = 14f,
        )
    }

    private fun hucard(): CartSpec {
        val outline = roundedPoly(pts(0f, 0f, 150f, 0f, 150f, 232f, 0f, 232f), 6f)
        return CartSpec(
            w = 150f, h = 232f, outline = outline,
            label = Rect(11f, 14f, 139f, 186f), labelRadius = 3f, rim = 4f,
            relief = listOf(Relief(rectPath(18f, 196f, 114f, 3f), raised = false)),
            pinsTop = .9f, letteringY = 204f,
        )
    }
}

private fun CartSpecs.disc(size: Float, labelR: Float, hub: Float, hole: Float): CartSpec {
    val c = size / 2f
    val outline = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(0f, 0f, size, size))
        addOval(Rect(c - hole, c - hole, c + hole, c + hole))
    }
    return CartSpec(
        w = size, h = size, outline = outline,
        label = Rect(c - labelR, c - labelR, c + labelR, c + labelR), labelRadius = labelR, rim = 4f,
        relief = emptyList(), pinsTop = 1f, letteringY = 10f,
        disc = DiscSpec(c, c, c, labelR, hub, hole),
    )
}

private fun CartSpecs.n64(): CartSpec {
    // the real cart from the front: wide and short, a gently arched top, two side panels split off by seams,
    // and a tall label in the middle
    // the top is one arch: big rounded shoulders on the side panels, rising to a gently curved middle
    val outline = Path().apply {
        moveTo(12f, 162f)
        quadraticTo(6f, 162f, 6f, 156f)
        // near the bottom the side panels step in, where the cart goes into the console
        lineTo(6f, 134f)
        lineTo(0f, 129f)
        lineTo(0f, 46f)
        cubicTo(0f, 20f, 14f, 8f, 40f, 6f)
        lineTo(58f, 4f)
        quadraticTo(120f, -4f, 182f, 4f)
        lineTo(200f, 6f)
        cubicTo(226f, 8f, 240f, 20f, 240f, 46f)
        lineTo(240f, 129f)
        lineTo(234f, 134f)
        lineTo(234f, 156f)
        quadraticTo(234f, 162f, 228f, 162f)
        close()
    }
    return CartSpec(
        w = 240f, h = 162f, outline = outline,
        label = Rect(70f, 18f, 170f, 146f), labelRadius = 4f, rim = 6f,
        relief = listOf(
            // seams between the side panels and the middle
            Relief(rectPath(57f, 5f, 2.2f, 157f), raised = false),
            Relief(rectPath(181f, 5f, 2.2f, 157f), raised = false),
            // the step near the bottom of each side panel
            Relief(rectPath(6f, 133f, 48f, 2f), raised = false),
            Relief(rectPath(186f, 133f, 48f, 2f), raised = false),
        ),
        pinsTop = .9f, letteringY = 22f,
    )
}

/** A caddy outline with big round top corners (it hugs the disc) and nearly square bottom ones, like a real UMD. */
private fun umdShell(w: Float, h: Float, top: Float, bottom: Float): Path = Path().apply {
    moveTo(top, 0f)
    lineTo(w - top, 0f)
    arcTo(Rect(w - 2 * top, 0f, w, 2 * top), -90f, 90f, false)
    lineTo(w, h - bottom)
    arcTo(Rect(w - 2 * bottom, h - 2 * bottom, w, h), 0f, 90f, false)
    lineTo(bottom, h)
    arcTo(Rect(0f, h - 2 * bottom, 2 * bottom, h), 90f, 90f, false)
    lineTo(0f, top)
    arcTo(Rect(0f, 0f, 2 * top, 2 * top), 180f, 90f, false)
    close()
}

/**
 * PSP UMD: a 60 mm disc sealed in a caddy that is round on top and square at the bottom. It is drawn as a disc
 * (printed label, clear hub) inside a smoky shell, not as a cartridge.
 */
private fun CartSpecs.umd(): CartSpec {
    // the real UMD: a white caddy, round on top and nearly square at the bottom, with the sealed disc (no hole)
    // seen through a window a little above the middle, "PSP" and a controller mark along the bottom
    val s = 232f
    val cx = s / 2f
    val cy = 106f
    val outline = umdShell(s, s, 108f, 18f)
    return CartSpec(
        w = s, h = s, outline = outline,
        label = Rect(cx - 94f, cy - 94f, cx + 94f, cy + 94f), labelRadius = 94f, rim = 5f,
        relief = emptyList(), pinsTop = 1f, letteringY = 10f,
        disc = DiscSpec(cx, cy, 100f, 94f, 36f, 0f, rimText = true),
    )
}

/** An app as a square, Switch-style game card: a notched corner, the app icon on the face and contacts below. */
private fun CartSpecs.appCard(): CartSpec {
    val outline = roundedPoly(pts(0f, 0f, 176f, 0f, 208f, 32f, 208f, 232f, 0f, 232f), 13f)
    val contacts = Path().apply { for (i in 0 until 9) addRect(Rect(40f + i * 14f, 214f, 40f + i * 14f + 8f, 226f)) }
    return CartSpec(
        w = 208f, h = 232f, outline = outline,
        label = Rect(24f, 46f, 184f, 206f), labelRadius = 20f, rim = 6f,
        relief = listOf(
            Relief(contacts, raised = false),
            Relief(rectPath(18f, 14f, 60f, 4f), raised = true),
            Relief(rectPath(18f, 22f, 44f, 4f), raised = true),
        ),
        pinsTop = .94f, letteringY = 14f,
    )
}
