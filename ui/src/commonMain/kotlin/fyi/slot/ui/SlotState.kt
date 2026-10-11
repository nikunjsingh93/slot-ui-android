package fyi.slot.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Observable state + input handling for the whole slot UI. Platform-agnostic: the Android app
 * feeds it key events and the library, and reacts to [host] callbacks.
 */
class SlotState(var host: SlotHost = object : SlotHost {}) {
    var games by mutableStateOf<List<Game>>(emptyList())
    var scanning by mutableStateOf(false)
    var systemIndex by mutableIntStateOf(0)
    var selected by mutableIntStateOf(0)
    var screen by mutableStateOf<Screen>(Screen.Browse)
    var overlay by mutableStateOf(Overlay.None)
    var settings by mutableStateOf(Settings())
    var hud by mutableStateOf(HudState())
    var gamepad by mutableStateOf(true)
    var toast by mutableStateOf<Toast?>(null)
    var playing by mutableStateOf<Game?>(null)
    val covers = mutableStateMapOf<String, ImageBitmap>()
    val states = mutableStateListOf<StateSlot>()
    /** Which installed apps are shown, and as what. Anything missing is hidden. */
    val appKinds = mutableStateMapOf<String, AppKind>()

    // App drawer (Y on the shelf)
    var drawerApps by mutableStateOf<List<AppEntry>>(emptyList())
    val appIcons = mutableStateMapOf<String, ImageBitmap>()
    var drawerCursor by mutableIntStateOf(0)
    /** Columns of the grid as laid out on screen, so Up/Down can jump a row. */
    var drawerCols by mutableIntStateOf(1)
    /** Index of the app whose options sheet (Open / App info / Uninstall) is showing, or null. */
    var drawerMenu by mutableStateOf<Int?>(null)
    var drawerMenuCursor by mutableIntStateOf(0)

    /** Monotonic counter bumped on every move; lets the shelf play a tick without diffing. */
    var moveTick by mutableIntStateOf(0)

    // --- shelf motion (slot's shelf.rs): a ring of carts on a critically damped spring ---------------
    /** Unbounded ring position the spring chases; `selected == target mod n`. */
    var target by mutableIntStateOf(0)
    /** Current (fractional) position of the spring. */
    var scroll by mutableFloatStateOf(0f)
    private var velocity = 0f
    /** True while a finger is dragging the shelf (the spring is paused). */
    var dragging by mutableStateOf(false)
    /** Free-running clock (ms) advanced by [stepShelf]; hold-to-repeat is timed against it. */
    private var clockMs = 0f
    var heldDir by mutableIntStateOf(0)
    private var heldDue = 0f
    private var heldFired = 0

    /** Advances the shelf spring by [dt] seconds. Returns true while anything is still moving or repeating. */
    fun stepShelf(dt: Float): Boolean {
        clockMs += dt * 1000f
        if (heldDir != 0 && screen == Screen.Browse && overlay == Overlay.None) {
            if (clockMs >= heldDue) {
                ringStep(heldDir)
                heldDue = clockMs + HOLD_RATES[minOf(heldFired, HOLD_RATES.lastIndex)]
                heldFired++
            }
        } else heldDir = 0
        if (dragging) return true
        // `accel = -2*w*v - w^2*(x - target)` integrated with sub-steps so it stays stable at low frame rates
        var left = dt
        while (left > 0f) {
            val h = minOf(left, 1f / 240f)
            val accel = -2f * SHELF_OMEGA * velocity - SHELF_OMEGA * SHELF_OMEGA * (scroll - target)
            velocity += accel * h
            scroll += velocity * h
            left -= h
        }
        val settled = kotlin.math.abs(scroll - target) < 0.0005f && kotlin.math.abs(velocity) < 0.002f
        if (settled) { scroll = target.toFloat(); velocity = 0f }
        return !settled || heldDir != 0
    }

    private fun snapShelf() {
        target = selected
        scroll = selected.toFloat()
        velocity = 0f
        heldDir = 0
    }

