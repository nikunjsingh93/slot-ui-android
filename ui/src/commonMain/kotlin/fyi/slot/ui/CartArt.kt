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
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlin.math.min

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
        // the system is part of the key: an app switched between Apps and Installed keeps its id but changes look
        val key = game.id + game.system.id + (if (cover != null) "#c" else "") + game.coreId
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

    /**
     * True when [cover] is a scan of the disc itself (LaunchBox disc scans: a round disc on a transparent square)
     * rather than box art, so it is drawn whole and centred instead of cropped like a case cover.
     */
    private val clearCorners: Boolean = cover?.let { img ->
        runCatching {
            val px = IntArray(1)
            listOf(2 to 2, img.width - 3 to 2, 2 to img.height - 3, img.width - 3 to img.height - 3).all { (x, y) ->
                img.readPixels(px, x, y, 1, 1)
                (px[0] ushr 24) < 16
            }
        }.getOrDefault(false)
    } == true
    private val discScan: Boolean = spec.disc != null && clearCorners

    /**
     * A real N64 / SNES / NES label cut out of a photo of the cartridge (it comes with rounded, transparent corners):
     * the label window then takes the real label's own shape, placed where it sits on the real cart.
     */
    private val realLabel: Boolean = spec.disc == null && clearCorners && REAL_LABEL_AREA.containsKey(game.system.shape)

    /** Where the label is drawn: the cart's own window, or for a [realLabel] the real label's shape fitted into its area. */
    private val labelRect: Rect = if (!realLabel || cover == null) spec.label else {
        val area = REAL_LABEL_AREA.getValue(game.system.shape)
        val s = min(area.width / cover.width, area.height / cover.height)
        val w = cover.width * s
        val h = cover.height * s
        Rect(area.center.x - w / 2f, area.center.y - h / 2f, area.center.x + w / 2f, area.center.y + h / 2f)
    }
    private val glitter: List<Offset> by lazy {
        var s = 0x2545F491L
        fun next(): Float { s = s * 1103515245L + 12345L; return ((s shr 8) and 0xFFFF).toFloat() / 65535f }
        List(150) { Offset(next() * spec.w, next() * spec.h) }
    }

    /** Paints the cart face with its top-left at the current origin, in design units. */
    fun DrawScope.paint(alpha: Float = 1f) {
        spec.disc?.let { paintDisc(it, alpha); return }
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
        val l = labelRect
        val r = if (realLabel) 5f else spec.labelRadius
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
        // a real label carries its own branding over where the lettering would be
        letterText?.takeIf { !realLabel }?.let { t ->
            drawText(t, tint.copy(alpha = .9f), topLeft = Offset((spec.w - t.size.width) / 2f, spec.letteringY), alpha = alpha)
        }
    }


    // --- optical discs ----------------------------------------------------------------------------------

    private fun DrawScope.paintDisc(d: DiscSpec, alpha: Float) {
        val o = spec.outline
        val c = Offset(d.cx, d.cy)
        val umd = game.system.shape == CartShape.UMD
        if (umd && discScan && cover != null) {
            // a scan of the whole UMD (caddy and all): it is the cart (our own outline's shadow would show round it)
            drawImage(cover, dstOffset = IntOffset.Zero, dstSize = IntSize(spec.w.toInt(), spec.h.toInt()), alpha = alpha)
            return
        }
        paintShadow(alpha)
        drawPath(o, if (d.rimText) shell.color else Color(0xFFD7D9E0), alpha = alpha)
        clipPath(o) {
            // the reflective data surface seen through the plastic
            drawCircle(
                Brush.radialGradient(0f to Color.White.copy(alpha = .10f), 1f to Color.Black.copy(alpha = .28f), center = c, radius = d.radius),
                d.radius, c, alpha = alpha,
            )
            // printed label
            val label = Path().apply { addOval(Rect(c.x - d.labelRadius, c.y - d.labelRadius, c.x + d.labelRadius, c.y + d.labelRadius)) }
            clipPath(label) {
                val side = d.labelRadius * 2f
                val l = Rect(c.x - d.labelRadius, c.y - d.labelRadius, c.x + d.labelRadius, c.y + d.labelRadius)
                if (cover != null) {
                    // PlayStation 1 / Dreamcast covers carry a spine down the left edge: crop it away (not from a disc scan)
                    val spine = !discScan && (game.system == GameSystem.PSX || game.system == GameSystem.DC)
                    val trim = if (spine) 0.84f else 1f
                    val srcSide = min(min(cover.width * trim, cover.height * trim), min(cover.width.toFloat(), cover.height.toFloat()))
                    val sw = srcSide.toInt()
                    val sh = srcSide.toInt()
                    val down = if (discScan) 0.5f else if (game.system == GameSystem.GC) 0.4f else 0.6f
                    drawImage(
                        cover,
                        srcOffset = IntOffset(if (spine) cover.width - sw else (cover.width - sw) / 2, ((cover.height - sh) * down).toInt()), srcSize = IntSize(sw, sh),
                        dstOffset = IntOffset(l.left.toInt(), l.top.toInt()), dstSize = IntSize(side.toInt() + 1, side.toInt() + 1),
                        alpha = alpha,
                    )
                } else {
                    drawRect(lerpColor(labelColor, Color.Black, .35f), l.topLeft, l.size, alpha = alpha)
                    drawRect(
                        Brush.verticalGradient(0f to Color.White.copy(alpha = .14f), 1f to Color.Black.copy(alpha = .25f), startY = l.top, endY = l.bottom),
                        l.topLeft, l.size, alpha = alpha,
                    )
                    title?.let { t ->
                        val top = c.y + d.hubRadius + 6f + ((d.labelRadius - d.hubRadius - 18f) - t.size.height) / 2f
                        translate(0f, 1.1f) { drawText(t, Color.Black.copy(alpha = .45f * alpha), topLeft = Offset(c.x - t.size.width / 2f, top)) }
                        drawText(t, Color.White, topLeft = Offset(c.x - t.size.width / 2f, top), alpha = alpha)
                    }
                }
            }
            drawCircle(Color.Black.copy(alpha = (if (d.rimText) .55f else .18f) * alpha), d.labelRadius, c, style = Stroke(if (d.rimText) 1.4f else 0.9f))
            // brand lettering on the rim, above the label
            val ink = if (game.system == GameSystem.DC) Color(0xFFE8631A) else if (lightShell) Color(0xFF2A2A30) else Color.White
            if (umd) {
                // "PSP" bottom left and the controller mark bottom right, moulded into the white caddy
                val grey = Color(0xFF9C9DA6)
                letterText?.let { t -> drawText(t, grey, topLeft = Offset(16f, spec.h - 16f - t.size.height), alpha = alpha) }
                val gx = spec.w - 40f
                val gy = spec.h - 30f
                drawRoundRect(grey.copy(alpha = alpha), Offset(gx, gy), Size(22f, 9f), CornerRadius(4f))
                drawRoundRect(grey.copy(alpha = alpha), Offset(gx - 1f, gy + 3f), Size(8f, 11f), CornerRadius(4f))
                drawRoundRect(grey.copy(alpha = alpha), Offset(gx + 15f, gy + 3f), Size(8f, 11f), CornerRadius(4f))
            }
            letterText?.takeIf { d.rimText && !umd }?.let { t ->
                val y = c.y - (d.radius + d.labelRadius) / 2f - t.size.height / 2f
                drawText(t, ink.copy(alpha = .85f), topLeft = Offset(c.x - t.size.width / 2f, y), alpha = alpha)
            }
            if (d.rimText && game.system == GameSystem.GC) {
                // a little tilted cube, standing in for the GameCube mark
                val cy = c.y + (d.radius + d.labelRadius) / 2f
                translate(c.x, cy) {
                    rotate(45f, Offset.Zero) {
                        drawRect(ink.copy(alpha = .8f * alpha), Offset(-5.5f, -5.5f), Size(11f, 11f), style = Stroke(1.6f))
                        drawRect(ink.copy(alpha = .8f * alpha), Offset(-2.2f, -2.2f), Size(4.4f, 4.4f))
                    }
                }
            }
            // clear hub ring and the centre hole's inner shadow
            if (umd) {
                // a UMD is sealed: no hole, just the white printed centre
                drawCircle(Color(0xFFF3F3F5), d.hubRadius, c, alpha = alpha)
                drawCircle(Color.Black.copy(alpha = .18f * alpha), d.hubRadius, c, style = Stroke(1f))
            } else {
                drawCircle(Color.White.copy(alpha = .30f * alpha), d.hubRadius, c)
                drawCircle(Color.Black.copy(alpha = .35f * alpha), d.hubRadius, c, style = Stroke(1.2f))
                drawCircle(Color.White.copy(alpha = .35f * alpha), d.hubRadius - 4f, c, style = Stroke(1f))
                drawCircle(Color.Black.copy(alpha = .5f * alpha), d.holeRadius + 1.5f, c, style = Stroke(2f))
            }
            // light sweeping across the disc
            drawCircle(
                Brush.sweepGradient(
                    *arrayOf(
                        0.00f to Color.Transparent, 0.10f to Color.White.copy(alpha = .22f), 0.22f to Color.Transparent,
                        0.50f to Color.Transparent, 0.60f to Color.White.copy(alpha = .20f), 0.72f to Color.Transparent,
                        1.00f to Color.Transparent,
                    ),
                    center = c,
                ),
                d.radius, c, alpha = alpha,
            )
        }
        drawPath(o, Color.Black.copy(alpha = .4f * alpha), style = Stroke(width = 1.1f))
        drawCircle(Color.White.copy(alpha = .18f * alpha), d.radius - 1.2f, c, style = Stroke(1f))
    }

    companion object {
        /** Per cart shape, the area a real label is fitted into (design units), roughly where it sits on the real cart. */
        private val REAL_LABEL_AREA = mapOf(
            CartShape.N64 to Rect(64f, 10f, 176f, 154f),
            CartShape.SNES to Rect(20f, 18f, 220f, 114f),
            CartShape.NES to Rect(30f, 44f, 210f, 206f),
        )
        fun create(assets: CartAssets, game: Game, cover: ImageBitmap?): CartPainter {
            val spec = CartSpecs.of(game)
            val shell = Shells.forGame(game)
            val l = spec.label
            val style = TextStyle(fontFamily = assets.font, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.White)
            val disc = spec.disc
            val title = if (cover == null) {
                if (disc != null) fitText(
                    assets.measurer, game.title, style,
                    w = disc.labelRadius * 1.2f, h = disc.labelRadius - disc.hubRadius - 18f, maxPx = 26f, minPx = 7f, maxLines = 3,
                ) else fitText(
                    assets.measurer, game.title, style,
                    w = l.width - 14f, h = l.height - 22f, maxPx = (l.height / 3.1f).coerceAtMost(30f), minPx = 8f, maxLines = 4,
                )
            } else null
            val badge = if (cover == null) assets.measurer.measure(
                game.system.short, style.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold), density = Unit1,
            ) else null
            val lettering = when (game.system.shape) {
                CartShape.GBA -> assets.letteringGba
                CartShape.GB -> assets.letteringGb
                CartShape.GBC -> assets.letteringGbc
                else -> null
            }
            // (a real N64 cart has no lettering on its front)
            val letterText = if (lettering == null && game.system.shape != CartShape.N64) assets.measurer.measure(
                if (game.system.shape == CartShape.UMD) "PSP" else game.system.lettering,
                if (game.system.shape == CartShape.UMD) style.copy(fontSize = 17.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Normal)
                else if (disc != null) style.copy(fontSize = ((disc.radius - disc.labelRadius) * 0.5f).sp, letterSpacing = 0.5.sp)
                else style.copy(fontSize = (spec.w / 19f).sp, letterSpacing = (spec.w / 110f).sp),
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
