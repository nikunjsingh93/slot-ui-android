package fyi.slot.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Runs [step] once per display frame with the elapsed seconds until it returns false.
 *
 * All of slot's motion goes through this instead of Compose's animation APIs: those multiply every
 * duration by the system "animator duration scale", which many handhelds set to 0 (so nothing animates).
 */
suspend fun runFrames(step: (dt: Float) -> Boolean) {
    var last = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
        last = now
        if (!step(dt)) return
    }
}

/** Root of the slot UI. Draws the shelf, HUD, core picker and menus; transparent while a game runs. */
@Composable
fun SlotRoot(state: SlotState, modifier: Modifier = Modifier, gameProcess: Boolean = false) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val m = remember(w, h, state.settings.cartSize) { Metrics(w, h, state.settings.cartSize.factor) }
        CompositionLocalProvider(LocalU provides m.u) {
            val assets = rememberCartAssets()
            if (!gameProcess) {
                if (state.settings.shelfList && state.shelf.isNotEmpty()) ListShelf(state, m) { m2 -> Browser(state, m2, assets) }
                else Browser(state, m, assets)
            }
            if (gameProcess || state.overlay != Overlay.None) Menus(state, m, gameProcess)
            ToastHost(state, m)
        }
    }
}

@Composable
private fun Browser(state: SlotState, m: Metrics, assets: CartAssets) {
    val screen = state.screen
    val shelf = state.shelf
    val n = shelf.size
    val front = remember(m) { buildSlotFront(m) }

    // 0 = on the shelf .. 1 = the cart is seated in the slot (slot's `seat`)
    var seat by remember { mutableFloatStateOf(if (screen == Screen.Playing) 1f else 0f) }
    // black overlay: the slot chrome fades as the game's screen powers on / off
    var power by remember { mutableFloatStateOf(if (screen == Screen.Playing) 1f else 0f) }

    // The shelf spring, hold-to-repeat and drag settling all run on the frame clock.
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(state.target, state.heldDir, state.dragging || state.flingVel != 0f) }.collectLatest {
            runFrames { dt -> state.stepShelf(dt) }
        }
    }

    // Insert / eject choreography (timings from slot's app.rs).
    val phase = when (screen) {
        is Screen.Inserting -> 1
        Screen.Playing -> 2
        Screen.Ejecting -> 3
        else -> 0
    }
    LaunchedEffect(phase) {
        when (phase) {
            1 -> {
                var t = 0f
                var clunk = false
                power = 0f
                runFrames { dt ->
                    t += dt
                    seat = min(t / SEATED_AT, 1f)
                    if (!clunk && seat >= 0.42f) { clunk = true; state.host.sfx(Sfx.Insert) }
                    t < INSERT_S
                }
                state.finishInsert()
            }
            2 -> {
                seat = 1f
                var t = 0f
                runFrames { dt -> t += dt; power = min(t / 0.22f, 1f); t < 0.22f }
            }
            3 -> {
                seat = 1f
                val hold = 0.15f
                var t = -hold
                var popped = false
                runFrames { dt ->
                    t += dt
                    power = (-t / hold).coerceIn(0f, 1f)
                    if (t >= 0f && !popped) { popped = true; state.host.sfx(Sfx.Eject) }
                    seat = if (t < 0f) 1f else 1f - (t / EJECT_S).coerceIn(0f, 1f)
                    t < EJECT_S
                }
                seat = 0f
                state.finishEject()
            }
            else -> { seat = 0f; power = 0f }
        }
    }

    // Hold A for a fresh start.
    LaunchedEffect(state.aDown) {
        if (state.aDown) {
            runFrames { dt ->
                state.aHold += dt * 1000f
                if (state.aHold >= SlotState.HOLD_FRESH_MS) { state.holdCompleted(); false } else state.aDown
            }
        }
    }

    val current = state.current
    val spec0 = current?.let { CartSpecs.of(it) }
    val pickerOpen = screen is Screen.CorePicker
    val ui = (1f - seat * 4f).coerceIn(0f, 1f)
    val vertical = state.settings.shelfList
    // a step on the shelf: one cart width across, or (list view) one cart height down
    val cwSel = spec0?.let { if (vertical) it.h * m.cartScale(it) * VERTICAL_GAP else it.w * m.cartScale(it) } ?: (300f * m.u)

    Box(Modifier.fillMaxSize().background(SlotColors.Recess)) {
        Canvas(
            Modifier.fillMaxSize()
                .draggable(
                    orientation = if (vertical) Orientation.Vertical else Orientation.Horizontal,
                    enabled = screen == Screen.Browse && n > 1,
                    state = rememberDraggableState { dx ->
                        state.dragging = true
                        state.scroll -= dx / cwSel
                    },
                    onDragStarted = { state.flingVel = 0f },
                    onDragStopped = { velocity -> state.fling(-velocity / cwSel) },
                )
                .pointerInput(n, m) {
                    detectTapGestures(
                        onTap = { at -> handleTap(state, m, at) },
                        onLongPress = { at ->
                            if (state.screen == Screen.Browse && hitCart(state, m, at) == 0) state.openCorePicker()
                        },
                    )
                },
        ) {
            drawShelfBackdrop(m)
            val rest = state.target - state.scroll
            // while moving, carts are placed around the one nearest the middle of the moving shelf
            val centre = kotlin.math.round(state.scroll).toInt()
            val inSeat = seat > 0f
            // far first, selected last
            // the list view shows only the selected cart beside the list
            val reach = if (vertical) 2 else 4
            val order = (-reach..reach).sortedByDescending { abs(it) }
            for (slot in order) {
                val g = (if (n >= 3) ringGame(state, centre + slot) else shelfGame(state, slot)) ?: continue
                if (slot == 0 && (inSeat || pickerOpen)) continue
                val painter = assets.painterFor(g, state.covers[g.id])
                val off = if (n >= 3) (centre + slot) - state.scroll else rest + slot
                val pose = if (vertical) verticalPose(m, painter.spec, off, seat) else shelfPose(m, painter.spec, off, recede = seat)
                val w = painter.w * pose.scale
                if (pose.cx + w / 2f < 0f || pose.cx - w / 2f > m.w || pose.alpha <= 0f) continue
                drawCartPose(painter, pose, 24f * m.u)
            }
            if (n == 0) drawEmpty(m)
            if (inSeat) drawRect(Color.Black.copy(alpha = seat))
            drawSlotBack(m)
            if (inSeat) {
                val g = shelfGame(state, 0)
                if (g != null) {
                    val painter = assets.painterFor(g, state.covers[g.id])
                    val rcx = m.cx + rest * (painter.w * m.cartScale(painter.spec))
                    drawCartPose(painter, seatedPose(m, painter.spec, rcx, seat), 24f * m.u)
                }
            }
            drawSlotFront(m, front)
            if (power > 0f) drawRect(Color.Black.copy(alpha = power))
        }

        // --- HUD ------------------------------------------------------------------------------
        val hudAlpha = if (pickerOpen) 0f else ui
        Box(Modifier.fillMaxSize().alpha(hudAlpha)) {
            TopBar(state, m)
            // letter tabs: only while the shelf is being swiped, fading out a moment after it stops
            var letterPing by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            var lettersShown by remember { androidx.compose.runtime.mutableStateOf(false) }
            val moving = state.dragging || state.flingVel != 0f
            LaunchedEffect(moving, letterPing) {
                if (moving) lettersShown = true else if (lettersShown) { delay(2500); lettersShown = false }
            }
            val lettersAlpha by androidx.compose.animation.core.animateFloatAsState(if (lettersShown) 1f else 0f, tween(if (lettersShown) 120 else 400), label = "letters")
            if (n > 1 && !state.settings.shelfList && lettersAlpha > 0.01f) {
                Box(Modifier.fillMaxWidth().offset { IntOffset(0, m.topBar.roundToInt()) }.padding(horizontal = 16f.udp()).alpha(lettersAlpha), contentAlignment = Alignment.TopCenter) {
                    LetterBar(state) { letterPing++ }
                }
            }
            BasinTitle(state, m)
            EmptyNote(state, m)
            Footer(state, m)
        }

        if (screen is Screen.CorePicker) CorePicker(state, m, assets, screen)
    }
}

