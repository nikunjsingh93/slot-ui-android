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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
 * through it like a slot machine, glued to the shelf's own position so the list and the carts move as one.
 */
@Composable
fun GameListPanel(state: SlotState, m: Metrics) {
    val shelf = state.shelf
    val n = shelf.size
    if (n == 0) return
    val list = rememberLazyListState()
    val sel = state.selected
    val density = LocalDensity.current
    val rowPx = with(density) { ROW.udp().toPx() }
    val top = m.topBar + 6f * m.u
    val bottom = m.bandTop - 6f * m.u
    val listH = (bottom - top).coerceAtLeast(rowPx)
    // half a panel of empty space above the first game and below the last, so every game can reach the middle
    val halfPad = ((listH - rowPx) / 2f).roundToInt().coerceAtLeast(0)
    LaunchedEffect(n, halfPad) {
        snapshotFlow { state.scroll }.collect { s ->
            val base = kotlin.math.floor(s)
            val i = ((base.toInt() % n) + n) % n
            list.scrollToItem(i, ((s - base) * rowPx).roundToInt())
        }
    }
    val panelW = m.w * 0.44f
    Box(
        Modifier.offset { IntOffset(0, top.roundToInt()) }.width(with(density) { panelW.toDp() }).height(with(density) { listH.toDp() })
            .padding(start = 14f.udp(), end = 6f.udp()),
    ) {
        // the fixed highlight, drawn behind the rows
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().height(ROW.udp()).clip(RoundedCornerShape(9f.udp())).background(SlotColors.Ink),
        )
        LazyColumn(
            Modifier.fillMaxSize()
                // dragging the list moves the shelf itself (same momentum as the carts); the list just follows it
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { dy -> state.dragging = true; state.scroll -= dy / rowPx },
                    onDragStarted = { state.flingVel = 0f },
                    onDragStopped = { v -> state.fling(-v / rowPx) },
                ),
            state = list,
            userScrollEnabled = false,
            contentPadding = PaddingValues(vertical = with(density) { halfPad.toDp() }),
        ) {
            itemsIndexed(shelf, key = { _, g -> g.id }) { i, g ->
                val on = i == sel
                val newLetter = i == 0 || state.letterOf(shelf[i - 1]) != state.letterOf(g)
                val ink = if (on) SlotColors.CapInk else SlotColors.Ink.copy(alpha = .85f)
                Row(
                    Modifier.fillMaxWidth().height(ROW.udp())
                        .clickable {
                            if (on) { if (state.screen == Screen.Browse) state.insert(g, resume = true) } else state.selectIndex(i)
                        }
                        .padding(end = 10f.udp()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(26f.udp()), contentAlignment = Alignment.Center) {
                        if (newLetter) SlotText(state.letterOf(g).toString(), 12f, bold = true, color = ink.copy(alpha = .5f))
                    }
                    SlotText(g.title, 15f, bold = on, color = ink, modifier = Modifier.weight(1f))
                    if (g.hasSave) {
                        Spacer(Modifier.width(6f.udp()))
                        SlotText("saved", 11f, color = ink.copy(alpha = .55f))
                    }
                }
            }
        }
    }
}

private const val ROW = 38f
