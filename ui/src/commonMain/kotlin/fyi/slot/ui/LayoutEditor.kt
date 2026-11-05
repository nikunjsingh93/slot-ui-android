package fyi.slot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.hypot

/**
 * Edit button layout: the on-screen buttons of one system, shown over the game. Drag the d-pad, the face buttons, the
 * shoulder buttons, Start/Select or the game screen (the outline) to move them, pinch to resize. Portrait and landscape
 * are kept apart. Done keeps the layout, Reset puts the defaults back.
 */
@Composable
fun LayoutEditor(state: SlotState, m: Metrics) {
    val sys = state.editSystem ?: return
    val portrait = m.portrait
    var tweak by remember(sys, portrait) { mutableStateOf(state.settings.touchTweak(sys, portrait)) }
    val layout = layoutFor(sys, m, tweak)
    val ds = sys == GameSystem.NDS
    val screen = gameScreenArea(sys, ds, m.w, m.h, touch = true, step = state.settings.portraitScreenStep, tweak = tweak)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f))) {
        // the game screen (the DS's two screens) as an outline: drag it to move it, pinch to resize it
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val parts = if (ds) listOf(
                androidx.compose.ui.geometry.Rect(screen.left, screen.top, screen.right, screen.center.y),
                androidx.compose.ui.geometry.Rect(screen.left, screen.center.y, screen.right, screen.bottom),
            ) else listOf(screen)
            for (r in parts) {
                drawRect(Color.White.copy(alpha = .08f), r.topLeft, r.size)
                drawRect(Color.White.copy(alpha = .55f), r.topLeft, r.size, style = androidx.compose.ui.graphics.drawscope.Stroke(2f * m.u))
            }
        }
        Box(
            Modifier.fillMaxSize().pointerInput(sys, m) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val l0 = layoutFor(sys, m, tweak)
                    val p = down.position
                    val slack = 14f * m.u
                    val shoulder = l0.pills.firstOrNull { it.first in SHOULDER_PADS && it.second.contains(p, slack) }
                    val startSel = l0.pills.any { (it.first == Pad.Start || it.first == Pad.Select) && it.second.contains(p, slack) }
                    val bx = if (l0.buttons.isEmpty()) 0f else l0.buttons.map { it.cx }.average().toFloat()
                    val by = if (l0.buttons.isEmpty()) 0f else l0.buttons.map { it.cy }.average().toFloat()
                    val reach = (l0.buttons.maxOfOrNull { hypot(it.cx - bx, it.cy - by) + it.r } ?: 0f) * 1.25f
                    val distD = hypot(p.x - l0.dpadCx, p.y - l0.dpadCy)
                    val distB = hypot(p.x - bx, p.y - by)
                    // which part the finger picked up: a shoulder button, Start/Select, the d-pad, the face buttons, or the screen
                    val part = when {
                        shoulder != null -> TWEAK_SHOULDERS
                        startSel -> TWEAK_START
                        distD <= l0.dpadR * 1.6f && distD <= distB -> TWEAK_DPAD
                        distB <= reach -> TWEAK_BUTTONS
                        distD <= l0.dpadR * 1.6f -> TWEAK_DPAD
                        else -> TWEAK_SCREEN
                    }
                    // the right shoulder button moves as the mirror of the left one
                    val mirror = shoulder != null && (shoulder.second.l + shoulder.second.r) / 2f > m.w / 2f
                    var lastC = down.position
                    var lastSpan = 0f
                    var lastN = 1
                    while (true) {
                        val ev = awaitPointerEvent()
                        val pts = ev.changes.filter { it.pressed }
                        if (pts.isEmpty()) break
                        val c = Offset(pts.map { it.position.x }.average().toFloat(), pts.map { it.position.y }.average().toFloat())
                        val span = if (pts.size >= 2) pts.map { hypot(it.position.x - c.x, it.position.y - c.y) }.average().toFloat() else 0f
                        if (pts.size == lastN) {
                            val pan = c - lastC
                            val zoom = if (pts.size >= 2 && lastSpan > 8f) span / lastSpan else 1f
                            val t = fullTweak(tweak).toMutableList()
                            val o = part
                            val screenPart = o == TWEAK_SCREEN
                            t[o] = (t[o] * zoom).coerceIn(if (screenPart) .4f else .5f, if (screenPart) 2.2f else 1.9f)
                            t[o + 1] = (t[o + 1] + (if (mirror) -pan.x else pan.x) / m.w).coerceIn(-.6f, .6f)
                            t[o + 2] = (t[o + 2] + pan.y / m.h).coerceIn(-.6f, .6f)
                            tweak = t
                        }
                        lastC = c; lastSpan = span; lastN = pts.size
                        ev.changes.forEach { it.consume() }
                    }
                    state.saveTouchLayout(sys, portrait, tweak)
                }
            },
        ) { TouchArt(layout, emptySet(), m) }
        Column(Modifier.align(Alignment.TopCenter).padding(top = (m.h * 0.14f).pxToDp(), start = 24f.udp(), end = 24f.udp()), horizontalAlignment = Alignment.CenterHorizontally) {
            SlotText("Edit button layout", 20f, bold = true)
            SlotText(sys.title, 13f, color = SlotColors.Ink.copy(alpha = .6f))
            Spacer(Modifier.height(6f.udp()))
            SlotText(if (portrait) "Portrait layout" else "Landscape layout", 13f, color = SlotColors.Ink.copy(alpha = .6f))
            SlotText("Drag the buttons, L / R, Start / Select or the game screen to move them", 14f, color = SlotColors.Ink.copy(alpha = .85f), align = TextAlign.Center, maxLines = 3)
            SlotText("Pinch to resize", 14f, color = SlotColors.Ink.copy(alpha = .85f), align = TextAlign.Center)
            Spacer(Modifier.height(14f.udp()))
            Row(horizontalArrangement = Arrangement.spacedBy(12f.udp())) {
                EditChip("Reset") { tweak = TOUCH_DEFAULT; state.saveTouchLayout(sys, portrait, null) }
                EditChip("Done", primary = true) { state.closeLayoutEditor() }
            }
        }
    }
}

@Composable
private fun EditChip(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(38f.udp()).width(104f.udp()).clip(RoundedCornerShape(19f.udp()))
            .background(if (primary) SlotColors.Ink else SlotColors.Housing).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { SlotText(label, 15f, bold = true, color = if (primary) SlotColors.CapInk else SlotColors.Ink) }
}