/** The game at ring position [slot] relative to the selection, or null when there is no such cart. */
private fun shelfGame(state: SlotState, slot: Int): Game? {
    val shelf = state.shelf
    val n = shelf.size
    if (n == 0) return null
    if (n == 1) return if (slot == 0) shelf[0] else null
    if (n == 2) {
        val i = state.selected + slot
        return shelf.getOrNull(i)
    }
    val i = (((state.selected + slot) % n) + n) % n
    return shelf[i]
}

/** The ring slot (…-1, 0, 1…) under [at], preferring the nearer to the centre. */
private fun hitCart(state: SlotState, m: Metrics, at: Offset): Int? {
    val rest = state.target - state.scroll
    var best: Int? = null
    var bestD = Float.MAX_VALUE
    for (slot in -3..3) {
        val g = shelfGame(state, slot) ?: continue
        val spec = CartSpecs.of(g)
        val p = if (state.settings.shelfList) verticalPose(m, spec, rest + slot, 0f) else shelfPose(m, spec, rest + slot)
        val w = spec.w * p.scale
        val h = spec.h * p.scale
        if (at.x in (p.cx - w / 2)..(p.cx + w / 2) && at.y in (p.footY - h)..p.footY) {
            val d = abs(rest + slot)
            if (d < bestD) { bestD = d; best = slot }
        }
    }
    return best
}

