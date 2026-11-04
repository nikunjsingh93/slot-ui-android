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
 * Edit button layout: the on-screen buttons of one system, shown over the game. Drag the d-pad or the face
 * buttons to move them, pinch over a group to resize it. Done keeps the layout, Reset puts the defaults back.
 */
@Composable
fun LayoutEditor(state: SlotState, m: Metrics) {
    val sys = state.editSystem ?: return
    var tweak by remember(sys) { mutableStateOf(state.settings.touchTweaks[sys.id] ?: TOUCH_DEFAULT) }
    val layout = layoutFor(sys, m, tweak)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f))) {
        Box(
            Modifier.fillMaxSize().pointerInput(sys, m) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val l0 = layoutFor(sys, m, tweak)
                    val bx = if (l0.buttons.isEmpty()) 0f else l0.buttons.map { it.cx }.average().toFloat()
                    val by = if (l0.buttons.isEmpty()) 0f else l0.buttons.map { it.cy }.average().toFloat()
                    val dist = hypot(down.position.x - l0.dpadCx, down.position.y - l0.dpadCy)
                    val distB = hypot(down.position.x - bx, down.position.y - by)
                    val dpad = dist <= distB
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
                            val t = tweak.toMutableList()
                            val o = if (dpad) 0 else 3
                            t[o] = (t[o] * zoom).coerceIn(.5f, 1.9f)
                            t[o + 1] = (t[o + 1] + pan.x / m.w).coerceIn(-.6f, .6f)
                            t[o + 2] = (t[o + 2] + pan.y / m.h).coerceIn(-.6f, .6f)
                            tweak = t
                        }
                        lastC = c; lastSpan = span; lastN = pts.size
                        ev.changes.forEach { it.consume() }
                    }
                    state.saveTouchLayout(sys, tweak)
                }
            },
        ) { TouchArt(layout, emptySet(), m) }
        Column(Modifier.align(Alignment.TopCenter).padding(top = (m.h * 0.14f).pxToDp(), start = 24f.udp(), end = 24f.udp()), horizontalAlignment = Alignment.CenterHorizontally) {
            SlotText("Edit button layout", 20f, bold = true)
            SlotText(sys.title, 13f, color = SlotColors.Ink.copy(alpha = .6f))
            Spacer(Modifier.height(6f.udp()))
            SlotText("Drag the buttons to move them", 14f, color = SlotColors.Ink.copy(alpha = .85f), align = TextAlign.Center)
            SlotText("Pinch to resize", 14f, color = SlotColors.Ink.copy(alpha = .85f), align = TextAlign.Center)
            Spacer(Modifier.height(14f.udp()))
            Row(horizontalArrangement = Arrangement.spacedBy(12f.udp())) {
                EditChip("Reset") { tweak = TOUCH_DEFAULT; state.saveTouchLayout(sys, null) }
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
