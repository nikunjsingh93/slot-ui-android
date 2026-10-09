package fyi.slot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import fyi.slot.ui.res.Res
import fyi.slot.ui.res.lettering_gb
import fyi.slot.ui.res.lettering_gba
import fyi.slot.ui.res.lettering_gbc
import org.jetbrains.compose.resources.imageResource
import kotlin.math.max

/** Shared, composition-scoped resources every cart painter needs. */
@Stable
class CartAssets(
    val measurer: TextMeasurer,
    val font: FontFamily,
    val letteringGba: ImageBitmap,
    val letteringGb: ImageBitmap,
    val letteringGbc: ImageBitmap,
) {
    private val cache = HashMap<String, CartPainter>()

    fun painterFor(game: Game, cover: ImageBitmap?): CartPainter {
        val key = game.id + (if (cover != null) "#c" else "") + game.coreId
        return cache.getOrPut(key) { CartPainter.create(this, game, cover) }
    }
}

@Composable
fun rememberCartAssets(): CartAssets {
    val m = rememberTextMeasurer(cacheSize = 64)
    val font = rememberSlotFont()
    val a = imageResource(Res.drawable.lettering_gba)
    val b = imageResource(Res.drawable.lettering_gb)
    val c = imageResource(Res.drawable.lettering_gbc)
    return remember(m, font, a, b, c) { CartAssets(m, font, a, b, c) }
}

private val Unit1 = Density(1f, 1f)

/** Fit [text] into a [w] x [h] box (1 unit = 1px), shrinking from [maxPx] to [minPx]. */
fun fitText(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    w: Float,
    h: Float,
    maxPx: Float,
    minPx: Float,
    maxLines: Int,
): TextLayoutResult {
    var px = maxPx
    var last: TextLayoutResult? = null
    while (px >= minPx) {
        val r = measurer.measure(
            text, style.copy(fontSize = px.sp, lineHeight = (px * 1.18f).sp),
            overflow = TextOverflow.Ellipsis, softWrap = true, maxLines = maxLines,
            constraints = Constraints(maxWidth = w.toInt().coerceAtLeast(1)), density = Unit1,
        )
        last = r
        if (r.size.height <= h && !r.hasVisualOverflow && !r.didOverflowHeight) return r
        px -= 1f
    }
    return last!!
}