private fun handleTap(state: SlotState, m: Metrics, at: Offset) {
    if (state.screen != Screen.Browse) return
    val hit = hitCart(state, m, at)
    when {
        hit == 0 -> state.current?.let { state.insert(it, resume = true) }
        hit != null -> state.select(((state.selected + hit) % state.shelf.size + state.shelf.size) % state.shelf.size)
        state.shelf.isEmpty() -> state.host.onPickLibrary()
        at.y > m.bandTop && abs(at.x - m.cx) < m.mouthW / 2f -> state.current?.let { state.insert(it, resume = true) }
    }
}

private fun DrawScope.drawEmpty(m: Metrics) {
    val spec = CartSpecs.gba
    val s = m.cartScale(spec)
    val w = spec.w * s
    val h = spec.h * s
    val left = m.cx - w / 2
    val top = m.foot(spec) - h
    translate(left, top) {
        scale(s, s, pivot = Offset.Zero) {
            drawPath(
                spec.outline, SlotColors.Edge.copy(alpha = .8f),
                style = Stroke(2.2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7f, 6f))),
            )
        }
    }
}

@Composable
private fun TopBar(state: SlotState, m: Metrics) {
    Box(Modifier.fillMaxWidth().height(m.topBar.pxToDp()).padding(horizontal = 24f.udp())) {
        Box(Modifier.align(if (m.portrait) Alignment.BottomCenter else Alignment.Center).fillMaxWidth().padding(bottom = if (m.portrait) 8f.udp() else 0f.udp())) {
            if (state.systems.isNotEmpty()) SystemPills(state.systems, state.systemIndex) { state.selectSystem(it) }
        }
    }
}

/** The game's name, just above the slot and below the carts, so it never hides the slot itself. */
@Composable
private fun BasinTitle(state: SlotState, m: Metrics) {
    val g = state.current ?: return
    val textH = m.titleH
    Box(
        Modifier.fillMaxWidth().height(textH.pxToDp()).offset { IntOffset(0, m.shelfBottom.roundToInt()) },
        contentAlignment = Alignment.Center,
    ) {
        val holding = state.aDown && state.aHold > 180f
        if (holding) {
            val prog = (state.aHold / SlotState.HOLD_FRESH_MS).coerceIn(0f, 1f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(150f.udp()).height(4f.udp()).clip(RoundedCornerShape(2f.udp())).background(SlotColors.Recess)) {
                    Box(Modifier.fillMaxSize().offset { IntOffset(((prog - 1f) * 150f * m.u).roundToInt(), 0) }.background(SlotColors.Accent))
                }
                Spacer(Modifier.height(3f.udp()))
                SlotText("Keep holding for a new game", 11f, color = SlotColors.Accent)
            }
        } else {
            Crossfade(g.id, animationSpec = tween(120)) { id ->
                val title = state.shelf.firstOrNull { it.id == id }?.title ?: g.title
                SlotText(
                    title, 20f, bold = true, align = TextAlign.Center,
                    modifier = Modifier.width(((m.w / m.u).coerceAtMost(330f)).udp()),
                )
            }
        }
    }
}