    private fun ringIndex(t: Int, n: Int) = ((t % n) + n) % n

    /** Move the selection by [by] places around the ring (clamped when there are fewer than 3 carts). */
    private fun ringStep(by: Int) {
        val n = shelf.size
        if (n < 2) return
        if (n < 3 && selected + by !in 0 until n) return
        target += by
        selected = ringIndex(target, n)
        moveTick++
        host.sfx(Sfx.Tick)
    }

    /** Called when a drag ends: settle on the nearest cart. */
    fun settleAt(position: Float) {
        val n = shelf.size
        if (n == 0) return
        scroll = position
        val t = kotlin.math.round(position).toInt()
        target = t
        selected = ringIndex(t, n)
        dragging = false
        moveTick++
    }

    /** A is physically down on the shelf; [aHold] counts frames-worth of ms while it is (tap = resume, hold = fresh). */
    var aDown by mutableStateOf(false)
    var aHold by mutableStateOf(0f)

    // Menu cursors
    var quickPage by mutableIntStateOf(0)
    var quickRow by mutableIntStateOf(0)
    var stateCursor by mutableIntStateOf(0)
    var settingsRow by mutableIntStateOf(0)

    private val lastIndex = HashMap<GameSystem, Int>()
    private var lastMoveAt = 0L

    val systems: List<GameSystem> by derivedStateOf {
        games.map { it.system }.distinct().sortedBy { it.ordinal }
    }
    val system: GameSystem? get() = systems.getOrNull(systemIndex)

    val shelf: List<Game> by derivedStateOf {
        val s = systems.getOrNull(systemIndex)
        games.filter { it.system == s }.sortedWith(compareBy({ it.title.lowercase() }, { it.stem }))
    }
    val current: Game? get() = shelf.getOrNull(selected)

    /** Games without a core of their own use the system default from Settings > Emulators. */
    fun withDefaultCore(g: Game): Game =
        if (g.corePinned) g else g.copy(coreId = settings.defaultCores[g.system.id]?.takeIf { id -> g.system.cores.any { it.id == id } })

    fun setLibrary(list: List<Game>) {
        val keep = current?.id
        games = list.map(::withDefaultCore)
        if (systemIndex >= systems.size) systemIndex = 0
        val i = shelf.indexOfFirst { it.id == keep }
        selected = if (i >= 0) i else selected.coerceIn(0, (shelf.size - 1).coerceAtLeast(0))
        snapShelf()
    }

    fun toast(text: String, now: Long = 0L) {
        toast = Toast(text, now)
    }

    // --- navigation ------------------------------------------------------------------------

    /** Jump to cart [i] by the shortest way round the ring (used by taps). */
    fun select(i: Int) {
        val n = shelf.size
        if (i !in 0 until n || i == selected) return
        var d = ringIndex(i - selected, n)
        if (d > n / 2 && n >= 3) d -= n
        if (n < 3) d = i - selected
        ringStep(d)
    }

    fun skipLetter(dir: Int) {
        val list = shelf
        val n = list.size
        if (n < 2) return
        fun key(g: Game) = g.title.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
        val here = key(list[selected])
        if (list.all { key(it) == here }) return
        val to: Int
        if (dir > 0) {
            var at = selected
            at = selected
            for (k in 0 until n) { at = ringIndex(at + 1, n); if (key(list[at]) != here) break }
            to = at
        } else {
            fun startOf(from: Int): Int {
                val letter = key(list[from]); var at = from
                for (k in 0 until n) { val before = ringIndex(at - 1, n); if (key(list[before]) != letter) break; at = before }
                return at
            }
            val start = startOf(selected)
            to = if (start == selected) startOf(ringIndex(start - 1, n)) else start
        }
        val ahead = ringIndex(to - selected, n)
        ringStep(if (dir > 0) ahead else ahead - n)
    }