@Stable
class CartPainter private constructor(
    val game: Game,
    val spec: CartSpec,
    val shell: ShellStyle,
    val labelColor: Color,
    val title: TextLayoutResult?,
    val badge: TextLayoutResult?,
    val lettering: ImageBitmap?,
    val letterText: TextLayoutResult?,
    val cover: ImageBitmap?,
) {
    val w get() = spec.w
    val h get() = spec.h
    private val lightShell = shell.color.luminance() > 0.5f
    private val glitter: List<Offset> by lazy {
        var s = 0x2545F491L
        fun next(): Float { s = s * 1103515245L + 12345L; return ((s shr 8) and 0xFFFF).toFloat() / 65535f }
        List(150) { Offset(next() * spec.w, next() * spec.h) }
    }

    /** Paints the cart face with its top-left at the current origin, in design units. */
    fun DrawScope.paint(alpha: Float = 1f) {
        paintShadow(alpha)
        val o = spec.outline
        drawPath(o, shell.color, alpha = alpha)
        clipPath(o) {
            paintShell(alpha)
            paintRelief(alpha)
            paintLabel(alpha)
            paintLettering(alpha)
        }
        // crisp edge
        drawPath(o, Color.Black.copy(alpha = .38f * alpha), style = Stroke(width = 1.1f))
    }

    private fun DrawScope.paintShadow(alpha: Float) {
        val o = spec.outline
        for ((w, a, dy) in listOf(Triple(18f, .035f, 9f), Triple(12f, .05f, 7f), Triple(7f, .07f, 5f), Triple(3f, .10f, 3f))) {
            translate(0f, dy) { drawPath(o, Color.Black.copy(alpha = a * alpha), style = Stroke(width = w)) }
        }
        translate(0f, 4f) { drawPath(o, Color.Black.copy(alpha = .30f * alpha)) }
    }

    private fun DrawScope.paintShell(alpha: Float) {
        val o = spec.outline
        when (shell.finish) {
            Finish.Solid -> {}
            Finish.Translucent, Finish.Glitter -> {
                // body is darker (we see the PCB through it) with a bright rim where the plastic is thick
                drawPath(o, Color.Black.copy(alpha = .22f * alpha))
                drawPath(o, lerpColor(shell.color, Color.White, .38f).copy(alpha = alpha), style = Stroke(width = spec.rim * 2f))
                drawPath(o, lerpColor(shell.color, Color.White, .70f).copy(alpha = .55f * alpha), style = Stroke(width = 2.2f))
            }
        }
        if (shell.finish == Finish.Glitter) {
            for ((i, p) in glitter.withIndex()) {
                val a = if (i % 3 == 0) .85f else .45f
                drawRect(Color.White.copy(alpha = a * alpha), topLeft = p, size = Size(1.1f, 1.1f))
            }
        }
        // Top-down lighting
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = .26f),
                0.18f to Color.White.copy(alpha = .08f),
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = .34f),
                startY = 0f, endY = spec.h,
            ),
            alpha = alpha, size = Size(spec.w, spec.h),
        )
        // Left/right falloff, gives the curved-plastic feel
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = .20f), .08f to Color.Transparent,
                .92f to Color.Transparent, 1f to Color.Black.copy(alpha = .22f),
                startX = 0f, endX = spec.w,
            ),
            alpha = alpha, size = Size(spec.w, spec.h),
        )
        // Pins end of cart is slightly darker where it tucks into the slot
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 1f to Color.Black.copy(alpha = .18f),
                startY = spec.h * .82f, endY = spec.h,
            ),
            topLeft = Offset(0f, spec.h * .82f), size = Size(spec.w, spec.h * .18f), alpha = alpha,
        )
    }

    private fun DrawScope.paintRelief(alpha: Float) {
        for (r in spec.relief) {
            val d = if (r.raised) -1.1f else 1.1f
            translate(d, d) { drawPath(r.path, Color.White.copy(alpha = .30f * alpha)) }
            drawPath(r.path, Color.Black.copy(alpha = .40f * alpha))
        }
        for ((p, wdt) in spec.strokeRelief) {
            drawPath(p, Color.White.copy(alpha = .22f * alpha), style = Stroke(width = wdt, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            translate(0f, 4.6f) { drawPath(p, Color.Black.copy(alpha = .35f * alpha), style = Stroke(width = 2.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)) }
        }
    }

    private fun DrawScope.paintLabel(alpha: Float) {
        val l = spec.label
        val r = spec.labelRadius
        // recess around the label
        drawRoundRect(
            Color.Black.copy(alpha = .42f * alpha),
            topLeft = Offset(l.left - 1.4f, l.top - 1.4f), size = Size(l.width + 2.8f, l.height + 2.8f),
            cornerRadius = CornerRadius(r + 1.2f),
        )
        drawRoundRect(
            Color.White.copy(alpha = .16f * alpha),
            topLeft = Offset(l.left - 1.4f, l.top - 0.2f), size = Size(l.width + 2.8f, l.height + 2.2f),
            cornerRadius = CornerRadius(r + 1.2f), style = Stroke(width = .8f),
        )
        val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(l, CornerRadius(r))) }
        clipPath(clip) {
            if (cover != null) {
                val sc = max(l.width / cover.width, l.height / cover.height)
                val sw = (l.width / sc).toInt().coerceAtMost(cover.width)
                val sh = (l.height / sc).toInt().coerceAtMost(cover.height)
                drawImage(
                    cover,
                    srcOffset = IntOffset((cover.width - sw) / 2, (cover.height - sh) / 2), srcSize = IntSize(sw, sh),
                    dstOffset = IntOffset(l.left.toInt(), l.top.toInt()), dstSize = IntSize(l.width.toInt() + 1, l.height.toInt() + 1),
                    alpha = alpha,
                )
            } else {
                drawGeneratedLabel(l, alpha)
            }
            // glossy edge + inset shadow on top of everything in the label
            drawRect(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = .34f), 1f to Color.Transparent, startY = l.top, endY = l.top + 6f),
                topLeft = l.topLeft, size = Size(l.width, 6f), alpha = alpha,
            )
            drawRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = .20f), .42f to Color.Transparent, 1f to Color.Transparent,
                    start = l.topLeft, end = Offset(l.right, l.bottom),
                ),
                topLeft = l.topLeft, size = Size(l.width, l.height), alpha = alpha,
            )
        }
    }

    private fun DrawScope.drawGeneratedLabel(l: Rect, alpha: Float) {
        drawRect(labelColor, topLeft = l.topLeft, size = l.size, alpha = alpha)
        // darker band at the bottom + faint diagonal pattern, like a printed sticker
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = .30f), startY = l.top, endY = l.bottom),
            topLeft = l.topLeft, size = l.size, alpha = alpha,
        )
        val bar = (l.height * .09f).coerceIn(3f, 9f)
        drawRect(Color.White.copy(alpha = .20f * alpha), topLeft = Offset(l.left, l.top), size = Size(l.width, bar))
        title?.let { t ->
            val x = l.left + (l.width - t.size.width) / 2f
            val y = l.top + (l.height - t.size.height) / 2f + bar / 2f
            translate(0f, 1.1f) { drawText(t, Color.Black.copy(alpha = .45f * alpha), topLeft = Offset(x, y)) }
            drawText(t, Color.White, topLeft = Offset(x, y), alpha = alpha)
        }
        badge?.let { b ->
            drawText(b, Color.White.copy(alpha = .75f), topLeft = Offset(l.right - b.size.width - 6f, l.bottom - b.size.height - 4f), alpha = alpha)
        }
    }

    private fun DrawScope.paintLettering(alpha: Float) {
        val tint = if (lightShell) Color(0xFF26262A) else Color.White
        if (lettering != null) {
            drawImage(
                lettering, dstSize = IntSize(spec.w.toInt(), spec.h.toInt()),
                alpha = .92f * alpha, colorFilter = ColorFilter.colorMatrix(lumaToAlpha(if (lightShell) 38f else 255f)),
            )
        }
        letterText?.let { t ->
            drawText(t, tint.copy(alpha = .9f), topLeft = Offset((spec.w - t.size.width) / 2f, spec.letteringY), alpha = alpha)
        }
    }

    companion object {
        fun create(assets: CartAssets, game: Game, cover: ImageBitmap?): CartPainter {
            val spec = CartSpecs.of(game)
            val shell = Shells.forGame(game)
            val l = spec.label
            val style = TextStyle(fontFamily = assets.font, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.White)
            val title = if (cover == null) fitText(
                assets.measurer, game.title, style,
                w = l.width - 14f, h = l.height - 22f, maxPx = (l.height / 3.1f).coerceAtMost(30f), minPx = 8f, maxLines = 4,
            ) else null
            val badge = if (cover == null) assets.measurer.measure(
                game.system.short, style.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold), density = Unit1,
            ) else null
            val lettering = when (game.system.shape) {
                CartShape.GBA -> assets.letteringGba
                CartShape.GB -> assets.letteringGb
                CartShape.GBC -> assets.letteringGbc
                else -> null
            }
            val letterText = if (lettering == null) assets.measurer.measure(
                game.system.lettering,
                style.copy(fontSize = (spec.w / 19f).sp, letterSpacing = (spec.w / 110f).sp),
                density = Unit1,
            ) else null
            return CartPainter(game, spec, shell, Shells.labelColor(game.title), title, badge, lettering, letterText, cover)
        }
    }
}

/** The lettering PNGs are opaque grayscale (black = nothing); turn luminance into alpha with a flat [ink] colour. */
fun lumaToAlpha(ink: Float) = androidx.compose.ui.graphics.ColorMatrix(
    floatArrayOf(
        0f, 0f, 0f, 0f, ink,
        0f, 0f, 0f, 0f, ink,
        0f, 0f, 0f, 0f, ink,
        1f, 0f, 0f, 0f, 0f,
    ),
)

fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t, green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t, alpha = a.alpha + (b.alpha - a.alpha) * t,
)
