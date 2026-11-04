package fyi.slot.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun Menus(state: SlotState, m: Metrics, gameProcess: Boolean = false) {
    val playing = state.screen == Screen.Playing
    Box(Modifier.fillMaxSize()) {
        if (gameProcess && playing && state.overlay == Overlay.None) {
            GameHud(state, m)
        }
        when (state.overlay) {
            Overlay.Quick, Overlay.Settings -> MenuScrim(state, m)
            Overlay.States -> StateSwitcher(state, m)
            Overlay.Drawer -> AppDrawer(state, m)
            else -> {}
        }
    }
}

@Composable
private fun GameHud(state: SlotState, m: Metrics) {
    val g = state.playing
    val showTouch = when (state.settings.showTouchControls) {
        TouchMode.Always -> true
        TouchMode.Never -> false
        TouchMode.Auto -> !state.gamepad
    }
    if (showTouch && g != null) TouchControls(state, g.system, m)
    if (state.fastForward) {
        Box(Modifier.fillMaxSize().padding(16f.udp()), contentAlignment = Alignment.TopEnd) {
            Box(Modifier.clip(RoundedCornerShape(12f.udp())).background(Color.Black.copy(alpha = .55f)).padding(horizontal = 12f.udp(), vertical = 5f.udp())) {
                SlotText("▶▶ ${state.settings.fastForwardSpeed}x".replace("▶▶", ">>"), 14f, bold = true)
            }
        }
    }
}

@Composable
private fun MenuScrim(state: SlotState, m: Metrics) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f))
            .pointerInput(Unit) { detectTapGestures { state.popMenu() } },
        contentAlignment = Alignment.Center,
    ) {
        val page = state.menuStack.lastOrNull() ?: return@Box
        val cursor = state.menuCursor.lastOrNull() ?: 0
        MenuPanel(state, m, page, cursor)
    }
}

