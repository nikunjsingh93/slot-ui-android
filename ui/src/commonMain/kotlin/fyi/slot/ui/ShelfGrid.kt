package fyi.slot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shelf view "Grid": every game of the current system at once, as a grid of its carts and discs (cover art on the
 * label), between the system bar and the slot. The selection is the same as the carts view, so the D-pad, A to play,
 * Start for the core picker and L1/R1 for systems all work as usual; Up/Down move a row. Tap a game to select it, tap
 * it again (or long-press) to play or pick its core. Starting a game hides the grid so the cart drops into the slot.
 */
@Composable
fun ShelfGrid(state: SlotState, m: Metrics, assets: CartAssets) {
    val shelf = state.shelf
    val n = shelf.size
    if (n == 0) return
    val top = m.topBar
    val height = (m.bandTop - top).coerceAtLeast(m.u * 100f)
    // Settings > Interface > Cart size makes the tiles bigger or smaller
    val cell = 100f * state.settings.cartSize.factor
    // tiles take the shape of this system's carts (wide for GBA, square for discs), so rows waste no space
    val aspect = remember(shelf) { CartSpecs.of(shelf[0]).let { (it.w / it.h).coerceIn(.8f, 1.6f) } }
    val cols = ((m.w / m.u - 24f) / cell).toInt().coerceIn(2, 12)
    LaunchedEffect(cols) { state.gridCols = cols }
    DisposableEffect(Unit) { onDispose { state.coverRange = null } }

    key(state.systemIndex) {
        val grid = rememberLazyGridState(initialFirstVisibleItemIndex = (state.selected / cols - 1).coerceAtLeast(0) * cols)
        // tell the host which games are on screen, so their art loads first
        LaunchedEffect(grid, n) {
            snapshotFlow {
                val vis = grid.layoutInfo.visibleItemsInfo
                if (vis.isEmpty()) null else vis.first().index..vis.last().index
            }.collect { state.coverRange = it }
        }
        // keep the selection on screen when it is moved with the D-pad
        LaunchedEffect(state.selected, cols) {
            val info = grid.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == state.selected }
            val shown = item != null && item.offset.y >= info.viewportStartOffset && item.offset.y + item.size.height <= info.viewportEndOffset
            if (!shown) grid.animateScrollToItem((state.selected / cols - 1).coerceAtLeast(0) * cols)
        }
        Box(
            Modifier.offset { IntOffset(0, top.roundToInt()) }.fillMaxWidth().height(height.pxToDp())
                .background(SlotColors.Recess),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                state = grid,
                contentPadding = PaddingValues(horizontal = 12f.udp(), vertical = 10f.udp()),
                horizontalArrangement = Arrangement.spacedBy(6f.udp()),
                verticalArrangement = Arrangement.spacedBy(6f.udp()),
            ) {
                items(n, key = { shelf[it].id }) { i -> GridTile(state, assets, shelf[i], i, aspect) }
            }
        }
    }
}

@Composable
private fun GridTile(state: SlotState, assets: CartAssets, g: Game, i: Int, aspect: Float) {
    val on = i == state.selected
    Column(
        Modifier.clip(RoundedCornerShape(10f.udp()))
            .background(if (on) SlotColors.Ink.copy(alpha = .14f) else Color.Transparent)
            .pointerInput(g.id, i) {
                detectTapGestures(
                    onTap = {
                        if (state.screen != Screen.Browse) return@detectTapGestures
                        if (state.selected == i) state.insert(g, resume = true) else state.selectIndex(i)
                    },
                    onLongPress = {
                        if (state.screen != Screen.Browse) return@detectTapGestures
                        state.selectIndex(i)
                        state.openCorePicker()
                    },
                )
            }
            .padding(horizontal = 3f.udp(), vertical = 4f.udp()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(aspect)) {
            val painter = assets.painterFor(g, state.covers[g.id])
            val spec = painter.spec
            val s = min(size.width * .9f / spec.w, size.height * .9f / spec.h)
            drawCartPose(painter, CartPose(size.width / 2f, size.height / 2f + spec.h * s / 2f, s, 1f), 20f * s)
        }
        Box(Modifier.fillMaxWidth().height(34f.udp()), contentAlignment = Alignment.TopCenter) {
            SlotText(
                g.title, 11f, bold = on, align = TextAlign.Center, maxLines = 2,
                color = if (on) SlotColors.Ink else SlotColors.Ink.copy(alpha = .7f),
            )
        }
    }
}