@Composable
private fun EmptyNote(state: SlotState, m: Metrics) {
    if (state.shelf.isNotEmpty()) return
    val spec = CartSpecs.gba
    val top = m.foot(spec) + 24f * m.u
    Column(
        Modifier.fillMaxWidth().offset { IntOffset(0, top.roundToInt()) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.scanning) {
            SlotText("Scanning your library...", 18f, bold = true)
        } else {
            SlotText("No games yet", 20f, bold = true)
            Spacer(Modifier.height(4f.udp()))
            SlotText(
                if (state.gamepad) "Press A to choose your ROM folder" else "Tap anywhere to choose your ROM folder",
                13.5f, color = SlotColors.Ink.copy(alpha = .6f),
            )
        }
    }
}

/** Battery on the left, clock on the right, in the band either side of the slot (slot's footer). */
@Composable
private fun Footer(state: SlotState, m: Metrics) {
    Box(Modifier.fillMaxSize()) {
        // left: battery, then the settings button (what SELECT does on a controller)
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 22f.udp()).height(m.bandH.pxToDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BatteryGauge(state.hud)
            Spacer(Modifier.width(12f.udp()))
            IconButton(IconKind.Gear, 32f) { state.openSettings() }
        }
        // right: the app drawer button (Y), then the clock
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 22f.udp()).height(m.bandH.pxToDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(IconKind.Grid, 32f) { state.openDrawer() }
            Spacer(Modifier.width(12f.udp()))
            SlotText(state.hud.clock, 14f, color = SlotColors.Ink.copy(alpha = .8f))
        }
    }
}

@Composable
fun TouchButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(26f.udp()).clip(RoundedCornerShape(13f.udp())).background(SlotColors.Recess)
            .clickable(onClick = onClick).padding(horizontal = 14f.udp()),
        contentAlignment = Alignment.Center,
    ) { SlotText(label, 13f, color = SlotColors.Ink.copy(alpha = .85f)) }
}

@Composable
private fun ToastHost(state: SlotState, m: Metrics) {
    val t = state.toast ?: return
    LaunchedEffect(t) { delay(2600); if (state.toast === t) state.toast = null }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = (m.bandH / m.u + 18f).udp())
                .clip(RoundedCornerShape(10f.udp())).background(SlotColors.Housing)
                .padding(horizontal = 18f.udp(), vertical = 9f.udp()),
        ) { SlotText(t.text, 14f) }
    }
}

@Composable
internal fun Float.pxToDp() = with(androidx.compose.ui.platform.LocalDensity.current) { this@pxToDp.toDp() }

/** The game at unbounded ring position [pos] (the shelf's own coordinate). */
private fun ringGame(state: SlotState, pos: Int): Game? {
    val shelf = state.shelf
    val n = shelf.size
    if (n == 0) return null
    return shelf[((pos % n) + n) % n]
}

/** List view: carts stacked top to bottom, one cart height (times this) apart. */
private const val VERTICAL_GAP = 0.82f

/** Where a cart [offset] places from the selection sits when the shelf runs top to bottom (list view). */
private fun verticalPose(m: Metrics, spec: CartSpec, offset: Float, recede: Float): CartPose {
    val s0 = m.cartScale(spec)
    val t = kotlin.math.min(kotlin.math.abs(offset), 1f)
    val scale = s0 * (1f + (SIDE_SCALE - 1f) * t)
    // neighbours stay faint so the selected cart, the title and the slot read clearly
    val alpha = (1f + (0.32f - 1f) * t) * (1f - recede.coerceIn(0f, 1f))
    return CartPose(m.cx, m.foot(spec) + offset * spec.h * s0 * VERTICAL_GAP, scale, alpha)
}
