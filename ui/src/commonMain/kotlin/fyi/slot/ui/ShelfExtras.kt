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

/**
 * The first letters of the games on this shelf as a row of small tabs; tapping one jumps to the first game with that
 * letter (N for every Need for Speed). Shown on touch screens, and for a few seconds after a swipe on handhelds.
 */
@Composable
fun LetterBar(state: SlotState, onTouch: () -> Unit) {
    val shelf = state.shelf
    val letters = remember(shelf) { shelf.map { state.letterOf(it) }.distinct() }
    if (letters.size < 2) return
    val here = state.current?.let { state.letterOf(it) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).clip(RoundedCornerShape(14f.udp()))
            .background(Color.Black.copy(alpha = .45f)).padding(horizontal = 6f.udp(), vertical = 3f.udp()),
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
    // keep the selection in view when it moves by D-pad or by a swipe on the cart
    LaunchedEffect(sel, shelf.size) {
        val info = list.layoutInfo.visibleItemsInfo
        val visible = info.filter { it.offset >= 0 && it.offset + it.size <= list.layoutInfo.viewportEndOffset }.map { it.index }
        if (sel !in visible) list.scrollToItem((sel - 3).coerceAtLeast(0))
    }
    Column(modifier.padding(start = 14f.udp(), end = 6f.udp(), top = 10f.udp(), bottom = 10f.udp())) {
        SlotText(state.system?.title ?: "", 16f, bold = true, color = SlotColors.Ink.copy(alpha = .7f), modifier = Modifier.padding(start = 8f.udp(), bottom = 6f.udp()))
        LazyColumn(Modifier.fillMaxSize(), state = list) {
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