@Composable
fun MenuPanel(state: SlotState, m: Metrics, page: MenuPage, cursor: Int) {
    val panelW = if (m.portrait) (m.w / m.u - 32f) else 400f
    val rowH = 36f
    val maxRows = ((m.h / m.u - 150f) / rowH).toInt().coerceIn(4, 9)
    val n = page.items.size
    // Touch scrolling moves the window of rows on its own; moving the cursor (D-pad) brings it back to the cursor.
    var dragged by remember(page) { mutableStateOf<Int?>(null) }
    LaunchedEffect(cursor) { dragged = null }
    val first = dragged ?: (cursor - maxRows / 2).coerceIn(0, (n - maxRows).coerceAtLeast(0))
    val shownFirst by rememberUpdatedState(first)
    val shown = page.items.subList(first, (first + maxRows).coerceAtMost(n))
    Column(
        Modifier.width(panelW.udp()).clip(RoundedCornerShape(14f.udp())).background(SlotColors.Housing)
            .border(1.2f.udp(), SlotColors.Edge, RoundedCornerShape(14f.udp()))
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(page, n, maxRows) {
                var acc = 0f
                val rowPx = rowH * m.u
                detectVerticalDragGestures(onDragStart = { acc = 0f }) { change, dy ->
                    change.consume()
                    acc += dy
                    val step = (acc / rowPx).toInt()
                    if (step != 0) {
                        acc -= step * rowPx
                        dragged = ((dragged ?: shownFirst) - step).coerceIn(0, (n - maxRows).coerceAtLeast(0))
                    }
                }
            }.padding(vertical = 10f.udp()),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16f.udp(), vertical = 6f.udp()), verticalAlignment = Alignment.CenterVertically) {
            if (state.menuStack.size > 1) {
                IconButton(IconKind.Back, 28f) { state.popMenu() }
                Spacer(Modifier.width(10f.udp()))
            }
            Column(Modifier.weight(1f)) {
                SlotText(page.title, 18f, bold = true)
                page.subtitle?.let { SlotText(it, 12f, color = SlotColors.Ink.copy(alpha = .55f)) }
            }
            IconButton(IconKind.Close, 28f) { state.closeOverlay() }
        }
        Spacer(Modifier.height(4f.udp()))
        shown.forEachIndexed { k, item ->
            val idx = first + k
            val sel = idx == cursor && item.selectable
            Row(
                Modifier.fillMaxWidth().height(rowH.udp()).padding(horizontal = 8f.udp())
                    .clip(RoundedCornerShape(9f.udp()))
                    .background(if (sel) SlotColors.Ink else Color.Transparent)
                    .clickable(enabled = item.selectable) { state.tapMenuRow(idx) }
                    .padding(horizontal = 12f.udp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ink = if (sel) SlotColors.CapInk else SlotColors.Ink.copy(alpha = if (item.selectable) 1f else .6f)
                SlotText(item.label, 15.5f, modifier = Modifier.weight(1f), color = ink, bold = sel)
                when (item) {
                    is MenuToggle -> Pill(if (item.get()) "On" else "Off", on = item.get(), selected = sel)
                    is MenuChoice -> {
                        val t = item.options.getOrElse(item.get()) { "" }
                        SlotText(if (sel) "‹ $t ›".replace("‹", "<").replace("›", ">") else t, 14f, color = if (sel) SlotColors.CapInk else SlotColors.Ink.copy(alpha = .75f), bold = sel)
                    }
                    is MenuSub -> SlotText(">", 16f, color = ink.copy(alpha = .7f), bold = true)
                    is MenuInfo -> SlotText(item.value, 14f, color = SlotColors.Ink.copy(alpha = .6f))
                    is MenuAction -> item.detail?.let { SlotText(it, 12f, color = ink.copy(alpha = .6f)) }
                }
            }
        }
        if (n > maxRows) {
            SlotText("${cursor + 1} / $n", 11.5f, color = SlotColors.Ink.copy(alpha = .4f), align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6f.udp()))
        }
        if (state.gamepad) {
            Spacer(Modifier.height(8f.udp()))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Hint("A", "Select"); Spacer(Modifier.width(16f.udp())); Hint("◀▶", "Change"); Spacer(Modifier.width(16f.udp())); Hint("B", "Back")
            }
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, selected: Boolean) {
    Box(
        Modifier.clip(RoundedCornerShape(10f.udp()))
            .background(if (on) SlotColors.Accent else (if (selected) SlotColors.CapInk.copy(alpha = .15f) else SlotColors.Recess))
            .padding(horizontal = 11f.udp(), vertical = 3f.udp()),
    ) { SlotText(text, 12.5f, color = if (on) SlotColors.CapInk else (if (selected) SlotColors.CapInk else SlotColors.Ink.copy(alpha = .7f)), bold = true) }
}

// --- save state switcher ---------------------------------------------------------------------------

