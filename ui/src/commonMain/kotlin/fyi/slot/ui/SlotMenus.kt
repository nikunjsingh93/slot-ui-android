package fyi.slot.ui

val GB_PALETTES = listOf("Core default", "DMG Green", "Pocket", "Light", "Grayscale")
private val FF_SPEEDS = listOf(2, 3, 4, 6, 8)
private val PERCENTS = listOf(0, 25, 50, 75, 100)

private fun SlotState.update(f: (Settings) -> Settings) {
    settings = f(settings)
    host.onSettingsChanged(settings)
}

private val onOff = listOf("Off", "On")

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


private fun SlotState.controlsPage(): MenuPage = MenuPage(
    "Controls",
    listOf(
        MenuChoice("On-screen controls", TouchMode.entries.map { it.label }, { settings.showTouchControls.ordinal }) { i ->
            update { it.copy(showTouchControls = TouchMode.entries[i]) }
        },
        MenuToggle("Swap A/B and X/Y", { settings.swapFaceButtons }) { v -> update { it.copy(swapFaceButtons = v) } },
        MenuToggle("Interface sounds", { settings.sounds }) { v -> update { it.copy(sounds = v) } },
    ),
)

fun SlotState.buildQuickMenu(): MenuPage {
    val g = playing
    return MenuPage(
        g?.title ?: "Paused",
        listOf(
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
            MenuAction("Eject", if (settings.autoSaveOnEject) "Saves automatically" else null) {
                host.onAction(GameAction.Eject(settings.autoSaveOnEject))
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
                MenuInfo("Games found", games.size.toString()),
            ))
        },
        MenuSub("Screen") { screenPage(null) },
        MenuSub("Gameplay") { gameplayPage() },
        MenuSub("Controls") { controlsPage() },
        MenuSub("Apps & games") { appsPage() },
        MenuSub("Button guide") {
            MenuPage("Button guide", listOf(
                MenuInfo("Left / Right", "Browse (hold to speed up)"),
                MenuInfo("Up / Down", "Jump by letter"),
                MenuInfo("L1 / R1", "Switch system"),
                MenuInfo("A", "Play (hold for a new game)"),
                MenuInfo("START", "Pick the core"),
                MenuInfo("MENU", "Settings / pause menu"),
                MenuInfo("MENU twice", "Save states"),
                MenuInfo("L2 / R2 in game", "Rewind / fast-forward"),
            ))
        },
        MenuSub("About") {
            MenuPage("About", listOf(
                MenuInfo("slot. for Android", "0.1.0"),
                MenuInfo("Original frontend", "Brandon Kowalski"),
                MenuInfo("Licence", "GPL-3.0"),
                MenuInfo("Emulation", "libretro cores"),
            ))
        },
    ),
)

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
