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

enum class Overlay { None, Quick, States, Settings, Library, Eject }

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
)

enum class TouchMode(val label: String) { Auto("When no controller"), Always("Always"), Never("Never") }

/** A save-state slot shown in the switcher. */
data class StateSlot(val slot: Int, val label: String, val thumb: ImageBitmap?, val time: String)

/** An installed Android app the user can put on the Apps or Installed shelf. */
data class AppEntry(val pkg: String, val label: String, val likelyGame: Boolean)

enum class AppKind(val label: String) { Hidden("Hidden"), App("App"), Game("Game") }

/** Everything the Android (or preview) host has to provide to the shared UI. */
interface SlotHost {
    fun onLaunch(game: Game, resume: Boolean) {}
    fun onChooseCore(game: Game, coreId: String) {}
    fun onSettingsChanged(settings: Settings) {}
    fun onPickLibrary() {}
    fun onRescan() {}
    /** Every launchable app on the device, for Settings > Apps & games. */
    fun listApps(): List<AppEntry> = emptyList()
    fun onAppKind(pkg: String, kind: AppKind) {}
    fun onAction(action: GameAction) {}
    /** Touch overlay button for the running game. */
    fun onButton(pad: Pad, down: Boolean) {}
    fun sfx(effect: Sfx) {}
}

enum class Sfx { Insert, Eject, Tick, Select, Back }