@Composable
fun StateSwitcher(state: SlotState, m: Metrics) {
    val n = state.states.size
    val cursor = state.stateCursor
    val pos = remember { Animatable(cursor.toFloat()) }
    LaunchedEffect(cursor) { pos.animateTo(cursor.toFloat(), spring(dampingRatio = .72f, stiffness = 380f)) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f))
            .pointerInput(Unit) { detectTapGestures { state.overlay = Overlay.None; state.host.onAction(GameAction.Resume) } },
    ) {
        Column(Modifier.align(Alignment.TopCenter).padding(top = 22f.udp()), horizontalAlignment = Alignment.CenterHorizontally) {
            SlotText("Save states", 20f, bold = true)
            SlotText(state.playing?.title ?: "", 13f, color = SlotColors.Ink.copy(alpha = .6f))
        }
        Canvas(Modifier.fillMaxSize()) {}
        val cardW = 190f
        val cardH = 190f
        Box(Modifier.fillMaxSize()) {
            for ((i, s) in state.states.withIndex()) {
                val d = i - pos.value
                if (abs(d) > 3.2f) continue
                val t = abs(d).coerceAtMost(1f)
                val scale = 1.12f + (.82f - 1.12f) * t
                val xU = d * (if (m.portrait) 150f else 215f)
                Box(
                    Modifier.align(Alignment.Center)
                        .offset { IntOffset((xU * m.u).roundToInt(), (-6f * m.u).roundToInt()) }
                        .alpha(1f - (abs(d) - 1f).coerceIn(0f, 1f) * .75f - (t * .25f)),
                ) {
                    PolaroidCard(s, selected = i == cursor, scale = scale, w = cardW, h = cardH) { state.stateCursor = i; if (i == cursor) state.pressA() }
                }
            }
            if (n == 0) SlotText("No states yet", 16f, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 22f.udp()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.gamepad) {
                Row(horizontalArrangement = Arrangement.spacedBy(14f.udp())) {
                    Hint("◀▶", "Browse"); Hint("A", "Load"); Hint("Y", "Save here"); Hint("X", "Delete"); Hint("SELECT", "Undo load"); Hint("B", "Back")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10f.udp())) {
                    TouchButton("Back") { state.overlay = Overlay.None; state.host.onAction(GameAction.Resume) }
                    TouchButton("Save here") { state.pressY() }
                    TouchButton("Delete") { state.pressX() }
                    TouchButton("Undo load") { state.host.onAction(GameAction.UndoLoad) }
                }
            }
        }
    }
}


@Composable
private fun PolaroidCard(s: StateSlot, selected: Boolean, scale: Float, w: Float, h: Float, onTap: () -> Unit) {
    val filled = s.thumb != null || s.time.isNotEmpty()
    Column(
        Modifier.width((w * scale).udp()).clip(RoundedCornerShape((6f * scale).udp()))
            .background(Color(0xFFF4F2EC)).clickable(onClick = onTap).padding((10f * scale).udp()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(((w - 20f) * .72f * scale).udp()).background(Color(0xFF16161A)),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = s.thumb
            if (thumb != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawImage(
                        thumb, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                    )
                }
            } else {
                SlotText(if (filled) "Saved" else "Empty", 14f * scale, color = SlotColors.Ink.copy(alpha = .4f))
            }
        }
        Spacer(Modifier.height((8f * scale).udp()))
        SlotText(s.label, 14f * scale, color = SlotColors.CapInk, bold = true)
        SlotText(if (filled) s.time else "—", 11.5f * scale, color = SlotColors.CapInk.copy(alpha = .6f))
    }
}

// --- touch controls ---------------------------------------------------------------------------------

private class TouchButtonSpec(val pad: Pad, val cx: Float, val cy: Float, val r: Float, val label: String)

private class TouchLayout(val dpadCx: Float, val dpadCy: Float, val dpadR: Float, val buttons: List<TouchButtonSpec>, val pills: List<Triple<Pad, Rectish, String>>)

private data class Rectish(val l: Float, val t: Float, val r: Float, val b: Float) {
    fun contains(p: Offset, slack: Float) = p.x in (l - slack)..(r + slack) && p.y in (t - slack)..(b + slack)
}

