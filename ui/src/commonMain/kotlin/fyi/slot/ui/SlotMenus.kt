package fyi.slot.ui

val GB_PALETTES = listOf("Core default", "DMG Green", "Pocket", "Light", "Grayscale")
private val FF_SPEEDS = listOf(2, 3, 4, 6, 8)
private val PERCENTS = listOf(0, 25, 50, 75, 100)

private fun SlotState.update(f: (Settings) -> Settings) {
    settings = f(settings)
    host.onSettingsChanged(settings)
}

private val onOff = listOf("Off", "On")
private val PORTRAIT_POSITIONS = listOf("Top", "Down 1", "Down 2", "Down 3 (default)", "Down 4", "Down 5")

private fun SlotState.screenPage(forSystem: GameSystem?): MenuPage {
    val items = mutableListOf<MenuItem>()
    fun shaderChoice(label: String, get: () -> ShaderChoice, set: (ShaderChoice) -> Unit) =
        MenuChoice(label, ShaderChoice.entries.map { it.label }, { get().ordinal }, { set(ShaderChoice.entries[it]) })
    when (forSystem?.shape) {
        CartShape.GBA -> items += shaderChoice("Shader", { settings.gbaShader }) { s -> update { it.copy(gbaShader = s) } }
        CartShape.GB, CartShape.GBC -> items += shaderChoice("Shader", { settings.gbShader }) { s -> update { it.copy(gbShader = s) } }
        null -> {
            items += shaderChoice("GBA shader", { settings.gbaShader }) { s -> update { it.copy(gbaShader = s) } }
            items += shaderChoice("GB / GBC shader", { settings.gbShader }) { s -> update { it.copy(gbShader = s) } }
            items += shaderChoice("Other systems", { settings.otherShader }) { s -> update { it.copy(otherShader = s) } }
        }
        else -> items += shaderChoice("Shader", { settings.otherShader }) { s -> update { it.copy(otherShader = s) } }
    }
    items += MenuToggle("Colour correction", { settings.colorCorrection }) { v -> update { it.copy(colorCorrection = v) } }
    items += MenuChoice("Screen position (portrait)", PORTRAIT_POSITIONS, { settings.portraitScreenStep }) { i -> update { it.copy(portraitScreenStep = i) } }
    if (forSystem == null || forSystem.shape == CartShape.GB || forSystem.shape == CartShape.GBC) {
        items += MenuChoice(
            "Game Boy palette", GB_PALETTES, { GB_PALETTES.indexOf(settings.gbPalette).coerceAtLeast(0) },
        ) { i -> update { it.copy(gbPalette = GB_PALETTES[i]) } }
    }
    items += MenuChoice("Brightness", PERCENTS.drop(1).map { "$it%" }, { PERCENTS.drop(1).indexOfFirst { p -> p / 100f >= settings.brightness - .01f }.coerceAtLeast(0) }) { i ->
        update { it.copy(brightness = PERCENTS.drop(1)[i] / 100f) }
    }
    return MenuPage("Screen", items)
}

private fun SlotState.gameplayPage(): MenuPage = MenuPage(
    "Gameplay",
    listOf(
        MenuChoice("Fast-forward speed", FF_SPEEDS.map { "${it}x" }, { FF_SPEEDS.indexOf(settings.fastForwardSpeed).coerceAtLeast(0) }) { i ->
            update { it.copy(fastForwardSpeed = FF_SPEEDS[i]) }
        },
        MenuToggle("Mute while fast-forwarding", { settings.muteFastForward }) { v -> update { it.copy(muteFastForward = v) } },
        MenuToggle("Rewind (hold L2)", { settings.rewind }) { v -> update { it.copy(rewind = v) } },
        MenuToggle("Turbo buttons", { settings.turbo }) { v -> update { it.copy(turbo = v) } },
        MenuToggle("Rumble", { settings.rumble }) { v -> update { it.copy(rumble = v) } },
        MenuToggle("Auto-save on eject", { settings.autoSaveOnEject }) { v -> update { it.copy(autoSaveOnEject = v) } },
    ),
)