    fun switchSystem(dir: Int) {
        val n = systems.size
        if (n < 2) return
        system?.let { lastIndex[it] = selected }
        systemIndex = ((systemIndex + dir) % n + n) % n
        selected = (lastIndex[systems[systemIndex]] ?: 0).coerceIn(0, (shelf.size - 1).coerceAtLeast(0))
        snapShelf()
        moveTick++
        host.sfx(Sfx.Select)
    }

    fun selectSystem(index: Int) {
        if (index == systemIndex || index !in systems.indices) return
        switchSystem(index - systemIndex)
    }

    fun openCorePicker(game: Game? = current) {
        val g = game ?: return
        val chip = g.system.cores.indexOfFirst { it.id == g.core.id }.coerceAtLeast(0)
        screen = Screen.CorePicker(g, chip)
        host.sfx(Sfx.Select)
    }

    fun closeCorePicker() {
        if (screen is Screen.CorePicker) {
            screen = Screen.Browse
            host.sfx(Sfx.Back)
        }
    }

    fun chooseChip(i: Int) {
        val s = screen as? Screen.CorePicker ?: return
        val c = i.coerceIn(0, s.game.system.cores.lastIndex)
        if (c != s.chip) {
            screen = s.copy(chip = c)
            host.sfx(Sfx.Tick)
        }
    }

    /** Called by the scene when the user commits to playing [game]. */
    fun insert(game: Game, resume: Boolean) {
        val s = screen
        val withCore = if (s is Screen.CorePicker) {
            val core = game.system.cores[s.chip.coerceIn(0, game.system.cores.lastIndex)]
            host.onChooseCore(game, core.id)
            // Reflect the choice in the library immediately.
            games = games.map { if (it.id == game.id) it.copy(coreId = core.id, corePinned = true) else it }
            game.copy(coreId = core.id, corePinned = true)
        } else game
        heldDir = 0
        screen = Screen.Inserting(withCore, resume)
        // the clunk itself is played by the scene at the moment the cart catches
    }

    /** The insert animation finished; hand over to the emulator. */
    fun finishInsert() {
        val s = screen as? Screen.Inserting ?: return
        playing = s.game
        screen = Screen.Playing
        overlay = Overlay.None
        host.onLaunch(s.game, s.resume)
    }

    /** The game was ejected (or the emulator exited). */
    fun ejected() {
        screen = Screen.Ejecting
        overlay = Overlay.None
        playing = null
    }

    /** The eject animation finished. */
    fun finishEject() {
        if (screen == Screen.Ejecting) screen = Screen.Browse
    }

    // --- input ---------------------------------------------------------------------------------

    fun press(pad: Pad, repeat: Boolean = false, now: Long = 0L) {
        if (repeat && now - lastMoveAt < 70) return
        lastMoveAt = now
        if (pad == Pad.Menu && !repeat && overlay == Overlay.Quick && now - lastMenuTap in 1..MENU_DOUBLE_TAP_MS) {
            lastMenuTap = 0
            menuStack.clear(); menuCursor.clear()
            openStates()
            return
        }
        if (overlay != Overlay.None) { overlayPress(pad, repeat); return }
        if (screen == Screen.Playing) {
            if (pad == Pad.Menu && !repeat) {
                if (now - lastMenuTap in 1..MENU_DOUBLE_TAP_MS) openStates() else openQuickMenu()
                lastMenuTap = now
            }
            return
        }
        when (val s = screen) {
            Screen.Browse -> when (pad) {
                // Android's own key auto-repeat is ignored: slot times its own (400 ms, then 110/85/65/50 ms).
                Pad.Left, Pad.Right, Pad.R1, Pad.R2 -> if (!repeat) {
                    val d = if (pad == Pad.Left || pad == Pad.R1) -1 else 1
                    ringStep(d)
                    heldDir = d; heldDue = clockMs + HOLD_DELAY_MS; heldFired = 0
                }
                Pad.Up -> if (!repeat) skipLetter(-1)
                Pad.Down -> if (!repeat) skipLetter(+1)
                Pad.L1 -> if (!repeat) switchSystem(-1)
                Pad.L2 -> if (!repeat) switchSystem(+1)
                Pad.Start -> openCorePicker()
                Pad.Y -> openDrawer()
                Pad.Menu, Pad.Select -> openSettings()
                Pad.A -> if (!repeat) {
                    if (current != null) { aDown = true; aHold = 0f } else if (games.isEmpty()) host.onPickLibrary()
                }
                else -> {}
            }
            is Screen.CorePicker -> when (pad) {
                Pad.Left -> chooseChip(s.chip - 1)
                Pad.Right -> chooseChip(s.chip + 1)
                Pad.B, Pad.Menu -> closeCorePicker()
                Pad.A, Pad.Start -> if (!repeat) insert(s.game, resume = true)
                Pad.X -> if (!repeat) insert(s.game, resume = false)
                else -> {}
            }
            else -> {}
        }
    }