private fun layoutFor(sys: GameSystem, m: Metrics, tweak: List<Int> = TOUCH_DEFAULT): TouchLayout {
    val u = m.u
    val w = m.w
    val h = m.h
    val portrait = m.portrait
    val bottom = h - (if (portrait) 120f else 70f) * u
    val dpadCx = (if (portrait) 100f else 120f) * u
    val dpadCy = bottom - (if (portrait) 20f else 40f) * u
    val dpadR = (if (portrait) 74f else 80f) * u
    val cx = w - (if (portrait) 100f else 130f) * u
    val cy = dpadCy
    val r = 34f * u
    val btns = mutableListOf<TouchButtonSpec>()
    when (sys.shape) {
        CartShape.SNES -> {
            btns += TouchButtonSpec(Pad.A, cx + 52 * u, cy, r, "A")
            btns += TouchButtonSpec(Pad.B, cx, cy + 50 * u, r, "B")
            btns += TouchButtonSpec(Pad.X, cx, cy - 50 * u, r, "X")
            btns += TouchButtonSpec(Pad.Y, cx - 52 * u, cy, r, "Y")
        }
        CartShape.GENESIS -> {
            btns += TouchButtonSpec(Pad.B, cx + 58 * u, cy - 6 * u, r, "C")
            btns += TouchButtonSpec(Pad.A, cx + 4 * u, cy + 22 * u, r, "B")
            btns += TouchButtonSpec(Pad.Y, cx - 50 * u, cy + 50 * u, r, "A")
        }
        else -> {
            btns += TouchButtonSpec(Pad.A, cx + 36 * u, cy - 20 * u, r, "A")
            btns += TouchButtonSpec(Pad.B, cx - 28 * u, cy + 24 * u, r, "B")
        }
    }
    val pills = mutableListOf<Triple<Pad, Rectish, String>>()
    val py = h - 34f * u
    val pw = 70f * u
    val ph = 24f * u
    val pcx = w / 2f
    pills += Triple(Pad.Select, Rectish(pcx - pw - 12 * u, py, pcx - 12 * u, py + ph), "SELECT")
    pills += Triple(Pad.Start, Rectish(pcx + 12 * u, py, pcx + pw + 12 * u, py + ph), "START")
    val shoulder = sys.shape in setOf(CartShape.GBA, CartShape.SNES, CartShape.GENESIS)
    if (shoulder) {
        pills += Triple(Pad.L1, Rectish(14 * u, 14 * u, 14 * u + 86 * u, 14 * u + 34 * u), "L")
        pills += Triple(Pad.R1, Rectish(w - 14 * u - 86 * u, 14 * u, w - 14 * u, 14 * u + 34 * u), "R")
    }
    pills += Triple(Pad.Menu, Rectish(pcx - 28 * u, 12 * u, pcx + 28 * u, 12 * u + 24 * u), "MENU")
    val t = if (tweak.size == 6) tweak else TOUCH_DEFAULT
    val dS = (60 + 10 * t[0]) / 100f
    val bS = (60 + 10 * t[3]) / 100f
    fun off(i: Int) = (-20 + 5 * i) / 100f
    val scaled = btns.map { b -> TouchButtonSpec(b.pad, cx + (b.cx - cx) * bS + off(t[4]) * w, cy + (b.cy - cy) * bS + off(t[5]) * h, b.r * bS, b.label) }
    return TouchLayout(dpadCx + off(t[1]) * w, dpadCy + off(t[2]) * h, dpadR * dS, scaled, pills)
}

