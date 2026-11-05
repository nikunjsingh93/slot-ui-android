package fyi.slot.android

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import fyi.slot.ui.CartShape
import fyi.slot.ui.Game
import fyi.slot.ui.GameAction
import fyi.slot.ui.GameSystem
import fyi.slot.ui.Overlay
import fyi.slot.ui.Pad
import fyi.slot.ui.Screen
import fyi.slot.ui.Settings
import fyi.slot.ui.Sfx
import fyi.slot.ui.SlotHost
import fyi.slot.ui.SlotRoot
import fyi.slot.ui.SlotState
import fyi.slot.ui.touchTweak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * Runs one game. Lives in its own process (see the manifest) so that a misbehaving core can only
 * ever take down this activity, never the launcher.
 */
class GameActivity : ComponentActivity(), SlotHost {
    private lateinit var game: Game
    private lateinit var state: SlotState
    private lateinit var store: StateStore
    private lateinit var settings: Settings
    private lateinit var sounds: Sounds
    private val handler = Handler(Looper.getMainLooper())
    private var retro by mutableStateOf<GLRetroView?>(null)
    /** The game screen "powers on" and off through black, like slot (driven by the frame clock, not Compose animations). */
    private var blackAlpha by androidx.compose.runtime.mutableFloatStateOf(1f)

    private var started = false
    private var emuPaused = false
    private var ejecting = false
    private var lastShot: Bitmap? = null
    /** Disc and N64 systems use L2/R2 as real buttons, so rewind and fast-forward stay off them. */
    private val triggersAreButtons get() = game.system.external