    fun release(pad: Pad, now: Long = 0L) {
        if (((pad == Pad.Left || pad == Pad.R1) && heldDir < 0) || ((pad == Pad.Right || pad == Pad.R2) && heldDir > 0)) heldDir = 0
        if (pad == Pad.A && aDown) {
            aDown = false
            aHold = 0f
            if (screen == Screen.Browse && overlay == Overlay.None) current?.let { insert(it, resume = true) }
        }
    }

    /** Drops every "held" input (focus lost, a controller vanished): nothing may keep repeating on its own. */
    fun releaseAll() {
        heldDir = 0
        aDown = false
        aHold = 0f
    }

    /** The shelf's frame loop calls this when A has been held long enough. */
    fun holdCompleted() {
        if (aDown && screen == Screen.Browse) {
            aDown = false
            aHold = 0f
            current?.let { insert(it, resume = false) }
        }
    }

    // --- menus -----------------------------------------------------------------------------------

    val menuStack = mutableStateListOf<MenuPage>()
    val menuCursor = mutableStateListOf<Int>()
    private var lastMenuTap = 0L
    var fastForward by mutableStateOf(false)
    var statesBusy by mutableStateOf(false)

    fun openQuickMenu() {
        menuStack.clear(); menuCursor.clear()
        pushMenu(buildQuickMenu())
        overlay = Overlay.Quick
        host.onAction(GameAction.RefreshStates)
        host.sfx(Sfx.Select)
    }

    fun openSettings() {
        menuStack.clear(); menuCursor.clear()
        pushMenu(buildSettingsMenu())
        overlay = Overlay.Settings
        host.sfx(Sfx.Select)
    }

    fun openDrawer() {
        drawerApps = host.listApps()
        drawerCursor = 0
        drawerMenu = null
        overlay = Overlay.Drawer
        host.onDrawerOpen()
        host.sfx(Sfx.Select)
    }

    fun launchDrawerApp(index: Int = drawerCursor) {
        val app = drawerApps.getOrNull(index) ?: return
        overlay = Overlay.None
        host.onLaunchApp(app.pkg)
    }

    private fun updateDrawerSettings(f: (Settings) -> Settings) {
        settings = f(settings)
        host.onSettingsChanged(settings)
    }

    fun toggleDrawerLayout() = updateDrawerSettings { it.copy(drawerGrid = !it.drawerGrid) }

    fun stepDrawerSize(dir: Int) = updateDrawerSettings {
        val n = DrawerSize.entries.size
        it.copy(drawerSize = DrawerSize.entries[(it.drawerSize.ordinal + dir).coerceIn(0, n - 1)])
    }

    /** What the options sheet offers for [app]: system apps can't be uninstalled. */
    fun drawerOptions(app: AppEntry): List<String> = listOfNotNull("Open", "App info", "Uninstall".takeIf { app.canUninstall })

    fun openDrawerMenu(index: Int = drawerCursor) {
        if (drawerApps.getOrNull(index) == null) return
        drawerCursor = index
        drawerMenu = index
        drawerMenuCursor = 0
        host.sfx(Sfx.Select)
    }

