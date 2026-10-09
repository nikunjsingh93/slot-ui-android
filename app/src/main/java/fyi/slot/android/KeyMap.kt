package fyi.slot.android

import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import fyi.slot.ui.Pad
import kotlin.math.abs

object KeyMap {
    /** Maps Android keycodes to slot's controller vocabulary. [swap] flips A/B and X/Y. */
    fun pad(keyCode: Int, swap: Boolean): Pad? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> Pad.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> Pad.Down
        KeyEvent.KEYCODE_DPAD_LEFT -> Pad.Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> Pad.Right
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
            if (swap) Pad.B else Pad.A
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> if (swap) Pad.A else Pad.B
        KeyEvent.KEYCODE_BUTTON_X -> if (swap) Pad.Y else Pad.X
        KeyEvent.KEYCODE_BUTTON_Y -> if (swap) Pad.X else Pad.Y
        KeyEvent.KEYCODE_BUTTON_L1 -> Pad.L1
        KeyEvent.KEYCODE_BUTTON_R1 -> Pad.R1
        KeyEvent.KEYCODE_BUTTON_L2 -> Pad.L2
        KeyEvent.KEYCODE_BUTTON_R2 -> Pad.R2
        KeyEvent.KEYCODE_BUTTON_START -> Pad.Start
        KeyEvent.KEYCODE_BUTTON_SELECT -> Pad.Select
        KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_MENU -> Pad.Menu
        else -> null
    }

    /** The keycode to hand to LibretroDroid for a physical key, honouring the swap setting. */
    fun emuKey(keyCode: Int, swap: Boolean): Int {
        if (!swap) return keyCode
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            else -> keyCode
        }
    }

    fun padToKey(pad: Pad, swap: Boolean): Int? {
        val raw = when (pad) {
            Pad.Up -> KeyEvent.KEYCODE_DPAD_UP
            Pad.Down -> KeyEvent.KEYCODE_DPAD_DOWN
            Pad.Left -> KeyEvent.KEYCODE_DPAD_LEFT
            Pad.Right -> KeyEvent.KEYCODE_DPAD_RIGHT
            Pad.A -> KeyEvent.KEYCODE_BUTTON_A
            Pad.B -> KeyEvent.KEYCODE_BUTTON_B
            Pad.X -> KeyEvent.KEYCODE_BUTTON_X
            Pad.Y -> KeyEvent.KEYCODE_BUTTON_Y
            Pad.L1 -> KeyEvent.KEYCODE_BUTTON_L1
            Pad.R1 -> KeyEvent.KEYCODE_BUTTON_R1
            Pad.L2 -> KeyEvent.KEYCODE_BUTTON_L2
            Pad.R2 -> KeyEvent.KEYCODE_BUTTON_R2
            Pad.Start -> KeyEvent.KEYCODE_BUTTON_START
            Pad.Select -> KeyEvent.KEYCODE_BUTTON_SELECT
            Pad.Menu -> return null
        }
        // On-screen buttons are already in "emulator" space, so they are not swapped.
        return raw
    }

    fun isGamepad(device: InputDevice?): Boolean {
        device ?: return false
        val s = device.sources
        return !device.isVirtual &&
            ((s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)
    }

    fun hasGamepad(): Boolean = InputDevice.getDeviceIds().any { isGamepad(InputDevice.getDevice(it)) }
}

/**
 * Turns a hat switch / analog stick into repeating directional presses, for menus.
 * (Real DPAD *keys* arrive with Android's own auto-repeat, this is only for axes.)
 */
class AxisNav(private val onPress: (Pad, Boolean) -> Unit, private val onRelease: (Pad) -> Unit = {}) {
    private val handler = Handler(Looper.getMainLooper())
    private var held: Pad? = null
    private var repeater: Runnable? = null

    fun onMotion(e: MotionEvent): Boolean {
        if ((e.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK || e.action != MotionEvent.ACTION_MOVE) return false
        val hx = e.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val x = if (abs(hx) > .5f) hx else e.getAxisValue(MotionEvent.AXIS_X)
        val y = if (abs(hy) > .5f) hy else e.getAxisValue(MotionEvent.AXIS_Y)
        val dir = when {
            abs(x) > abs(y) && abs(x) > .6f -> if (x > 0) Pad.Right else Pad.Left
            abs(y) > .6f -> if (y > 0) Pad.Down else Pad.Up
            else -> null
        }
        if (dir != held) {
            stop()
            if (dir != null) {
                held = dir
                onPress(dir, false)
                val r = object : Runnable {
                    override fun run() { held?.let { onPress(it, true) }; handler.postDelayed(this, 90) }
                }
                repeater = r
                handler.postDelayed(r, 380)
            }
        }
        return true
    }

    /** Ends the current hold, telling the UI the direction was released (a hat/stick going back to centre is a key-up). */
    fun stop() {
        repeater?.let { handler.removeCallbacks(it) }
        repeater = null
        val was = held
        held = null
        if (was != null) onRelease(was)
    }
}
