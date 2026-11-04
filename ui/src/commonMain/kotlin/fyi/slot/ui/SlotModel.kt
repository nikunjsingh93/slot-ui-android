package fyi.slot.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/** Controller buttons, in slot's vocabulary. The platform layer translates key events to these. */
enum class Pad { Up, Down, Left, Right, A, B, X, Y, L1, R1, L2, R2, Start, Select, Menu }

sealed interface Screen {
    data object Browse : Screen
    data class CorePicker(val game: Game, val chip: Int) : Screen
    /** Cart sliding into the slot; [resume] false means a fresh start (A was held). */
    data class Inserting(val game: Game, val resume: Boolean) : Screen
    data object Playing : Screen
    /** The game process exited; the cart is popping back out of the slot. */
    data object Ejecting : Screen
}

enum class Overlay { None, Quick, States, Settings, Library, Eject, Drawer, EditLayout }

data class HudState(val batteryPercent: Int = 100, val charging: Boolean = false, val clock: String = "12:00")

enum class ShaderChoice(val label: String) {
    Default("Sharp pixels"), Lcd("LCD grid"), Crt("CRT scanlines"), Smooth("Smooth (CUT)"),
}

data class Settings(
    val gbaShader: ShaderChoice = ShaderChoice.Lcd,
    val gbShader: ShaderChoice = ShaderChoice.Lcd,
    val otherShader: ShaderChoice = ShaderChoice.Default,
    val colorCorrection: Boolean = true,
    val gbPalette: String = "Core default",
    val fastForwardSpeed: Int = 3,
    val muteFastForward: Boolean = true,
    val rewind: Boolean = true,
    val turbo: Boolean = true,
    val rumble: Boolean = true,
    val autoSaveOnEject: Boolean = true,
    val showTouchControls: TouchMode = TouchMode.Auto,
    val sounds: Boolean = true,
    val swapFaceButtons: Boolean = false,
    val downloadCovers: Boolean = true,
    val brightness: Float = 1f,
    val cartSize: CartSize = CartSize.Default,
    /** Shelf view: false = carts only (slot), true = a list of games beside the cart. */
    val shelfList: Boolean = false,
    val drawerGrid: Boolean = true,
    val drawerSize: DrawerSize = DrawerSize.Medium,
    /** Settings > Emulators: system id -> the core or emulator used unless a game has its own choice. */
    val defaultCores: Map<String, String> = emptyMap(),
    /** Portrait play with on-screen controls: how far the game picture sits below the top edge, 0..5 steps of 3% of the screen height. */
    val portraitScreenStep: Int = 3,
    /** Per system id: on-screen button layout as six numbers: d-pad scale, x, y, buttons scale, x, y (offsets are shares of the screen). */
    val touchTweaks: Map<String, List<Float>> = emptyMap(),
)

enum class TouchMode(val label: String) { Auto("When no controller"), Always("Always"), Never("Never") }

/** How big the carts and discs are on the shelf (Settings > Interface). */
enum class CartSize(val label: String, val factor: Float) {
    XSmall("Extra small", 0.62f), Small("Small", 0.8f), Default("Default", 1f), Large("Large", 1.15f), XLarge("Extra large", 1.3f),
}

enum class DrawerSize(val label: String, val icon: Float) { Small("Small", 52f), Medium("Medium", 72f), Large("Large", 96f) }

/** A save-state slot shown in the switcher. */
data class StateSlot(val slot: Int, val label: String, val thumb: ImageBitmap?, val time: String)

/** An installed Android app the user can put on the Apps or Installed shelf. */
data class AppEntry(val pkg: String, val label: String, val likelyGame: Boolean, val canUninstall: Boolean = false)

enum class AppKind(val label: String) { Hidden("Hidden"), App("App"), Game("Game") }

/** Everything the Android (or preview) host has to provide to the shared UI. */
interface SlotHost {
    /** The app version shown in Settings > About. */
    val versionName: String get() = ""
    fun onLaunch(game: Game, resume: Boolean) {}
    fun onChooseCore(game: Game, coreId: String) {}
    fun onSettingsChanged(settings: Settings) {}
    fun onPickLibrary() {}
    fun onRescan() {}
    /** Save transfer (Settings > Saves): zip all saves out, zip saves in, or the same for one game. */
    fun onExportSaves(game: Game?) {}
    fun onImportSaves(game: Game?) {}
    /** Every launchable app on the device, for Settings > Apps & games. */
    fun listApps(): List<AppEntry> = emptyList()
    fun onAppKind(pkg: String, kind: AppKind) {}
    /** The app drawer was opened: load icons for [SlotState.drawerApps] into [SlotState.appIcons]. */
    fun onDrawerOpen() {}
    fun onLaunchApp(pkg: String) {}
    /** Opens Android's own App info screen for [pkg]. */
    fun onAppInfo(pkg: String) {}
    /** Asks Android to uninstall [pkg] (the system shows its usual confirmation). */
    fun onUninstallApp(pkg: String) {}
    fun onAction(action: GameAction) {}
    /** Touch overlay button for the running game. */
    fun onButton(pad: Pad, down: Boolean) {}
    /** On-screen analog stick: left (N64 stick) or right (N64 C buttons), each axis -1..1. */
    fun onStick(right: Boolean, x: Float, y: Float) {}
    fun sfx(effect: Sfx) {}
}

enum class Sfx { Insert, Eject, Tick, Select, Back }

/** No change: scale 1 and no offset for both the d-pad and the face buttons. */
val TOUCH_DEFAULT = listOf(1f, 0f, 0f, 1f, 0f, 0f)
