package fyi.slot.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import fyi.slot.ui.Game
import fyi.slot.ui.GameAction
import fyi.slot.ui.Pad
import fyi.slot.ui.Screen
import fyi.slot.ui.Settings
import fyi.slot.ui.Sfx
import fyi.slot.ui.SlotHost
import fyi.slot.ui.SlotRoot
import fyi.slot.ui.SlotState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity(), SlotHost {
    private lateinit var state: SlotState
    /** Games found by the ROM scan; apps are added on top of this by [publish]. */
    private var romGames: List<Game> = emptyList()

    private fun publish() = state.setLibrary(romGames + Apps.games(this, state.appKinds))
    private lateinit var sounds: Sounds
    private lateinit var covers: Covers
    private val axisNav = AxisNav(
        { pad, repeat -> state.press(pad, repeat, android.os.SystemClock.uptimeMillis()) },
        { pad -> state.release(pad, android.os.SystemClock.uptimeMillis()) },
    )
    private var scanJob: kotlinx.coroutines.Job? = null

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) addRoot(uri)
    }

    private val playGame = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        onGameClosed(res.resultCode)
    }

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = refreshHud(i)
    }

    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) = refreshGamepad()
        override fun onInputDeviceRemoved(id: Int) = refreshGamepad()
        override fun onInputDeviceChanged(id: Int) = refreshGamepad()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        sounds = Sounds(this)
        covers = Covers(this)
        state = SlotState(this).also {
            it.settings = Persist.loadSettings(this)
            it.appKinds.putAll(Apps.loadKinds(this))
            it.gamepad = KeyMap.hasGamepad()
        }
        sounds.enabled = state.settings.sounds

        romGames = Persist.loadLibrary(this)
        publish()

        setContent { SlotRoot(state) }

        (getSystemService(INPUT_SERVICE) as InputManager).registerInputDeviceListener(inputListener, null)
        androidx.core.content.ContextCompat.registerReceiver(this, tick, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshHud(null)

        // Cart art for whatever is on screen.
        lifecycleScope.launch {
            snapshotFlow { Triple(state.systemIndex, state.selected, state.shelf.size) }.collectLatest {
                loadCoversAround()
            }
        }

        val localRoms = java.io.File(getExternalFilesDir(null), "roms").also { it.mkdirs() }
        if (Persist.loadRoots(this).isEmpty() && localRoms.listFiles().isNullOrEmpty() && state.games.isEmpty()) {
            state.toast("Choose your ROM folder to get started")
        } else {
            rescan(quiet = true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(tick) }
        (getSystemService(INPUT_SERVICE) as InputManager).unregisterInputDeviceListener(inputListener)
        sounds.release()
    }

    override fun onResume() {
        super.onResume()
        // coming back from an external emulator counts as ejecting the cart
        if (externalActive && state.screen == Screen.Playing && android.os.SystemClock.uptimeMillis() - externalAt > 1200) {
            externalActive = false
            onGameClosed(android.app.Activity.RESULT_OK)
        }
        hideSystemBars()
        refreshHud(null)
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars() else { axisNav.stop(); state.releaseAll() }
    }

    // --- HUD feeds -----------------------------------------------------------------------------

    private fun refreshHud(batteryIntent: Intent?) {
        val i = batteryIntent?.takeIf { it.action == Intent.ACTION_BATTERY_CHANGED }
            ?: registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0) (level * 100 / max(scale, 1)) else 100
        state.hud = state.hud.copy(
            batteryPercent = pct, charging = charging,
            clock = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()),
        )
    }

    private fun refreshGamepad() {
        state.gamepad = KeyMap.hasGamepad()
    }

    // --- library ---------------------------------------------------------------------------------

    private fun addRoot(uri: Uri) {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val roots = (Persist.loadRoots(this) + uri.toString()).distinct()
        Persist.saveRoots(this, roots)
        state.overlay = fyi.slot.ui.Overlay.None
        rescan(quiet = false)
    }

    private fun rescan(quiet: Boolean) {
        val roots = Persist.loadRoots(this).map(Uri::parse)
        val local = java.io.File(getExternalFilesDir(null), "roms").also { it.mkdirs() }
        if (roots.isEmpty() && (local.listFiles()?.isEmpty() != false)) { state.toast("No ROM folder yet. Pick one in Settings > Library"); return }
        scanJob?.cancel()
        state.scanning = true
        if (!quiet) state.toast("Scanning your library...")
        scanJob = lifecycleScope.launch {
            val before = state.games.size
            val meta = withContext(Dispatchers.IO) { Persist.loadLibrary(this@MainActivity).associateBy { it.id } }
            fun merge(list: List<Game>) = list.map { g ->
                meta[g.id]?.let { g.copy(coreId = it.coreId, lastPlayed = it.lastPlayed, hasSave = StateStore.hasAuto(this@MainActivity, g.id)) } ?: g
            }
            val list = runCatching {
                Scanner(this@MainActivity).scan(roots, local) { partial ->
                    runOnUiThread { if (state.scanning && before == 0) { romGames = merge(partial); publish() } }
                }
            }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                state.scanning = false
                state.toast("Scan failed: ${it.message}")
                return@launch
            }
            val merged = merge(list)
            withContext(Dispatchers.IO) { Persist.saveLibrary(this@MainActivity, merged) }
            state.scanning = false
            romGames = merged
            publish()
            if (!quiet || merged.size != before) state.toast("${merged.size} games in your library")
        }
    }

    private suspend fun loadCoversAround() {
        val shelf = state.shelf
        if (shelf.isEmpty()) return
        val lo = max(0, state.selected - 4)
        val hi = min(shelf.lastIndex, state.selected + 4)
        // current first, then outwards
        val order = (lo..hi).sortedBy { kotlin.math.abs(it - state.selected) }
        for (i in order) {
            val g = shelf[i]
            if (state.covers.containsKey(g.id)) continue
            val img = covers.load(g, state.settings.downloadCovers) ?: continue
            state.covers[g.id] = img
        }
    }

    // --- SlotHost --------------------------------------------------------------------------------

    override fun onLaunch(game: Game, resume: Boolean) {
        Persist.updateMeta(this, game.id, core = game.coreId ?: game.core.id, played = System.currentTimeMillis())
        if (game.system.external) { launchExternal(game); return }
        val i = Intent(this, GameActivity::class.java)
            .putExtra(GameActivity.EXTRA_ID, game.id).putExtra(GameActivity.EXTRA_STEM, game.stem)
            .putExtra(GameActivity.EXTRA_SYSTEM, game.system.id).putExtra(GameActivity.EXTRA_URI, game.uri)
            .putExtra(GameActivity.EXTRA_ENTRY, game.entryName).putExtra(GameActivity.EXTRA_CORE, game.core.id)
            .putExtra(GameActivity.EXTRA_SIZE, game.size)
            .putExtra(GameActivity.EXTRA_RESUME, resume)
        playGame.launch(i)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private var externalActive = false
    private var externalAt = 0L

    /** Hands the game to the emulator app that owns this system. We know it is over when slot comes back to the front. */
    private fun launchExternal(game: Game) {
        when (val r = ExternalLauncher.launch(this, game)) {
            is ExternalLauncher.Result.Started -> {
                externalActive = true
                externalAt = android.os.SystemClock.uptimeMillis()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
            is ExternalLauncher.Result.Failed -> {
                state.ejected()
                state.toast(r.message)
            }
        }
    }

    private fun onGameClosed(resultCode: Int) {
        state.settings = Persist.loadSettings(this)
        sounds.enabled = state.settings.sounds
        state.games = state.games.map { it.copy(hasSave = StateStore.hasAuto(this, it.id)) }
        state.ejected()
        if (resultCode == android.app.Activity.RESULT_CANCELED) state.toast("The game closed unexpectedly")
    }

    override fun onChooseCore(game: Game, coreId: String) {
        Persist.updateMeta(this, game.id, core = coreId)
    }

    override fun onSettingsChanged(settings: Settings) {
        Persist.saveSettings(this, settings)
        sounds.enabled = settings.sounds
    }

    override fun onPickLibrary() {
        pickFolder.launch(null)
    }

    override fun onRescan() = rescan(quiet = false)

    /** The app drawer opened: fetch its icons in the background so the grid fills in as they arrive. */
    override fun onDrawerOpen() {
        lifecycleScope.launch {
            for (app in state.drawerApps) {
                if (state.appIcons.containsKey(app.pkg)) continue
                val icon = withContext(Dispatchers.IO) { Apps.icon(this@MainActivity, app.pkg, 192) } ?: continue
                state.appIcons[app.pkg] = icon
            }
        }
    }

    override fun onLaunchApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) { state.toast("That app can't be opened"); return }
        runCatching { startActivity(intent) }.onFailure { state.toast("That app wouldn't open") }
    }

    override fun listApps(): List<fyi.slot.ui.AppEntry> = Apps.list(this)

    override fun onAppKind(pkg: String, kind: fyi.slot.ui.AppKind) {
        Apps.saveKinds(this, state.appKinds)
        publish()
    }

    override fun onAction(action: GameAction) {}

    override fun sfx(effect: Sfx) = sounds.play(effect)

    // --- input -----------------------------------------------------------------------------------

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val pad = KeyMap.pad(e.keyCode, state.settings.swapFaceButtons) ?: return super.dispatchKeyEvent(e)
        // Let the system handle Back when we're sitting on the plain shelf (e.g. leave the app).
        if (e.keyCode == KeyEvent.KEYCODE_BACK && state.screen == Screen.Browse && state.overlay == fyi.slot.ui.Overlay.None) {
            return super.dispatchKeyEvent(e)
        }
        if (KeyMap.isGamepad(e.device)) state.gamepad = true
        when (e.action) {
            KeyEvent.ACTION_DOWN -> state.press(pad, e.repeatCount > 0, e.eventTime)
            KeyEvent.ACTION_UP -> state.release(pad, e.eventTime)
        }
        return true
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (axisNav.onMotion(ev)) {
            state.gamepad = true
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }
}