/** Settings > Saves: move game saves to another device as a zip, for everything or for the selected game. */
private fun SlotState.savesPage(): MenuPage {
    val g = current?.takeIf { !it.external }
    val rows = mutableListOf<MenuItem>(
        MenuAction("Export all saves", "Zip of every game") { host.onExportSaves(null); closeOverlay() },
        MenuAction("Import saves", "From a zip") { host.onImportSaves(null); closeOverlay() },
    )
    if (g != null) {
        rows += MenuAction("Download save") { host.onExportSaves(g); closeOverlay() }
        rows += MenuAction("Upload save") { host.onImportSaves(g); closeOverlay() }
    }
    return MenuPage("Saves", rows, if (g == null) "Select a game on the shelf for per-game options" else "Download / upload: ${g.title}")
}

/** Settings > Controls > Edit button layout: pick a system, then drag and pinch its buttons into place. */
private fun SlotState.touchPage(): MenuPage = MenuPage(
    "Edit button layout",
    GameSystem.entries.filter { s -> s.cores.any { !it.external } }.map { sys -> MenuAction(sys.title) { openLayoutEditor(sys) } },
    subtitle = "Choose a system",
)

private fun SlotState.controlsPage(): MenuPage = MenuPage(
    "Controls",
    listOf(
        MenuChoice("On-screen controls", TouchMode.entries.map { it.label }, { settings.showTouchControls.ordinal }) { i ->
            update { it.copy(showTouchControls = TouchMode.entries[i]) }
        },
        MenuToggle("Swap A/B and X/Y", { settings.swapFaceButtons }) { v -> update { it.copy(swapFaceButtons = v) } },
        MenuSub("Edit button layout") { touchPage() },
        MenuToggle("Interface sounds", { settings.sounds }) { v -> update { it.copy(sounds = v) } },
    ),
)

fun SlotState.buildQuickMenu(): MenuPage {
    val g = playing
    return MenuPage(
        g?.title ?: "Paused",
        listOf(
            // Eject first: it is what you reach for most when you are done, and Back then A quits in two presses
            MenuAction("Eject", if (settings.autoSaveOnEject) "Saves automatically" else null) {
                host.onAction(GameAction.Eject(settings.autoSaveOnEject))
            },
            MenuAction("Resume") { closeOverlay() },
            MenuAction("Save states", "Browse, load, save") { openStates() },
            MenuAction("Quick save") { host.onAction(GameAction.SaveState(1)); toast("State saved to slot 1"); closeOverlay() },
            MenuAction("Quick load") { host.onAction(GameAction.LoadState(1)); closeOverlay() },
            MenuToggle("Fast forward", { fastForward }) { v -> fastForward = v; host.onAction(GameAction.FastForward(v)) },
            MenuSub("Screen") { screenPage(g?.system) },
            MenuSub("Gameplay") { gameplayPage() },
            MenuSub("Controls") { controlsPage() },
            MenuSub("Reset game") {
                MenuPage("Reset game?", listOf(
                    MenuAction("Yes, reset") { host.onAction(GameAction.Reset); closeOverlay() },
                    MenuAction("No") { popMenu() },
                ), subtitle = "Unsaved progress is lost.")
            },
        ),
    )
}

