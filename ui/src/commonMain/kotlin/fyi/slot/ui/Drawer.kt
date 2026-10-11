@file:OptIn(ExperimentalFoundationApi::class)

package fyi.slot.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

/**
 * The app drawer (Y on the shelf): every launchable app on the device, as a grid or a list, in three sizes.
 * D-pad moves, A opens, Y (or a long press) shows Open / App info / Uninstall, X flips grid/list, L1/R1 change the size;
 * on a touchscreen tap an app or use the header.
 */
@Composable
fun AppDrawer(state: SlotState, m: Metrics) {
    val s = state.settings
    val apps = state.drawerApps
    val icon = s.drawerSize.icon
    val cell = icon + 40f
    val cols = ((m.w / m.u - 56f) / cell).toInt().coerceAtLeast(2)
    LaunchedEffect(cols) { state.drawerCols = cols }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF0E0E11))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28f.udp(), vertical = 18f.udp())) {
            DrawerHeader(state, apps.size)
            Spacer(Modifier.height(12f.udp()))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (apps.isEmpty()) {
                    SlotText("Loading apps...", 16f, color = SlotColors.Ink.copy(alpha = .6f), modifier = Modifier.align(Alignment.Center))
                } else if (s.drawerGrid) DrawerGrid(state, apps, icon, cols) else DrawerList(state, apps, icon)
            }
            if (state.gamepad) {
                Spacer(Modifier.height(10f.udp()))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Hint("A", "Open"); Spacer(Modifier.width(16f.udp()))
                    Hint("Y", "Options"); Spacer(Modifier.width(16f.udp()))
                    Hint("X", if (s.drawerGrid) "List" else "Grid"); Spacer(Modifier.width(16f.udp()))
                    Hint("L1 R1", "Size"); Spacer(Modifier.width(16f.udp()))
                    Hint("B", "Back")
                }
            }
        }
        state.drawerMenu?.let { DrawerSheet(state, it) }
    }
}

@Composable
private fun DrawerHeader(state: SlotState, count: Int) {
    val s = state.settings
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SlotText("Apps", 24f, bold = true)
            SlotText("$count installed", 12.5f, color = SlotColors.Ink.copy(alpha = .55f))
        }
        Chip("Grid", s.drawerGrid) { if (!s.drawerGrid) state.toggleDrawerLayout() }
        Spacer(Modifier.width(6f.udp()))
        Chip("List", !s.drawerGrid) { if (s.drawerGrid) state.toggleDrawerLayout() }
        Spacer(Modifier.width(18f.udp()))
        Chip("A−", false) { state.stepDrawerSize(-1) }
        Spacer(Modifier.width(6f.udp()))
        Chip(s.drawerSize.label, true) { }
        Spacer(Modifier.width(6f.udp()))
        Chip("A+", false) { state.stepDrawerSize(+1) }
        Spacer(Modifier.width(18f.udp()))
        IconButton(IconKind.Close, 34f) { state.closeOverlay() }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(30f.udp()).clip(RoundedCornerShape(15f.udp()))
            .background(if (selected) SlotColors.Ink else SlotColors.Housing)
            .clickable(onClick = onClick).padding(horizontal = 13f.udp()),
        contentAlignment = Alignment.Center,
    ) { SlotText(label, 13f, bold = selected, color = if (selected) SlotColors.CapInk else SlotColors.Ink.copy(alpha = .8f)) }
}

@Composable
private fun AppIcon(state: SlotState, app: AppEntry, size: Float) {
    val bmp = state.appIcons[app.pkg]
    Box(Modifier.size(size.udp()).clip(RoundedCornerShape((size * 0.22f).udp())).background(SlotColors.Housing), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
        } else {
            SlotText(app.label.take(1).uppercase(), size * 0.45f, bold = true, color = SlotColors.Ink.copy(alpha = .6f))
        }
    }
}

