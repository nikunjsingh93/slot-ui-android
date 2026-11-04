package fyi.slot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * The first letters of the games on this shelf as a row of small tabs; tapping one jumps to the first game with that
 * letter (N for every Need for Speed). Shown while the shelf is swiped and for a moment after.
 */
@Composable
fun LetterBar(state: SlotState, onTouch: () -> Unit) {
    val shelf = state.shelf
    val letters = remember(shelf) { shelf.map { state.letterOf(it) }.distinct() }
    if (letters.size < 2) return
    val here = state.current?.let { state.letterOf(it) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6f.udp(), vertical = 3f.udp()),
        horizontalArrangement = Arrangement.spacedBy(2f.udp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (c in letters) {
            val on = c == here
            Box(
                Modifier.size(24f.udp()).clip(RoundedCornerShape(12f.udp()))
                    .background(if (on) SlotColors.Ink else Color.Transparent)
                    .clickable { onTouch(); state.jumpToLetter(c) },
                contentAlignment = Alignment.Center,
            ) { SlotText(c.toString(), 12.5f, bold = true, color = if (on) SlotColors.CapInk else SlotColors.Ink.copy(alpha = .85f)) }
        }
    }
}

/**
 * "List + cart" shelf view: the games as a list on the left, between the system bar and the bottom bar, beside the
 * carts and the slot (which move to the right). The white highlight stays fixed in the middle and the names roll
 * through it like a slot machine, glued to the shelf's own position so the list and the carts move as one. A second,
 * dark copy of the list is clipped to the highlight so a name changes colour exactly where it passes into it.
 */
@Composable
fun GameListPanel(state: SlotState, m: Metrics) {
    val shelf = state.shelf
    val n = shelf.size
    if (n == 0) return
    val density = LocalDensity.current
    val rowPx = with(density) { ROW.udp().toPx() }
    val headPx = with(density) { HEAD.udp().toPx() }
    val top = m.topBar + 6f * m.u
    val bottom = m.bandTop - 6f * m.u
    val listH = (bottom - top).coerceAtLeast(rowPx)
    // half a panel of empty space above and below, so any game can sit in the middle
    val halfPad = ((listH - rowPx) / 2f).roundToInt().coerceAtLeast(0)
    // three copies when the shelf loops (the middle one is shown), so the list wraps round like the carts
    val ring = if (n >= 3) 1 else 0
    val rows = remember(shelf) { buildRows(state, shelf, 2 * ring + 1) }
    val gameRow = remember(rows) { rows.withIndex().filter { it.value.game >= 0 }.groupBy { it.value.copy }.mapValues { (_, v) -> v.map { it.index } } }
    val light = rememberLazyListState()
    val dark = rememberLazyListState()
    LaunchedEffect(rows, halfPad) {
        snapshotFlow { state.scroll }.collect { s ->
            val base = kotlin.math.floor(s)
            val i = ((base.toInt() % n) + n) % n
            val at = gameRow[ring]?.getOrNull(i) ?: return@collect
            // the step to the next game is one row, plus its letter heading when it starts a new letter
            val next = rows.getOrNull(at + 1)
            val step = rowPx + if (next != null && next.game < 0) headPx else 0f
            val off = ((s - base) * step).roundToInt()
            light.scrollToItem(at, off)
            dark.scrollToItem(at, off)
        }
    }
    val panelW = m.w * 0.44f
    val padDp = with(density) { halfPad.toDp() }
    Box(
        Modifier.offset { IntOffset(0, top.roundToInt()) }.width(with(density) { panelW.toDp() }).height(with(density) { listH.toDp() })
            .padding(start = 14f.udp(), end = 6f.udp())
            // dragging anywhere on the list moves the shelf itself (same momentum as the carts); the list just follows
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { dy -> state.dragging = true; state.scroll -= dy / rowPx },
                onDragStarted = { state.flingVel = 0f },
                onDragStopped = { v -> state.fling(-v / rowPx) },
            ),
    ) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(ROW.udp()).clip(RoundedCornerShape(9f.udp())).background(SlotColors.Ink))
        // the list as it reads everywhere else (light ink)
        LazyColumn(
            Modifier.fillMaxSize(),
            state = light, userScrollEnabled = false, contentPadding = PaddingValues(vertical = padDp),
        ) {
            items(rows.size, key = { rows[it].key }) { k -> ListRow(state, shelf, rows[k], dark = false) }
        }
        // the same list in dark ink, visible only inside the highlight
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().height(ROW.udp()).clipToBounds()
                .clickable { state.current?.let { if (state.screen == Screen.Browse) state.insert(it, resume = true) } },
        ) {
            LazyColumn(
                Modifier.requiredHeight(with(density) { listH.toDp() }).fillMaxWidth(),
                state = dark, userScrollEnabled = false, contentPadding = PaddingValues(vertical = padDp),
            ) {
                items(rows.size, key = { rows[it].key }) { k -> ListRow(state, shelf, rows[k], dark = true) }
            }
        }
    }
}

/** A line of the list: a game ([game] index into the shelf) or a letter heading ([game] < 0). */
private class ListLine(val game: Int, val letter: Char, val copy: Int, val key: String)

private fun buildRows(state: SlotState, shelf: List<Game>, copies: Int): List<ListLine> {
    val out = ArrayList<ListLine>()
    for (c in 0 until copies) for (i in shelf.indices) {
        val g = shelf[i]
        if (i == 0 || state.letterOf(shelf[i - 1]) != state.letterOf(g)) out += ListLine(-1, state.letterOf(g), c, "h$c:$i")
        out += ListLine(i, state.letterOf(g), c, "g$c:${g.id}")
    }
    return out
}

@Composable
private fun ListRow(state: SlotState, shelf: List<Game>, line: ListLine, dark: Boolean) {
    if (line.game < 0) {
        Box(Modifier.fillMaxWidth().height(HEAD.udp()).padding(start = 10f.udp()), contentAlignment = Alignment.BottomStart) {
            SlotText(line.letter.toString(), 12f, bold = true, color = (if (dark) SlotColors.CapInk else SlotColors.Ink).copy(alpha = .45f))
        }
        return
    }
    val g = shelf[line.game]
    val ink = if (dark) SlotColors.CapInk else SlotColors.Ink.copy(alpha = .85f)
    Row(
        Modifier.fillMaxWidth().height(ROW.udp())
            .then(if (dark) Modifier else Modifier.clickable {
                if (line.game == state.selected) { if (state.screen == Screen.Browse) state.insert(g, resume = true) } else state.selectIndex(line.game)
            })
            .padding(horizontal = 10f.udp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SlotText(g.title, 15f, bold = dark, color = ink, modifier = Modifier.weight(1f))
        if (g.hasSave) {
            Spacer(Modifier.width(6f.udp()))
            SlotText("saved", 11f, color = ink.copy(alpha = .55f))
        }
    }
}

private const val ROW = 38f
private const val HEAD = 22f
