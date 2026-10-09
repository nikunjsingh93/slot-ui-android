package fyi.slot.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Unit1 = Density(1f, 1f)
private val FlipEase = CubicBezierEasing(.3f, 0f, .2f, 1f)

/**
 * "Flip a cart to its circuit board to choose a core": each emulator core is a chip you plug
 * into the board. Left/Right (or tap) picks, A inserts.
 */
@Composable
fun CorePicker(state: SlotState, m: Metrics, assets: CartAssets, screen: Screen.CorePicker) {
    val game = screen.game
    val painter = remember(game.id, game.coreId) { assets.painterFor(game, state.covers[game.id]) }
    val spec = painter.spec
    val flip = remember { Animatable(0f) }
    LaunchedEffect(Unit) { flip.animateTo(1f, tween(560, easing = FlipEase)) }
    val chipPick = remember { Animatable(screen.chip.toFloat()) }
    LaunchedEffect(screen.chip) { chipPick.animateTo(screen.chip.toFloat(), spring(dampingRatio = .6f, stiffness = 500f)) }

    val s = (m.availH * .98f / spec.h).coerceAtMost(m.cartScale(spec) * 1.12f).coerceAtMost((m.w * .8f) / spec.w)
    val cw = spec.w * s
    val ch = spec.h * s
    val left = m.cx - cw / 2f
    val top = m.shelfTop + (m.availH - ch) / 2f + 6f * m.u

    val labels = remember(game.system, assets) {
        game.system.cores.map { c ->
            assets.measurer.measure(
                c.name.uppercase(),
                TextStyle(fontFamily = assets.font, fontWeight = FontWeight.Bold, fontSize = 5.4.sp, color = Color(0xFFD7D7DB), textAlign = TextAlign.Center),
                density = Unit1,
            )
        }
    }
    val rom = remember(assets) {
        assets.measurer.measure(
            "ROM", TextStyle(fontFamily = assets.font, fontWeight = FontWeight.Bold, fontSize = 6.sp, color = Color(0xFF8E8E94)), density = Unit1,
        )
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f * flip.value.coerceAtMost(1f)))
            .pointerInput(Unit) { detectTapGestures { /* swallow taps behind the board */ } },
    ) {
        val angle = flip.value * 180f
        val density = LocalDensity.current.density
        Box(
            Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(LocalDensity.current) { cw.toDp() }, with(LocalDensity.current) { ch.toDp() })
                .graphicsLayer {
                    rotationY = angle
                    cameraDistance = 16f * density
                    translationY = -14f * m.u * kotlin.math.sin(flip.value * Math.PI.toFloat())
                }
                .pointerInput(screen.chip, s, spec) {
                    detectTapGestures { at ->
                        // tap a chip on the back face (mirrored: the face is shown flipped after 90 degrees)
                        val dx = (size.width - at.x) / s
                        val dy = at.y / s
                        chipRects(spec, game.system.cores.size).forEachIndexed { i, r ->
                            if (r.inflate(6f).contains(Offset(dx, dy))) {
                                if (i == screen.chip) state.insert(game, resume = true) else state.chooseChip(i)
                            }
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                scale(s, s, pivot = Offset.Zero) {
                    if (angle < 90f) {
                        with(painter) { paint() }
                    } else {
                        // back face is pre-mirrored so the rotation lands it the right way round
                        translate(spec.w, 0f) {
                            scale(-1f, 1f, pivot = Offset.Zero) {
                                drawBoard(spec, painter.shell, game, assets, labels, rom, chipPick.value, screen.chip)
                            }
                        }
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(top = (m.topBar / m.u * .30f).udp()).alpha(flip.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SlotText(if (game.system.external) "Choose an emulator" else "Choose a core", 20f, bold = true)
            Spacer(Modifier.height(2f.udp()))
            SlotText(game.title, 13f, color = SlotColors.Ink.copy(alpha = .6f))
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = (m.bandH / m.u + 4f).udp()).alpha(flip.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val core = game.system.cores[screen.chip.coerceIn(0, game.system.cores.lastIndex)]
            SlotText(core.name, 17f, bold = true)
            SlotText(core.tagline, 13f, color = SlotColors.Ink.copy(alpha = .65f))
            Spacer(Modifier.height(10f.udp()))
            if (state.gamepad) {
                Row(horizontalArrangement = Arrangement.spacedBy(16f.udp())) {
                    if (game.system.cores.size > 1) Hint("◀▶", "Choose")
                    Hint("A", "Insert"); if (!game.system.external) Hint("X", "New game"); Hint("B", "Back")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10f.udp())) {
                    TouchButton("Back") { state.closeCorePicker() }
                    TouchButton("Insert") { state.insert(game, true) }
                    if (!game.system.external) TouchButton("New game") { state.insert(game, false) }
                }
            }
        }
    }
}

/** Chip footprints on the board in design units: one row, centred in the lower half. */
fun chipRects(spec: CartSpec, n: Int): List<Rect> {
    val cw = 62f
    val chh = 38f
    val gap = 14f
    val total = n * cw + (n - 1) * gap
    val x0 = (spec.w - total) / 2f
    val y = spec.h * (if (spec.disc != null) .66f else if (spec.h > 200f) .56f else .44f)
    return List(n) { Rect(x0 + it * (cw + gap), y, x0 + it * (cw + gap) + cw, y + chh) }
}

private fun DrawScope.drawBoard(
    spec: CartSpec,
    shell: ShellStyle,
    game: Game,
    assets: CartAssets,
    chipLabels: List<TextLayoutResult>,
    romLabel: TextLayoutResult,
    pick: Float,
    selected: Int,
) {
    val w = spec.w
    val h = spec.h
    val dark = lerpColor(shell.color, Color.Black, .55f)
    // back of the plastic shell
    drawPath(spec.outline, dark)
    drawPath(spec.outline, Color.Black.copy(alpha = .35f), style = Stroke(1.2f))
    clipPath(spec.outline) {
        drawRect(
            Brush.verticalGradient(0f to Color.White.copy(alpha = .20f), .25f to Color.Transparent, 1f to Color.Black.copy(alpha = .35f), startY = 0f, endY = h),
            size = Size(w, h),
        )
        if (spec.disc != null) drawDiscBack(spec) else {
        // the open cavity
        val cav = Path().apply { addPath(spec.outline) }
        translate(w * (1f - .955f) / 2f, h * (1f - .93f) / 2f) { scale(.955f, .93f, pivot = Offset.Zero) { drawPath(cav, Color.Black.copy(alpha = .55f)) } }

        // PCB
        val px = w * .075f
        val py = h * .075f
        val pw = w * .85f
        val ph = h * .87f
        val board = RoundRect(px, py, px + pw, py + ph, CornerRadius(5f))
        val boardPath = Path().apply { addRoundRect(board) }
        drawPath(boardPath, Brush.verticalGradient(0f to Color(0xFF2F9E58), 1f to Color(0xFF237A45), startY = py, endY = py + ph))
        clipPath(boardPath) {
            // traces
            var seed = 1234567L
            fun rnd(): Float { seed = (seed * 1103515245L + 12345L) and 0x7fffffffL; return (seed shr 4 and 0xffff) / 65535f }
            val traceColor = SlotColors.Trace.copy(alpha = .55f)
            repeat(26) {
                val x = px + rnd() * pw
                val y0 = py + rnd() * ph * .8f
                val len = 20f + rnd() * 60f
                val horizontal = rnd() > .5f
                val p = Path().apply {
                    moveTo(x, y0)
                    if (horizontal) { lineTo(x + len, y0); lineTo(x + len + 12f, y0 + 12f) } else { lineTo(x, y0 + len); lineTo(x + 12f, y0 + len + 12f) }
                }
                drawPath(p, traceColor, style = Stroke(1.6f))
                drawCircle(SlotColors.GoldPad.copy(alpha = .8f), 1.6f, Offset(x, y0))
            }
            // soft lighting
            drawRect(
                Brush.linearGradient(0f to Color.White.copy(alpha = .10f), .5f to Color.Transparent, 1f to Color.Black.copy(alpha = .18f), start = Offset(px, py), end = Offset(px + pw, py + ph)),
                Offset(px, py), Size(pw, ph),
            )
        }
        drawPath(boardPath, Color.Black.copy(alpha = .4f), style = Stroke(1.4f))

        // gold fingers along the bottom edge
        val fx0 = px + pw * .12f
        val fx1 = px + pw * .88f
        var fx = fx0
        while (fx + 4.1f < fx1) {
            drawRoundRect(SlotColors.Gold, Offset(fx, py + ph - 22f), Size(4.1f, 20f), CornerRadius(.8f))
            fx += 6.125f
        }

        // mask ROM
        val rom = Rect(px + 12f, py + 8f, px + 12f + 58f, py + 8f + 32f)
        drawChip(rom, Color(0xFF17171A), pins = 5)
        drawText(romLabel, topLeft = Offset(rom.left + 6f, rom.top + 6f))
        // battery cell
        val bat = Rect(px + pw - 12f - 34f, py + 12f, px + pw - 12f, py + 12f + 34f)
        drawCircle(Color(0xFFB9B9BE), 17f, bat.center)
        drawCircle(Color(0xFF8A8A90), 14f, bat.center, style = Stroke(1.2f))
        drawCircle(Color(0xFFD2D2D6), 6f, bat.center)
        // SMD bits
        for (i in 0 until 5) drawRect(Color(0xFF1B1B1E), Offset(px + 16f + i * 9f, py + 62f), Size(5f, 2.6f))

        }
        // core chips
        val rects = chipRects(spec, game.system.cores.size)
        for ((i, r) in rects.withIndex()) {
            // socket
            drawRoundRect(Color.Black.copy(alpha = .45f), r.topLeft - Offset(3f, 3f), Size(r.width + 6f, r.height + 6f), CornerRadius(3f))
            drawRoundRect(Color(0xFF111114), r.topLeft - Offset(3f, 3f), Size(r.width + 6f, r.height + 6f), CornerRadius(3f), style = Stroke(1f))
        }
        for ((i, r) in rects.withIndex()) {
            val sel = i == selected
            val near = (1f - kotlin.math.abs(pick - i).coerceAtMost(1f))
            val lift = -5f * near
            translate(0f, lift) {
                if (sel) {
                    drawRoundRect(SlotColors.Accent.copy(alpha = .22f), r.topLeft - Offset(7f, 7f), Size(r.width + 14f, r.height + 14f), CornerRadius(6f))
                    drawRoundRect(SlotColors.Accent, r.topLeft - Offset(5f, 5f), Size(r.width + 10f, r.height + 10f), CornerRadius(5f), style = Stroke(1.6f))
                } else {
                    drawRoundRect(Color.Black.copy(alpha = .30f), r.topLeft + Offset(1f, 3f), r.size, CornerRadius(2f))
                }
                drawChip(r, if (sel) Color(0xFF1C1C20) else Color(0xFF151518), pins = 9)
                chipLabels.getOrNull(i)?.let { t ->
                    drawText(t, topLeft = Offset(r.center.x - t.size.width / 2f, r.center.y - t.size.height / 2f), alpha = if (sel) 1f else .65f)
                }
            }
        }
    }
}

/** A black IC with silver pins, after slot's chip.svg. */
private fun DrawScope.drawChip(r: Rect, body: Color, pins: Int) {
    val pinH = 3.2f
    val pinColor = Color(0xFFB3B3B8)
    val n = pins
    val step = r.width / (n + .5f)
    for (i in 0 until n) {
        val x = r.left + step * (i + .35f)
        drawRect(pinColor, Offset(x, r.top - pinH + .6f), Size(step * .55f, pinH))
        drawRect(pinColor, Offset(x, r.bottom - .6f), Size(step * .55f, pinH))
    }
    drawRoundRect(body, r.topLeft, r.size, CornerRadius(1.8f))
    drawRoundRect(Color.White.copy(alpha = .10f), r.topLeft, Size(r.width, 1.2f), CornerRadius(1f))
    drawCircle(Color(0xFF333336), 1.8f, Offset(r.left + 5f, r.bottom - 5f))
    drawRoundRect(Color.White.copy(alpha = .06f), r.topLeft, r.size, CornerRadius(1.8f), style = Stroke(.8f))
}

/** The shiny, unprinted underside of a disc: this is what you see when you flip a disc over to pick an emulator. */
private fun DrawScope.drawDiscBack(spec: CartSpec) {
    val d = spec.disc ?: return
    val c = Offset(d.cx, d.cy)
    drawCircle(
        Brush.radialGradient(0f to Color(0xFFE8EAF2), .55f to Color(0xFFB9BDCB), 1f to Color(0xFF8E92A3), center = c, radius = d.radius),
        d.radius, c,
    )
    for (r in listOf(.30f, .45f, .60f, .75f, .90f)) drawCircle(Color.Black.copy(alpha = .07f), d.radius * r, c, style = Stroke(1.2f))
    drawCircle(
        Brush.sweepGradient(
            *arrayOf(
                0.00f to Color(0x66FF6BD6), 0.14f to Color(0x6657E0FF), 0.28f to Color(0x66B8FF6B), 0.42f to Color(0x66FFD86B),
                0.56f to Color(0x66FF6BD6), 0.70f to Color(0x6657E0FF), 0.84f to Color(0x66B8FF6B), 1.00f to Color(0x66FF6BD6),
            ),
            center = c,
        ),
        d.radius, c,
    )
    drawCircle(Color.White.copy(alpha = .35f), d.hubRadius, c)
    drawCircle(Color.Black.copy(alpha = .3f), d.hubRadius, c, style = Stroke(1.2f))
    drawCircle(Color.Black.copy(alpha = .5f), d.holeRadius + 1.5f, c, style = Stroke(2f))
}