@Composable
private fun DrawerGrid(state: SlotState, apps: List<AppEntry>, icon: Float, cols: Int) {
    val grid = rememberLazyGridState()
    val cursor = state.drawerCursor
    LaunchedEffect(cursor, cols, icon) {
        val info = grid.layoutInfo.visibleItemsInfo
        if (info.isNotEmpty() && (cursor < info.first().index || cursor > info.last().index - cols / 2)) {
            grid.scrollToItem((cursor / cols * cols - cols).coerceAtLeast(0))
        }
    }
    LazyVerticalGrid(
        GridCells.Fixed(cols), Modifier.fillMaxSize(), state = grid,
        verticalArrangement = Arrangement.spacedBy(8f.udp()), horizontalArrangement = Arrangement.spacedBy(4f.udp()),
    ) {
        gridItemsIndexed(apps) { i, app ->
            val on = i == cursor
            Column(
                Modifier.clip(RoundedCornerShape(14f.udp()))
                    .background(if (on) SlotColors.Ink.copy(alpha = .16f) else Color.Transparent)
                    .then(if (on) Modifier.border(1.6f.udp(), SlotColors.Ink.copy(alpha = .85f), RoundedCornerShape(14f.udp())) else Modifier)
                    .combinedClickable(onLongClick = { state.openDrawerMenu(i) }) { state.drawerCursor = i; state.launchDrawerApp(i) }
                    .padding(vertical = 8f.udp()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(state, app, icon)
                Spacer(Modifier.height(5f.udp()))
                SlotText(app.label, 12f, align = TextAlign.Center, color = SlotColors.Ink.copy(alpha = if (on) 1f else .8f), modifier = Modifier.width((icon + 30f).udp()))
            }
        }
    }
}

@Composable
private fun DrawerList(state: SlotState, apps: List<AppEntry>, icon: Float) {
    val list = rememberLazyListState()
    val cursor = state.drawerCursor
    LaunchedEffect(cursor, icon) {
        val info = list.layoutInfo.visibleItemsInfo
        if (info.isNotEmpty() && (cursor <= info.first().index || cursor >= info.last().index)) {
            list.scrollToItem((cursor - 2).coerceAtLeast(0))
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = list, verticalArrangement = Arrangement.spacedBy(4f.udp())) {
        itemsIndexed(apps) { i, app ->
            val on = i == cursor
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12f.udp()))
                    .background(if (on) SlotColors.Ink else SlotColors.Housing.copy(alpha = .55f))
                    .combinedClickable(onLongClick = { state.openDrawerMenu(i) }) { state.drawerCursor = i; state.launchDrawerApp(i) }
                    .padding(horizontal = 10f.udp(), vertical = 6f.udp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(state, app, icon * .62f)
                Spacer(Modifier.width(14f.udp()))
                SlotText(app.label, 16.5f, bold = on, color = if (on) SlotColors.CapInk else SlotColors.Ink, modifier = Modifier.weight(1f))
                if (app.likelyGame) SlotText("game", 12f, color = (if (on) SlotColors.CapInk else SlotColors.Ink).copy(alpha = .55f))
            }
        }
    }
}

/** Long-press (or Y) options for one app: open it, see its Android App info, or uninstall it. */
@Composable
private fun DrawerSheet(state: SlotState, index: Int) {
    val app = state.drawerApps.getOrNull(index) ?: return
    val options = state.drawerOptions(app)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).pointerInput(Unit) { detectTapGestures { state.closeDrawerMenu() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(300f.udp()).clip(RoundedCornerShape(14f.udp())).background(SlotColors.Housing)
                .border(1.2f.udp(), SlotColors.Edge, RoundedCornerShape(14f.udp()))
                .pointerInput(Unit) { detectTapGestures { } }.padding(vertical = 10f.udp()),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16f.udp(), vertical = 8f.udp()), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(state, app, 40f)
                Spacer(Modifier.width(12f.udp()))
                Column(Modifier.weight(1f)) {
                    SlotText(app.label, 17f, bold = true)
                    SlotText(app.pkg, 11f, color = SlotColors.Ink.copy(alpha = .5f))
                }
            }
            Spacer(Modifier.height(4f.udp()))
            options.forEachIndexed { i, label ->
                val on = i == state.drawerMenuCursor
                val danger = label == "Uninstall"
                Row(
                    Modifier.fillMaxWidth().height(40f.udp()).padding(horizontal = 8f.udp()).clip(RoundedCornerShape(9f.udp()))
                        .background(if (on) SlotColors.Ink else Color.Transparent)
                        .clickable { state.drawerMenuCursor = i; state.runDrawerOption(i) }.padding(horizontal = 12f.udp()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ink = if (on) SlotColors.CapInk else if (danger) Color(0xFFE5534B) else SlotColors.Ink
                    SlotText(label, 16f, bold = on, color = ink)
                }
            }
            if (state.gamepad) {
                Spacer(Modifier.height(8f.udp()))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Hint("A", "Select"); Spacer(Modifier.width(14f.udp())); Hint("B", "Back")
                }
            }
        }
    }
}