fun SlotState.buildSettingsMenu(): MenuPage = MenuPage(
    "Settings",
    listOf(
        MenuSub("Library") {
            MenuPage("Library", listOf(
                MenuAction("Choose ROM folder") { host.onPickLibrary() },
                MenuAction("Rescan library") { host.onRescan(); closeOverlay() },
                MenuToggle("Download cart art", { settings.downloadCovers }) { v -> update { it.copy(downloadCovers = v) } },
                MenuAction("Art server", settings.artServer.ifEmpty { "Off" }) { closeOverlay(); host.onEditArtServer() },
                MenuInfo("Games found", games.size.toString()),
            ))
        },
        MenuSub("Screen") { screenPage(null) },
        MenuSub("Gameplay") { gameplayPage() },
        MenuSub("Emulators") { emulatorsPage() },
        MenuSub("Controls") { controlsPage() },
        MenuSub("Interface") { interfacePage() },
        MenuSub("Saves") { savesPage() },
        MenuSub("Apps & games") { appsPage() },
        MenuSub("Button guide") {
            MenuPage("Button guide", listOf(
                MenuInfo("Left / Right", "Browse (hold to speed up)"),
                MenuInfo("Up / Down", "Jump by letter"),
                MenuInfo("L1 / R1", "Previous / next system"),
                MenuInfo("L2 / R2", "Previous / next game"),
                MenuInfo("A", "Play (hold for a new game)"),
                MenuInfo("START", "Pick the core"),
                MenuInfo("Y", "App drawer"),
                MenuInfo("MENU", "Settings / pause menu"),
                MenuInfo("MENU twice", "Save states"),
                MenuInfo("L2 / R2 in game", "Rewind / fast-forward"),
            ))
        },
        MenuSub("About") {
            MenuPage("About", listOf(
                MenuInfo("slot. for Android", host.versionName),
                MenuInfo("Original frontend", "Brandon Kowalski"),
                MenuInfo("Licence", "GPL-3.0"),
                MenuInfo("Emulation", "libretro cores"),
            ))
        },
    ),
)

/** Settings > Emulators: for every system with more than one emulator or core, which one is used by default. */
private fun SlotState.emulatorsPage(): MenuPage {
    val rows = GameSystem.entries.filter { it.cores.size > 1 }.map { sys ->
        MenuChoice(
            sys.title, sys.cores.map { it.name },
            { sys.cores.indexOfFirst { it.id == settings.defaultCores[sys.id] }.coerceAtLeast(0) },
        ) { i ->
            update { it.copy(defaultCores = it.defaultCores + (sys.id to sys.cores[i].id)) }
            games = games.map(::withDefaultCore)
        }
    }
    return MenuPage("Emulators", rows, "Used unless you pick one for a game (START)")
}

/** Settings > Apps & games: every launchable app, each one Hidden (default), an App or a Game. */
private fun SlotState.appsPage(): MenuPage {
    val apps = host.listApps()
    val rows = apps.map { a ->
        val label = if (a.likelyGame) "${a.label}  (game)" else a.label
        MenuChoice(label, AppKind.entries.map { it.label }, { (appKinds[a.pkg] ?: AppKind.Hidden).ordinal }) { i ->
            val kind = AppKind.entries[i]
            if (kind == AppKind.Hidden) appKinds.remove(a.pkg) else appKinds[a.pkg] = kind
            host.onAppKind(a.pkg, kind)
        }
    }
    val items: List<MenuItem> = if (rows.isEmpty()) listOf(MenuInfo("No apps found")) else rows
    return MenuPage("Apps & games", items, subtitle = "Choose where each one shows")
}

private fun SlotState.interfacePage(): MenuPage = MenuPage(
    "Interface",
    listOf(
        MenuChoice("Cart size", CartSize.entries.map { it.label }, { settings.cartSize.ordinal }) { i ->
            update { it.copy(cartSize = CartSize.entries[i]) }
        },
        MenuChoice("Interface scale", UiScale.entries.map { it.label }, { settings.uiScale.ordinal }) { i ->
            update { it.copy(uiScale = UiScale.entries[i]) }
        },
        MenuChoice("List text size", ListSize.entries.map { it.label }, { settings.listSize.ordinal }) { i ->
            update { it.copy(listSize = ListSize.entries[i]) }
        },
        MenuChoice("Shelf view", listOf("Carts", "List + cart", "Grid"), { if (settings.shelfList) 1 else if (settings.shelfGrid) 2 else 0 }) { i ->
            update { it.copy(shelfList = i == 1, shelfGrid = i == 2) }
        },
        MenuChoice("App drawer layout", listOf("Grid", "List"), { if (settings.drawerGrid) 0 else 1 }) { i ->
            update { it.copy(drawerGrid = i == 0) }
        },
        MenuChoice("App drawer size", DrawerSize.entries.map { it.label }, { settings.drawerSize.ordinal }) { i ->
            update { it.copy(drawerSize = DrawerSize.entries[i]) }
        },
    ),
)