    private fun passKey(v: GLRetroView, pad: Pad, down: Boolean) {
        KeyMap.padToKey(pad, false)?.let { v.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, it, 0) }
    }

    private var rewinding = false
    private val rewindBuf = ArrayDeque<ByteArray>()
    private var ffHeld = false
    private var ffLocked = false
    private var lastR2Tap = 0L
    private val turboJobs = HashMap<Int, Runnable>()
    private val axisNav = AxisNav(
        { pad, repeat -> state.press(pad, repeat, android.os.SystemClock.uptimeMillis()) },
        { pad -> state.release(pad, android.os.SystemClock.uptimeMillis()) },
    )
    private var housekeeping: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            Log.e("slot", "game process crashed", e)
            Process.killProcess(Process.myPid())
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val sysId = intent.getStringExtra(EXTRA_SYSTEM) ?: return finishCancelled()
        val system = GameSystem.byId(sysId) ?: return finishCancelled()
        game = Game(
            id = intent.getStringExtra(EXTRA_ID) ?: return finishCancelled(),
            stem = intent.getStringExtra(EXTRA_STEM) ?: "game", system = system,
            uri = intent.getStringExtra(EXTRA_URI) ?: return finishCancelled(),
            size = intent.getLongExtra(EXTRA_SIZE, 0), entryName = intent.getStringExtra(EXTRA_ENTRY) ?: "",
            coreId = intent.getStringExtra(EXTRA_CORE),
        )
        settings = Persist.loadSettings(this)
        store = StateStore(this, game.id)
        sounds = Sounds(this).also { it.enabled = settings.sounds }
        applyBrightness()

        state = SlotState(this).also {
            it.settings = settings
            it.screen = Screen.Playing
            it.playing = game
            it.gamepad = KeyMap.hasGamepad()
        }
        hideSystemBars()

        setContent {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
                val portrait = maxWidth < maxHeight
                retro?.let { view ->
                    // DS: the view is exactly the two stacked screens (256 x 384), so a touch on it lands where the
                    // finger is (LibretroDroid measures touches across the whole view, bars included)
                    val ds = game.core.id == "melondsds"
                    // where the picture goes, as moved and resized in Settings > Controls > Edit button layout (same
                    // function the editor draws its outline with)
                    val wPx = constraints.maxWidth.toFloat()
                    val hPx = constraints.maxHeight.toFloat()
                    val area = fyi.slot.ui.gameScreenArea(
                        game.system, ds, wPx, hPx, touch = touchVisible(), step = state.settings.portraitScreenStep,
                        tweak = state.settings.touchTweak(game.system, portrait),
                    )
                    val dens = androidx.compose.ui.platform.LocalDensity.current
                    AndroidView(
                        factory = { view },
                        modifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(area.left.toInt(), area.top.toInt()) }
                            .size(with(dens) { area.width.toDp() }, with(dens) { area.height.toDp() }),
                    )
                }
                SlotRoot(state, gameProcess = true)
                if (blackAlpha > 0f) androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = blackAlpha)))
            }
        }

        // Pause the emulation whenever a menu is up.
        lifecycleScope.launch {
            snapshotFlow { state.overlay }.collectLatest { o ->
                if (o != Overlay.None) { prepareShot(); pauseEmu() } else resumeEmu()
            }
        }

        lifecycleScope.launch {
            val rom = runCatching { withContext(Dispatchers.IO) { RomFiles.materialize(this@GameActivity, game) } }.getOrElse {
                Log.e("slot", "rom copy failed", it)
                finishCancelled(); return@launch
            }
            start(rom)
        }
    }

    private fun touchVisible() = when (state.settings.showTouchControls) {
        fyi.slot.ui.TouchMode.Always -> true
        fyi.slot.ui.TouchMode.Never -> false
        fyi.slot.ui.TouchMode.Auto -> !state.gamepad
    }

    // --- boot -----------------------------------------------------------------------------------

    private fun start(rom: File) {
        val system = File(getExternalFilesDir(null), "system").also { it.mkdirs() }
        var coreId = game.core.id
        if (coreId == "gpsp" && !File(system, "gba_bios.bin").exists()) {
            state.toast("gpSP needs gba_bios.bin (see the README). Using mGBA instead.")
            coreId = "mgba"
            game = game.copy(coreId = "mgba")
        }
        // Settings > Gameplay > PS1 geometry fix: PGXP is SwanStation's, so PS1 games run there while it is on
        if (coreId == "pcsx_rearmed" && settings.pgxp) {
            coreId = "swanstation"
            game = game.copy(coreId = "swanstation")
        }
        val corePath = File(applicationInfo.nativeLibraryDir, "lib${coreId}_libretro_android.so")
        if (!corePath.exists()) { state.toast("Core $coreId is missing from this build"); handler.postDelayed({ finishCancelled() }, 1800); return }

        val data = GLRetroViewData(this).apply {
            coreFilePath = corePath.absolutePath
            gameFilePath = rom.absolutePath
            systemDirectory = system.absolutePath
            savesDirectory = File(filesDir, "saves/${game.id}").also { it.mkdirs() }.absolutePath
            variables = CoreOptions.variables(game, coreId, settings)
            shader = CoreOptions.shader(game, settings)
            rumbleEventsEnabled = settings.rumble
            saveRAMState = store.readSram()
            preferLowLatencyAudio = true
        }
        val view = GLRetroView(this, data)
        lifecycle.addObserver(view)
        retro = view

        lifecycleScope.launch {
            view.getGLRetroErrors().collect { code ->
                Log.e("slot", "libretro error $code")
                state.toast("The emulator couldn't start this game (error $code)")
                delay(2200)
                finishCancelled()
            }
        }
        lifecycleScope.launch {
            view.getGLRetroEvents().collect { ev ->
                if (ev is GLRetroView.GLRetroEvents.FrameRendered && !started) {
                    started = true
                    onFirstFrame(view)
                }
            }
        }
        lifecycleScope.launch {
            view.getRumbleEvents().collect { r ->
                if (settings.rumble) vibrate(max(r.strengthStrong, r.strengthWeak))
            }
        }
    }

    private suspend fun nextFrame(): Long = suspendCancellableCoroutine { c ->
        val cb = android.view.Choreographer.FrameCallback { c.resume(it) }
        android.view.Choreographer.getInstance().postFrameCallback(cb)
        c.invokeOnCancellation { android.view.Choreographer.getInstance().removeFrameCallback(cb) }
    }

    private suspend fun fadeBlack(to: Float, seconds: Float) {
        val from = blackAlpha
        var last = nextFrame()
        var t = 0f
        while (t < seconds) {
            val now = nextFrame()
            t += ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
            blackAlpha = from + (to - from) * (t / seconds).coerceIn(0f, 1f)
        }
        blackAlpha = to
    }

    private suspend fun onFirstFrame(view: GLRetroView) {
        if (BuildConfig.DEBUG) runCatching { view.getVariables().forEach { Log.d("slotvars", "${it.key}=${it.value}") } }
        if (intent.getBooleanExtra(EXTRA_RESUME, true)) {
            val s = withContext(Dispatchers.IO) { store.read(0) }
            if (s != null) {
                val ok = withContext(Dispatchers.Default) { runCatching { view.unserializeState(s) }.getOrDefault(false) }
                if (!ok) state.toast("Couldn't restore the last session")
            }
        }
        startHousekeeping(view)
        fadeBlack(0f, 0.22f)
    }

    /** SRAM autosave every 20s and a short rolling buffer for rewind. */
    private fun startHousekeeping(view: GLRetroView) {
        housekeeping?.cancel()
        housekeeping = lifecycleScope.launch(Dispatchers.Default) {
            var tick = 0
            while (true) {
                delay(100)
                if (emuPaused || ejecting) continue
                if (rewinding) {
                    val s = rewindBuf.removeLastOrNull()
                    if (s != null) runCatching { view.unserializeState(s) }
                    continue
                }
                if (settings.rewind && !triggersAreButtons) {
                    runCatching { view.serializeState() }.getOrNull()?.let {
                        rewindBuf.addLast(it)
                        while (rewindBuf.size > REWIND_FRAMES) rewindBuf.removeFirst()
                    }
                }
                if (++tick % 200 == 0) runCatching { store.writeSram(view.serializeSRAM()) }
            }
        }
    }

    // --- pause / resume ------------------------------------------------------------------------------

    private fun pauseEmu() {
        val v = retro ?: return
        if (emuPaused || !started) return
        emuPaused = true
        v.audioEnabled = false
        v.onPause()
    }

    private fun resumeEmu() {
        val v = retro ?: return
        if (!emuPaused) return
        emuPaused = false
        v.onResume()
        v.audioEnabled = !(settings.muteFastForward && (ffHeld || ffLocked))
    }

    private suspend fun prepareShot() {
        lastShot = capture() ?: lastShot
    }

    private suspend fun capture(): Bitmap? = suspendCancellableCoroutine { cont ->
        val v = retro
        if (v == null || v.width == 0 || v.height == 0 || !started) { cont.resume(null); return@suspendCancellableCoroutine }
        val bmp = Bitmap.createBitmap(max(1, v.width / 2), max(1, v.height / 2), Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(v, bmp, { r -> cont.resume(if (r == PixelCopy.SUCCESS) trimBlack(bmp) else null) }, handler)
        } catch (e: Exception) {
            cont.resume(null)
        }
    }

    /** Crops the black letterbox/pillarbox bars so state thumbnails show just the game. */
    private fun trimBlack(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        fun dark(c: Int) = (c and 0xFFFFFF) == 0
        var top = 0; var bottom = h - 1; var left = 0; var right = w - 1
        while (top < bottom && (0 until w).all { dark(px[top * w + it]) }) top++
        while (bottom > top && (0 until w).all { dark(px[bottom * w + it]) }) bottom--
        while (left < right && (top..bottom).all { dark(px[it * w + left]) }) left++
        while (right > left && (top..bottom).all { dark(px[it * w + right]) }) right--
        val cw = right - left + 1
        val ch = bottom - top + 1
        if (cw < 16 || ch < 16 || (cw == w && ch == h)) return b
        return Bitmap.createBitmap(b, left, top, cw, ch)
    }

    // --- SlotHost: actions from the menus -----------------------------------------------------------

    override fun onAction(action: GameAction) {
        when (action) {
            GameAction.Resume -> {}
            GameAction.RefreshStates -> {
                state.states.clear()
                state.states.addAll(store.slots())
            }
            is GameAction.SaveState -> saveState(action.slot)
            is GameAction.LoadState -> loadState(action.slot)
            is GameAction.DeleteState -> { store.delete(action.slot); onAction(GameAction.RefreshStates) }
            GameAction.UndoLoad -> undoLoad()
            GameAction.Reset -> retro?.reset()
            is GameAction.FastForward -> { ffLocked = action.on; applyFastForward() }
            is GameAction.Rewind -> {}
            is GameAction.Eject -> eject(action.save)
        }
    }

    private fun saveState(slot: Int) {
        val v = retro ?: return
        val shot = lastShot
        lifecycleScope.launch(Dispatchers.Default) {
            runCatching {
                val data = v.serializeState()
                store.write(slot, data, shot)
                runCatching { store.writeSram(v.serializeSRAM()) }
                withContext(Dispatchers.Main) {
                    state.toast(if (slot == 0) "Saved" else "Saved to slot $slot")
                    state.states.clear(); state.states.addAll(store.slots())
                }
            }.onFailure { withContext(Dispatchers.Main) { state.toast("Couldn't save the state") } }
        }
    }

    private fun loadState(slot: Int) {
        val v = retro ?: return
        lifecycleScope.launch(Dispatchers.Default) {
            val data = store.read(slot)
            if (data == null) { withContext(Dispatchers.Main) { state.toast("Slot $slot is empty") }; return@launch }
            runCatching { store.stashUndo(v.serializeState()) }
            val ok = runCatching { v.unserializeState(data) }.getOrDefault(false)
            withContext(Dispatchers.Main) { state.toast(if (ok) "State loaded" else "That state doesn't match this game") }
        }
    }

    private fun undoLoad() {
        val v = retro ?: return
        lifecycleScope.launch(Dispatchers.Default) {
            val data = store.readUndo()
            val ok = data != null && runCatching { v.unserializeState(data) }.getOrDefault(false)
            withContext(Dispatchers.Main) { state.toast(if (ok) "Load undone" else "Nothing to undo") }
        }
    }

    private fun eject(save: Boolean) {
        if (ejecting) return
        ejecting = true
        val v = retro
        lifecycleScope.launch {
            if (v != null && started) {
                if (lastShot == null) lastShot = capture()
                val shot = lastShot
                withContext(Dispatchers.Default) {
                    runCatching { store.writeSram(v.serializeSRAM()) }
                    if (save) runCatching { store.write(0, v.serializeState(), shot) }
                }
            }
            fadeBlack(1f, 0.16f)
            setResult(Activity.RESULT_OK)
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onSettingsChanged(settings: Settings) {
        this.settings = settings
        Persist.saveSettings(this, settings)
        sounds.enabled = settings.sounds
        applyBrightness()
        val v = retro ?: return
        v.shader = CoreOptions.shader(game, settings)
        val vars = CoreOptions.variables(game, game.core.id, settings)
        if (vars.isNotEmpty()) runCatching { v.updateVariables(*vars) }
        applyFastForward()
    }

    override fun sfx(effect: Sfx) = sounds.play(effect)

    private fun applyBrightness() {
        val lp = window.attributes
        lp.screenBrightness = if (settings.brightness >= .99f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else settings.brightness.coerceAtLeast(.2f)
        window.attributes = lp
    }

    private fun applyFastForward() {
        val v = retro ?: return
        if (!started) return
        val on = ffHeld || ffLocked
        state.fastForward = on
        v.frameSpeed = if (on) settings.fastForwardSpeed else 1
        if (!emuPaused) v.audioEnabled = !(on && settings.muteFastForward)
    }

    private fun vibrate(strength: Float) {
        if (strength <= 0f) return
        val vib = getSystemService(android.os.Vibrator::class.java) ?: return
        vib.vibrate(android.os.VibrationEffect.createOneShot(25, (strength * 255).toInt().coerceIn(1, 255)))
    }

    // --- touch overlay -----------------------------------------------------------------------------------

    private var screenTouchDown = 0L
    private var screenTouching = false

    /**
     * DS touch screen: the on-screen controls hand over a finger that is on none of their buttons. It is passed to the
     * game view as a one-finger touch, which LibretroDroid maps onto the picture and gives the core as its pointer.
     */
    override fun onScreenTouch(x: Float, y: Float, down: Boolean) {
        val v = retro ?: return
        val at = IntArray(2).also { v.getLocationInWindow(it) }
        val now = android.os.SystemClock.uptimeMillis()
        val action = when {
            down && !screenTouching -> { screenTouchDown = now; android.view.MotionEvent.ACTION_DOWN }
            down -> android.view.MotionEvent.ACTION_MOVE
            screenTouching -> android.view.MotionEvent.ACTION_UP
            else -> return
        }
        screenTouching = down
        // a quick tap is held for a few frames: the core only looks at the touch once a frame
        if (action == android.view.MotionEvent.ACTION_UP && now - screenTouchDown < 80) {
            handler.postDelayed({ if (!screenTouching) onScreenLift(x - at[0], y - at[1]) }, 80 - (now - screenTouchDown))
            return
        }
        val ev = android.view.MotionEvent.obtain(screenTouchDown, now, action, x - at[0], y - at[1], 0)
        ev.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        v.onTouchEvent(ev)
        ev.recycle()
    }

    private fun onScreenLift(x: Float, y: Float) {
        val v = retro ?: return
        val now = android.os.SystemClock.uptimeMillis()
        val ev = android.view.MotionEvent.obtain(screenTouchDown, now, android.view.MotionEvent.ACTION_UP, x, y, 0)
        v.onTouchEvent(ev)
        ev.recycle()
    }

    override fun onStick(right: Boolean, x: Float, y: Float) {
        retro?.sendMotionEvent(if (right) GLRetroView.MOTION_SOURCE_ANALOG_RIGHT else GLRetroView.MOTION_SOURCE_ANALOG_LEFT, x, y, 0)
    }

    override fun onButton(pad: Pad, down: Boolean) {
        val v = retro ?: return
        when (pad) {
            Pad.L2 -> if (triggersAreButtons) passKey(v, pad, down) else rewinding = down && settings.rewind
            Pad.R2 -> if (triggersAreButtons) passKey(v, pad, down) else { ffHeld = down; applyFastForward() }
            else -> KeyMap.padToKey(pad, false)?.let {
                v.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, it, 0)
            }
        }
    }

    // --- input ------------------------------------------------------------------------------------------------

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (BuildConfig.DEBUG) Log.d("slotkey", "key=${KeyEvent.keyCodeToString(e.keyCode)} act=${e.action} rep=${e.repeatCount} overlay=${state.overlay} cur=${state.menuCursor.lastOrNull()}")
        val swap = settings.swapFaceButtons
        if (KeyMap.isGamepad(e.device)) state.gamepad = true
        val pad = KeyMap.pad(e.keyCode, swap)
        val isMenuKey = pad == Pad.Menu || e.keyCode == KeyEvent.KEYCODE_BACK
        if (isMenuKey) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                // Back closes whatever is open, MENU toggles the quick menu.
                if (e.keyCode == KeyEvent.KEYCODE_BACK && state.overlay != Overlay.None) state.press(Pad.B, now = e.eventTime)
                else state.press(Pad.Menu, now = e.eventTime)
            }
            return true
        }
        if (state.overlay != Overlay.None) {
            if (pad == null) return super.dispatchKeyEvent(e)
            when (e.action) {
                KeyEvent.ACTION_DOWN -> state.press(pad, e.repeatCount > 0, e.eventTime)
                KeyEvent.ACTION_UP -> state.release(pad, e.eventTime)
            }
            return true
        }
        val v = retro ?: return true
        val down = e.action == KeyEvent.ACTION_DOWN
        if (e.action != KeyEvent.ACTION_DOWN && e.action != KeyEvent.ACTION_UP) return super.dispatchKeyEvent(e)
        if (down && e.repeatCount > 0) return true
        val port = max(0, (e.device?.controllerNumber ?: 1) - 1)
        val raw = remapped(e.keyCode)
        val key = gameKey(raw, swap)
        when (if (triggersAreButtons) 0 else raw) {
            KeyEvent.KEYCODE_BUTTON_L2 -> { rewinding = down && settings.rewind; return true }
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                if (down) {
                    val now = e.eventTime
                    if (now - lastR2Tap < 300) { ffLocked = !ffLocked; lastR2Tap = 0 } else lastR2Tap = now
                    ffHeld = true
                } else ffHeld = false
                applyFastForward()
                return true
            }
        }
        // X/Y become turbo A/B on systems that don't use them
        val turboOk = settings.turbo && game.system.shape in TURBO_SHAPES
        if (turboOk && (raw == KeyEvent.KEYCODE_BUTTON_X || raw == KeyEvent.KEYCODE_BUTTON_Y)) {
            val target = if (raw == KeyEvent.KEYCODE_BUTTON_X) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B
            setTurbo(v, raw, target, down, port)
            return true
        }
        if (pad == null && key in GAMEPAD_KEYS_ALL) { v.sendKeyEvent(e.action, key, port); return true }
        if (key in GAMEPAD_KEYS_ALL) { v.sendKeyEvent(e.action, key, port); return true }
        return super.dispatchKeyEvent(e)
    }

    /** Settings > Controls > Remap buttons: the button this physical one stands for in games. */
    private fun remapped(keyCode: Int): Int {
        val from = KeyMap.pad(keyCode, false) ?: return keyCode
        val to = settings.buttonMap[from.name]?.let { n -> Pad.entries.firstOrNull { it.name == n } } ?: return keyCode
        return KeyMap.padToKey(to, false) ?: keyCode
    }

    /**
     * The key the core gets: Settings > Controls > Swap A/B and X/Y, and on the PlayStation systems (PS1, PSP) the face
     * buttons the other way round from the Nintendo-style ones, so the confirm button is Cross where it belongs.
     */
    private fun gameKey(keyCode: Int, swap: Boolean): Int {
        val ps = game.system.shape == CartShape.DISC_PSX || game.system.shape == CartShape.UMD
        return KeyMap.emuKey(keyCode, swap != ps)
    }

    private fun setTurbo(v: GLRetroView, source: Int, target: Int, down: Boolean, port: Int) {
        turboJobs.remove(source)?.let { handler.removeCallbacks(it) }
        if (!down) { v.sendKeyEvent(KeyEvent.ACTION_UP, target, port); return }
        var on = true
        val r = object : Runnable {
            override fun run() {
                v.sendKeyEvent(if (on) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, target, port)
                on = !on
                handler.postDelayed(this, 33)
            }
        }
        turboJobs[source] = r
        r.run()
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if ((ev.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK || ev.action != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(ev)
        }
        state.gamepad = true
        if (state.overlay != Overlay.None) return axisNav.onMotion(ev)
        val v = retro ?: return true
        val port = max(0, (ev.device?.controllerNumber ?: 1) - 1)
        var dx = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
        var dy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val sx = ev.getAxisValue(MotionEvent.AXIS_X)
        val sy = ev.getAxisValue(MotionEvent.AXIS_Y)
        if (dx == 0f && kotlin.math.abs(sx) > .5f) dx = sx
        if (dy == 0f && kotlin.math.abs(sy) > .5f) dy = sy
        v.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, dx, dy, port)
        v.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, sx, sy, port)
        v.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, ev.getAxisValue(MotionEvent.AXIS_Z), ev.getAxisValue(MotionEvent.AXIS_RZ), port)
        // some pads report triggers only as axes
        val lt = max(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = max(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS))
        if (!triggersAreButtons) rewinding = lt > .6f && settings.rewind
        if (!triggersAreButtons && (rt > .6f) != ffHeld) { ffHeld = rt > .6f; applyFastForward() }
        return true
    }

    // --- lifecycle ----------------------------------------------------------------------------------------------

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars() else axisNav.stop()
    }

    override fun onResume() {
        super.onResume()
        // LibretroDroid restarts itself on resume; if a menu is still up, keep the game frozen behind it.
        if (state.overlay != Overlay.None && started) { emuPaused = false; pauseEmu() }
    }

    override fun onStop() {
        super.onStop()
        val v = retro
        if (v != null && started && !ejecting) {
            // Going to the background: keep progress safe in case Android reclaims us.
            Thread {
                runCatching { store.writeSram(v.serializeSRAM()) }
                if (settings.autoSaveOnEject) runCatching { store.write(0, v.serializeState(), lastShot) }
            }.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        housekeeping?.cancel()
        turboJobs.values.forEach { handler.removeCallbacks(it) }
        sounds.release()
        // The emulator core is native singleton state: give the next game a clean process.
        handler.postDelayed({ Process.killProcess(Process.myPid()) }, 150)
    }

    private fun finishCancelled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_STEM = "stem"
        const val EXTRA_SYSTEM = "system"
        const val EXTRA_URI = "uri"
        const val EXTRA_ENTRY = "entry"
        const val EXTRA_CORE = "core"
        const val EXTRA_SIZE = "size"
        const val EXTRA_RESUME = "resume"
        private const val REWIND_FRAMES = 60
        private val TURBO_SHAPES = setOf(fyi.slot.ui.CartShape.GBA, fyi.slot.ui.CartShape.GB, fyi.slot.ui.CartShape.GBC, fyi.slot.ui.CartShape.NES, fyi.slot.ui.CartShape.CARD, fyi.slot.ui.CartShape.HUCARD)
        private val GAMEPAD_KEYS_ALL = setOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
        )
    }
}