@Composable
fun TouchControls(state: SlotState, sys: GameSystem, m: Metrics) {
    val tweak = state.settings.touchTweaks[sys.id] ?: TOUCH_DEFAULT
    val layout = remember(sys, m, tweak) { layoutFor(sys, m, tweak) }
    var pressed by remember { mutableStateOf(emptySet<Pad>()) }
    Box(
        Modifier.fillMaxSize().pointerInput(layout) {
            awaitPointerEventScope {
                var current = emptySet<Pad>()
                while (true) {
                    val ev = awaitPointerEvent()
                    val now = HashSet<Pad>()
                    for (c in ev.changes) {
                        if (c.pressed) now += hit(layout, c.position)
                        c.consume()
                    }
                    if (now != current) {
                        for (p in current - now) {
                            if (p == Pad.Menu) continue
                            state.host.onButton(p, false)
                        }
                        for (p in now - current) {
                            if (p == Pad.Menu) state.press(Pad.Menu, now = 0L) else state.host.onButton(p, true)
                        }
                        current = now
                        pressed = now
                    }
                }
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawTouch(layout, pressed, m) }
        for (b in layout.buttons) {
            Box(
                Modifier.offset { IntOffset((b.cx - b.r).roundToInt(), (b.cy - b.r).roundToInt()) }
                    .size((b.r * 2f).pxToDp(), (b.r * 2f).pxToDp()),
                contentAlignment = Alignment.Center,
            ) { SlotText(b.label, 20f, color = Color.White.copy(alpha = .85f), bold = true) }
        }
        for ((_, r, label) in layout.pills) {
            Box(
                Modifier.offset { IntOffset(r.l.roundToInt(), r.t.roundToInt()) }
                    .size((r.r - r.l).pxToDp(), (r.b - r.t).pxToDp()),
                contentAlignment = Alignment.Center,
            ) { SlotText(label, 11.5f, color = Color.White.copy(alpha = .85f), bold = true) }
        }
    }
}

private fun hit(l: TouchLayout, p: Offset): Set<Pad> {
    val out = HashSet<Pad>()
    val dx = p.x - l.dpadCx
    val dy = p.y - l.dpadCy
    val dist = kotlin.math.hypot(dx, dy)
    if (dist < l.dpadR * 1.35f && dist > l.dpadR * .16f) {
        if (dx / dist > .38f) out += Pad.Right
        if (dx / dist < -.38f) out += Pad.Left
        if (dy / dist > .38f) out += Pad.Down
        if (dy / dist < -.38f) out += Pad.Up
    }
    for (b in l.buttons) if (kotlin.math.hypot(p.x - b.cx, p.y - b.cy) < b.r * 1.3f) out += b.pad
    for ((pad, r, _) in l.pills) if (r.contains(p, 8f)) out += pad
    return out
}

private fun DrawScope.drawTouch(l: TouchLayout, pressed: Set<Pad>, m: Metrics) {
    val u = m.u
    val base = Color.White
    // d-pad
    val arm = l.dpadR * .36f
    val len = l.dpadR
    val path = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(l.dpadCx - len, l.dpadCy - arm, l.dpadCx + len, l.dpadCy + arm, CornerRadius(arm * .5f)))
        addRoundRect(androidx.compose.ui.geometry.RoundRect(l.dpadCx - arm, l.dpadCy - len, l.dpadCx + arm, l.dpadCy + len, CornerRadius(arm * .5f)))
    }
    drawPath(path, base.copy(alpha = .16f))
    drawPath(path, base.copy(alpha = .35f), style = Stroke(1.6f * u))
    fun arrow(p: Pad, dx: Float, dy: Float) {
        val on = p in pressed
        val c = Offset(l.dpadCx + dx * len * .68f, l.dpadCy + dy * len * .68f)
        if (on) drawCircle(base.copy(alpha = .35f), arm * .8f, c)
        val s = arm * .30f
        val tri = androidx.compose.ui.graphics.Path().apply {
            if (dx != 0f) { moveTo(c.x - dx * s, c.y - s); lineTo(c.x + dx * s, c.y); lineTo(c.x - dx * s, c.y + s) }
            else { moveTo(c.x - s, c.y - dy * s); lineTo(c.x, c.y + dy * s); lineTo(c.x + s, c.y - dy * s) }
            close()
        }
        drawPath(tri, base.copy(alpha = if (on) .95f else .55f))
    }
    arrow(Pad.Left, -1f, 0f); arrow(Pad.Right, 1f, 0f); arrow(Pad.Up, 0f, -1f); arrow(Pad.Down, 0f, 1f)

    for (b in l.buttons) {
        val on = b.pad in pressed
        drawCircle(base.copy(alpha = if (on) .42f else .16f), b.r, Offset(b.cx, b.cy))
        drawCircle(base.copy(alpha = .38f), b.r, Offset(b.cx, b.cy), style = Stroke(1.6f * u))
    }
    for ((pad, r, _) in l.pills) {
        val on = pad in pressed
        drawRoundRect(base.copy(alpha = if (on) .42f else .16f), Offset(r.l, r.t), Size(r.r - r.l, r.b - r.t), CornerRadius((r.b - r.t) / 2f))
        drawRoundRect(base.copy(alpha = .35f), Offset(r.l, r.t), Size(r.r - r.l, r.b - r.t), CornerRadius((r.b - r.t) / 2f), style = Stroke(1.4f * u))
    }
}