    fun closeDrawerMenu() {
        if (drawerMenu != null) { drawerMenu = null; host.sfx(Sfx.Back) }
    }

    fun runDrawerOption(i: Int) {
        val index = drawerMenu ?: return
        val app = drawerApps.getOrNull(index) ?: return
        when (drawerOptions(app).getOrNull(i)) {
            "Open" -> { drawerMenu = null; launchDrawerApp(index) }
            "App info" -> { drawerMenu = null; host.onAppInfo(app.pkg) }
            "Uninstall" -> { drawerMenu = null; host.onUninstallApp(app.pkg) }
        }
    }

    /** The apps changed behind our back (one was uninstalled): refresh the list and keep the cursor in range. */
    fun refreshDrawer() {
        if (overlay != Overlay.Drawer) return
        drawerApps = host.listApps()
        drawerCursor = drawerCursor.coerceIn(0, (drawerApps.size - 1).coerceAtLeast(0))
    }

    private fun drawerMenuPress(pad: Pad, repeat: Boolean) {
        val app = drawerApps.getOrNull(drawerMenu ?: return) ?: run { drawerMenu = null; return }
        val n = drawerOptions(app).size
        when (pad) {
            Pad.Up -> { drawerMenuCursor = (drawerMenuCursor - 1 + n) % n; host.sfx(Sfx.Tick) }
            Pad.Down -> { drawerMenuCursor = (drawerMenuCursor + 1) % n; host.sfx(Sfx.Tick) }
            Pad.A -> if (!repeat) runDrawerOption(drawerMenuCursor)
            Pad.B, Pad.Y, Pad.Menu, Pad.Start, Pad.Select -> if (!repeat) closeDrawerMenu()
            else -> {}
        }
    }

    private fun drawerPress(pad: Pad, repeat: Boolean) {
        if (drawerMenu != null) { drawerMenuPress(pad, repeat); return }
        val n = drawerApps.size
        val step = if (settings.drawerGrid) drawerCols.coerceAtLeast(1) else 1
        fun go(by: Int) { if (n > 0) { drawerCursor = (drawerCursor + by).coerceIn(0, n - 1); host.sfx(Sfx.Tick) } }
        when (pad) {
            Pad.Left -> if (settings.drawerGrid) go(-1) else stepDrawerSize(-1)
            Pad.Right -> if (settings.drawerGrid) go(+1) else stepDrawerSize(+1)
            Pad.Up -> go(-step)
            Pad.Down -> go(+step)
            Pad.A -> if (!repeat) launchDrawerApp()
            Pad.X -> if (!repeat) toggleDrawerLayout()
            Pad.L1, Pad.L2 -> if (!repeat) stepDrawerSize(-1)
            Pad.R1, Pad.R2 -> if (!repeat) stepDrawerSize(+1)
            Pad.Y -> if (!repeat) openDrawerMenu()
            Pad.B, Pad.Menu, Pad.Start, Pad.Select -> if (!repeat) closeOverlay()
        }
    }

    fun openStates() {
        overlay = Overlay.States
        stateCursor = 0
        host.onAction(GameAction.RefreshStates)
        host.sfx(Sfx.Select)
    }

    fun pushMenu(page: MenuPage) {
        menuStack.add(page)
        menuCursor.add(page.items.indexOfFirst { it.selectable }.coerceAtLeast(0))
    }

    fun popMenu() {
        if (menuStack.size > 1) {
            menuStack.removeAt(menuStack.lastIndex); menuCursor.removeAt(menuCursor.lastIndex)
            host.sfx(Sfx.Back)
        } else closeOverlay()
    }

    fun closeOverlay() {
        val wasQuick = overlay == Overlay.Quick || overlay == Overlay.States
        drawerMenu = null
        overlay = Overlay.None
        menuStack.clear(); menuCursor.clear()
        if (wasQuick && screen == Screen.Playing) host.onAction(GameAction.Resume)
        host.sfx(Sfx.Back)
    }

