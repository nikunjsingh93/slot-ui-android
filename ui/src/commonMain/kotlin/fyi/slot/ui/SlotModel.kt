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
    /** Shelf view "Grid": every game of the system as a grid of carts and discs (ignored while [shelfList] is on). */
    val shelfGrid: Boolean = false,
    /** List view text size. */
    val listSize: ListSize = ListSize.Medium,
    /** Size of the whole interface. */
    val uiScale: UiScale = UiScale.Default,
    val drawerGrid: Boolean = true,
    val drawerSize: DrawerSize = DrawerSize.Medium,
    /** Settings > Emulators: system id -> the core or emulator used unless a game has its own choice. */
    val defaultCores: Map<String, String> = emptyMap(),
    /** Portrait play with on-screen controls: how far the game picture sits below the top edge, 0..5 steps of 3% of the screen height. */
    val portraitScreenStep: Int = 3,
    /** Built-in 3D emulators (PS1, N64, PSP, Dreamcast): internal resolution, 1x (native) to 4x. */
    val upscale: Int = 1,
    /** PS1: fix the wobbly, warping 3D (PGXP). Runs PS1 games in SwanStation, which has it. */
    val pgxp: Boolean = false,
    /** Controls > Remap buttons: physical button (Pad name) -> what it does in games (Pad name). */
    val buttonMap: Map<String, String> = emptyMap(),
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
    /** DS touch screen: a finger at ([x], [y]) in window pixels that is on none of the on-screen buttons, or [down] false when it lifts. */
    fun onScreenTouch(x: Float, y: Float, down: Boolean) {}
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

/**
 * No change: scale 1 and no offset (x, y as shares of the screen) for each part of the on-screen layout, in order:
 * the d-pad, the face buttons, the shoulder buttons (L/R/L2/R2), Start/Select, and the game screen itself.
 */
val TOUCH_DEFAULT = listOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f)

/** Indices into a layout tweak where each part's (scale, x, y) start. */
const val TWEAK_DPAD = 0
const val TWEAK_BUTTONS = 3
const val TWEAK_SHOULDERS = 6
const val TWEAK_START = 9
const val TWEAK_SCREEN = 12

/** A layout saved by an older version (fewer parts) padded out with the defaults. */
fun fullTweak(t: List<Float>?): List<Float> = if (t == null || t.size > TOUCH_DEFAULT.size) TOUCH_DEFAULT else t + TOUCH_DEFAULT.drop(t.size)

/** Layouts are kept per system and orientation: portrait under the system id, landscape under "<id>@l". */
fun touchKey(sys: GameSystem, portrait: Boolean) = if (portrait) sys.id else sys.id + "@l"

/** The layout for [sys] in this orientation; landscape starts from the portrait one until it is edited itself. */
fun Settings.touchTweak(sys: GameSystem, portrait: Boolean): List<Float> =
    fullTweak(touchTweaks[touchKey(sys, portrait)] ?: touchTweaks[sys.id])

/**
 * Where the game picture goes on a [w] x [h] screen (pixels): the DS's two stacked screens fitted to their shape, or the
 * area above the on-screen controls in portrait, or the whole screen, then moved and resized as the layout says.
 */
fun gameScreenArea(sys: GameSystem, ds: Boolean, w: Float, h: Float, touch: Boolean, step: Int, tweak: List<Float>): androidx.compose.ui.geometry.Rect {
    val portrait = w < h
    val base = when {
        ds && portrait && touch -> {
            val top = h * 0.01f * step
            val ww = minOf(w, h * 0.72f * 256f / 384f)
            androidx.compose.ui.geometry.Rect((w - ww) / 2f, top, (w + ww) / 2f, top + ww * 384f / 256f)
        }
        ds -> {
            val ww = minOf(w, h * 256f / 384f)
            val hh = ww * 384f / 256f
            androidx.compose.ui.geometry.Rect((w - ww) / 2f, (h - hh) / 2f, (w + ww) / 2f, (h + hh) / 2f)
        }
        portrait && touch -> {
            val top = h * 0.03f * step
            androidx.compose.ui.geometry.Rect(0f, top, w, top + h * (if (sys.external) .54f else .46f))
        }
        else -> androidx.compose.ui.geometry.Rect(0f, 0f, w, h)
    }
    val t = fullTweak(tweak)
    val s = t[TWEAK_SCREEN]
    val cx = base.center.x + t[TWEAK_SCREEN + 1] * w
    val cy = base.center.y + t[TWEAK_SCREEN + 2] * h
    return androidx.compose.ui.geometry.Rect(cx - base.width * s / 2f, cy - base.height * s / 2f, cx + base.width * s / 2f, cy + base.height * s / 2f)
}

/** List view text size; rows grow with the text so the highlight still fits. */
enum class ListSize(val label: String, val scale: Float) { Small("Small", 0.85f), Medium("Medium", 1f), Large("Large", 1.2f), XLarge("Extra large", 1.4f) }

/** The whole interface, bigger or smaller (the shelf, the bars, titles and menus); tablets usually want 70-80%. */
enum class UiScale(val label: String, val scale: Float) {
    P60("60%", 0.6f), P70("70%", 0.7f), XSmall("80%", 0.8f), Small("90%", 0.9f), Default("100%", 1f),
    Large("110%", 1.1f), XLarge("120%", 1.2f), XXLarge("135%", 1.35f),
}
