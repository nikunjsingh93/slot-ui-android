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
 * "List + cart" shelf view: the games as a list on one side (left on wide screens, top on tall ones) and slot's own
 * shelf with the selected cart and the slot on the other. Tapping a game selects it; tapping the selected one plays it.
 */
@Composable
fun ListShelf(state: SlotState, m: Metrics, shelfView: @Composable (Metrics) -> Unit) {
    val wide = !m.portrait
    val body: @Composable (Modifier, Modifier) -> Unit = { listMod, cartMod ->
        GameList(state, listMod)
        BoxWithConstraints(cartMod.clipToBounds()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val m2 = remember(w, h, state.settings.cartSize) { Metrics(w, h, state.settings.cartSize.factor) }
            CompositionLocalProvider(LocalU provides m2.u) { shelfView(m2) }
        }
    }
    Box(Modifier.fillMaxSize().background(SlotColors.Recess)) {
        if (wide) Row(Modifier.fillMaxSize()) { body(Modifier.fillMaxHeight().weight(.42f), Modifier.fillMaxHeight().weight(.58f)) }
        else Column(Modifier.fillMaxSize()) { body(Modifier.fillMaxWidth().weight(.42f), Modifier.fillMaxWidth().weight(.58f)) }
    }
}

@Composable
private fun GameList(state: SlotState, modifier: Modifier) {
    val shelf = state.shelf
    val list = rememberLazyListState()
    val sel = state.selected
    // The selected game stays in the middle of the list and the list slides under it, whether the move came from the
    // D-pad, a swipe on the carts or a tap. While the carts are being swiped the list follows them without animating.
    var listH by remember { mutableIntStateOf(0) }
    val rowPx = with(LocalDensity.current) { 38f.udp().toPx() }
    val headPx = with(LocalDensity.current) { 26f.udp().toPx() }
    // half a list of empty space above the first game and below the last, so they too can sit in the middle
    val halfPad = (listH / 2f - rowPx / 2f).roundToInt().coerceAtLeast(0)
    LaunchedEffect(sel, shelf.size, listH) {
        if (listH == 0 || shelf.isEmpty()) return@LaunchedEffect
        // a game that opens a new letter carries its letter heading above it; centre the game row, not the heading
        val heading = if (sel == 0 || state.letterOf(shelf[sel - 1]) != state.letterOf(shelf[sel])) headPx else 0f
        val offset = ((listH - rowPx) / 2f - heading).roundToInt() - halfPad
        if (state.dragging || state.flingVel != 0f) list.scrollToItem(sel, -offset) else list.animateScrollToItem(sel, -offset)
    }
    Column(modifier.padding(start = 14f.udp(), end = 6f.udp(), top = 10f.udp(), bottom = 10f.udp())) {
        SlotText(
            (state.system?.title ?: "").uppercase(), 11.5f, bold = true, align = TextAlign.Center,
            color = SlotColors.Ink.copy(alpha = .5f), modifier = Modifier.fillMaxWidth().padding(bottom = 8f.udp()),
        )
        LazyColumn(
            Modifier.fillMaxSize().onSizeChanged { listH = it.height }, state = list,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = with(LocalDensity.current) { halfPad.toDp() }),
        ) {
            itemsIndexed(shelf, key = { _, g -> g.id }) { i, g ->
                val on = i == sel
                val newLetter = i == 0 || state.letterOf(shelf[i - 1]) != state.letterOf(g)
                if (newLetter) {
                    SlotText(state.letterOf(g).toString(), 12f, bold = true, color = SlotColors.Ink.copy(alpha = .45f), modifier = Modifier.padding(start = 10f.udp(), top = 6f.udp(), bottom = 2f.udp()))
                }
                Row(
                    Modifier.fillMaxWidth().height(38f.udp()).clip(RoundedCornerShape(9f.udp()))
                        .background(if (on) SlotColors.Ink else Color.Transparent)
                        .clickable {
                            if (on) { if (state.screen == Screen.Browse) state.insert(g, resume = true) } else state.selectIndex(i)
                        }
                        .padding(horizontal = 10f.udp()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SlotText(g.title, 15f, bold = on, color = if (on) SlotColors.CapInk else SlotColors.Ink.copy(alpha = .9f), modifier = Modifier.weight(1f))
                    if (g.hasSave) {
                        Spacer(Modifier.width(6f.udp()))
                        SlotText("saved", 11f, color = (if (on) SlotColors.CapInk else SlotColors.Ink).copy(alpha = .5f))
                    }
                }
            }
        }
    }
}