    private fun moveCursor(dir: Int) {
        val page = menuStack.lastOrNull() ?: return
        val n = page.items.size
        var i = menuCursor.last()
        repeat(n) {
            i = (i + dir + n) % n
            if (page.items[i].selectable) { menuCursor[menuCursor.lastIndex] = i; host.sfx(Sfx.Tick); return }
        }
    }

    private fun activate(adjust: Int) {
        val page = menuStack.lastOrNull() ?: return
        when (val it = page.items.getOrNull(menuCursor.last())) {
            is MenuAction -> if (adjust == 0) it.run()
            is MenuToggle -> { it.set(!it.get()); host.sfx(Sfx.Tick) }
            is MenuChoice -> {
                val n = it.options.size
                val step = if (adjust == 0) 1 else adjust
                it.set(((it.get() + step) % n + n) % n)
                host.sfx(Sfx.Tick)
            }
            is MenuSub -> if (adjust >= 0) { pushMenu(it.build()); host.sfx(Sfx.Select) }
            else -> {}
        }
    }

    fun tapMenuRow(i: Int) {
        val page = menuStack.lastOrNull() ?: return
        if (i in page.items.indices && page.items[i].selectable) {
            menuCursor[menuCursor.lastIndex] = i
            activate(0)
        }
    }

    fun pressA() = press(Pad.A)
    fun pressX() = press(Pad.X)
    fun pressY() = press(Pad.Y)

    private fun overlayPress(pad: Pad, repeat: Boolean) {
        when (overlay) {
            Overlay.Quick, Overlay.Settings -> when (pad) {
                Pad.Up -> moveCursor(-1)
                Pad.Down -> moveCursor(+1)
                Pad.Left -> activate(-1)
                Pad.Right -> activate(+1)
                Pad.A -> if (!repeat) activate(0)
                Pad.B -> if (!repeat) popMenu()
                Pad.Menu -> if (!repeat) closeOverlay()
                else -> {}
            }
            Overlay.States -> statesPress(pad, repeat)
            Overlay.Drawer -> drawerPress(pad, repeat)
            else -> {}
        }
    }

    private fun statesPress(pad: Pad, repeat: Boolean) {
        val n = states.size
        when (pad) {
            Pad.Left -> if (n > 0) { stateCursor = (stateCursor - 1 + n) % n; host.sfx(Sfx.Tick) }
            Pad.Right -> if (n > 0) { stateCursor = (stateCursor + 1) % n; host.sfx(Sfx.Tick) }
            Pad.A -> if (!repeat) states.getOrNull(stateCursor)?.let { s ->
                if (s.thumb != null || s.time.isNotEmpty()) { host.onAction(GameAction.LoadState(s.slot)); closeOverlay() }
                else { host.onAction(GameAction.SaveState(s.slot)); closeOverlay() }
            }
            Pad.Y -> if (!repeat) states.getOrNull(stateCursor)?.let { host.onAction(GameAction.SaveState(it.slot)); host.onAction(GameAction.RefreshStates) }
            Pad.X -> if (!repeat) states.getOrNull(stateCursor)?.let { host.onAction(GameAction.DeleteState(it.slot)) }
            Pad.Select -> if (!repeat) host.onAction(GameAction.UndoLoad)
            Pad.B, Pad.Menu -> if (!repeat) { overlay = Overlay.None; host.onAction(GameAction.Resume) }
            else -> {}
        }
    }

    companion object {
        const val HOLD_FRESH_MS = 650f
        /** slot: first repeat after 400 ms, then 110, 85, 65, 50 ms. */
        const val HOLD_DELAY_MS = 400f
        val HOLD_RATES = floatArrayOf(110f, 85f, 65f, 50f)
        const val MENU_DOUBLE_TAP_MS = 380L
    }
}

data class Toast(val text: String, val at: Long)
