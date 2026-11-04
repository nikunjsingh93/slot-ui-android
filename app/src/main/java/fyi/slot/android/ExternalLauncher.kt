package fyi.slot.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import fyi.slot.ui.CoreChoice
import fyi.slot.ui.Game
import java.io.File

/**
 * Systems slot doesn't emulate itself (PlayStation, PS2, N64, GameCube) are handed to the emulator app you
 * already have installed. Each app has its own way of being told which game to boot; these are the ones that
 * were verified on a real handheld.
 */
object ExternalLauncher {
    private class Emulator(val core: String, val pkg: String, val label: String, val build: (Game, String, Uri) -> Intent)

    private val emulators = listOf(
        // DuckStation: its (exported) EmulationActivity takes the path in "bootPath"
        Emulator("duckstation", "com.github.stenzek.duckstation", "DuckStation") { _, path, uri ->
            Intent().setClassName("com.github.stenzek.duckstation", "com.github.stenzek.duckstation.EmulationActivity").putExtra("bootPath", uri.toString()).grant(uri)
        },
        // AetherSX2: same interface
        Emulator("aethersx2", "xyz.aethersx2.android", "AetherSX2") { _, path, uri ->
            Intent().setClassName("xyz.aethersx2.android", "xyz.aethersx2.android.EmulationActivity").putExtra("bootPath", uri.toString()).grant(uri)
        },
        // ARMSX2: an "open this file" request on its splash screen, with the same document grant
        Emulator("armsx2", "com.armsx2", "ARMSX2") { _, _, uri ->
            Intent(Intent.ACTION_VIEW).setClassName("com.armsx2", "com.armsx2.BootSplashActivity").grant(uri)
        },
        // ARMSX3 (PS3, same team and same "open this file" splash screen as ARMSX2); Play Store builds use their own id
        Emulator("armsx3", "com.armsx3", "ARMSX3") { _, _, uri ->
            Intent(Intent.ACTION_VIEW).setClassName("com.armsx3", "com.armsx2.BootSplashActivity").grant(uri)
        },
        Emulator("armsx3", "com.armsx3.play", "ARMSX3") { _, _, uri ->
            Intent(Intent.ACTION_VIEW).setClassName("com.armsx3.play", "com.armsx2.BootSplashActivity").grant(uri)
        },
        // hakuX (original Xbox): its launcher boots the disc image it is handed as intent data
        Emulator("hakux", "com.rfandango.haku_x", "hakuX") { _, _, uri ->
            Intent(Intent.ACTION_VIEW).setClassName("com.rfandango.haku_x", "com.rfandango.haku_x.LauncherActivity").grant(uri)
        },
        // Xendroid+ (Xbox 360): its emulator host takes the game in "game_uri"; release builds carry a ".fork" suffix
        Emulator("xendroid", "xendroid.compose.fork", "Xendroid+") { g, _, uri -> xendroid("xendroid.compose.fork", g, uri) },
        Emulator("xendroid", "xendroid.compose", "Xendroid+") { g, _, uri -> xendroid("xendroid.compose", g, uri) },
        // RetroArch: its game activity takes the ROM path, the core library and the config file as extras. The core is
        // the one slot itself bundles for the system, so it must be downloaded in RetroArch once (Online Updater).
        Emulator("retroarch", "com.retroarch.aarch64", "RetroArch") { g, path, _ -> retroarch("com.retroarch.aarch64", g, path) },
        Emulator("retroarch", "com.retroarch", "RetroArch") { g, path, _ -> retroarch("com.retroarch", g, path) },
        // M64Plus FZ: an "open this file" request, zips and all
        Emulator("mupen64fz", "org.mupen64plusae.v3.fzurita.pro", "M64Plus FZ Pro") { _, path, uri ->
            Intent(Intent.ACTION_VIEW, Uri.fromFile(File(path)))
                .setClassName("org.mupen64plusae.v3.fzurita.pro", "paulscode.android.mupen64plusae.SplashActivity")
        },
        // Dolphin / MMJR: MainActivity starts the game named in the "AutoStartFile" extra (older MMJR builds ignore the VIEW data and just show their menu)
        Emulator("dolphin", "org.dolphinemu.dolphinemu", "Dolphin") { _, path, uri ->
            Intent(Intent.ACTION_VIEW, Uri.parse(path)).setClassName("org.dolphinemu.dolphinemu", "org.dolphinemu.dolphinemu.ui.main.MainActivity").putExtra("AutoStartFile", path)
        },
        Emulator("dolphin_mmjr", "org.dolphinemu.mmjr", "Dolphin MMJR") { _, path, uri ->
            Intent(Intent.ACTION_VIEW, Uri.parse(path)).setClassName("org.dolphinemu.mmjr", "org.dolphinemu.dolphinemu.ui.main.MainActivity").putExtra("AutoStartFile", path)
        },
        // PPSSPP: an "open this file" request with the plain path
        Emulator("ppsspp_app", "org.ppsspp.ppsspp", "PPSSPP") { _, path, uri ->
            Intent(Intent.ACTION_VIEW, Uri.parse(path)).setClassName("org.ppsspp.ppsspp", "org.ppsspp.ppsspp.PpssppActivity")
        },
        // 3DS, Azahar and the Citra family: the game as the data of an "open" request on its EmulationActivity (with the
        // document grant), on a fresh task; Azahar's Play Store build still carries Lime3DS's id
        Emulator("azahar", "org.azahar_emu.azahar", "Azahar") { _, _, uri -> citraLike("org.azahar_emu.azahar", "org.citra.citra_emu.activities.EmulationActivity", uri) },
        Emulator("azahar", "io.github.lime3ds.android", "Azahar") { _, _, uri -> citraLike("io.github.lime3ds.android", "org.citra.citra_emu.activities.EmulationActivity", uri) },
        Emulator("azaharplus", "io.github.azaharplus.android", "AzaharPlus") { _, _, uri -> citraLike("io.github.azaharplus.android", "org.citra.citra_emu.activities.EmulationActivity", uri) },
        Emulator("mandarine", "io.github.mandarine3ds.mandarine", "Mandarine") { _, _, uri -> citraLike("io.github.mandarine3ds.mandarine", "io.github.mandarine3ds.mandarine.activities.EmulationActivity", uri) },
        // Citra MMJ: the file path in "GamePath"
        Emulator("citra_mmj", "org.citra.emu", "Citra MMJ") { _, path, _ ->
            Intent().setClassName("org.citra.emu", "org.citra.emu.ui.EmulationActivity").putExtra("GamePath", path)
        },
        // DS, melonDS: its LAUNCH_ROM action with the document in "uri"
        Emulator("melonds_app", "me.magnum.melonds", "melonDS") { _, _, uri ->
            Intent("me.magnum.melonds.LAUNCH_ROM").setClassName("me.magnum.melonds", "me.magnum.melonds.ui.emulator.EmulatorActivity")
                .putExtra("uri", uri.toString()).grant(uri)
        },
        Emulator("melonds_app", "me.magnum.melonds.nightly", "melonDS") { _, _, uri ->
            Intent("me.magnum.melonds.nightly.LAUNCH_ROM").setClassName("me.magnum.melonds.nightly", "me.magnum.melonds.ui.emulator.EmulatorActivity")
                .putExtra("uri", uri.toString()).grant(uri)
        },
        // DraStic: an "open" request with the document, on a fresh task
        Emulator("drastic", "com.dsemu.drastic", "DraStic") { _, _, uri -> citraLike("com.dsemu.drastic", "com.dsemu.drastic.DraSticActivity", uri) },
        // Switch, Eden (yuzu's successor): the game as the data of the request yuzu's EmulationActivity listens for
        Emulator("eden", "dev.eden.eden_emulator", "Eden") { _, _, uri -> eden("dev.eden.eden_emulator", uri) },
        Emulator("eden", "dev.eden.eden_emulator.nightly", "Eden") { _, _, uri -> eden("dev.eden.eden_emulator.nightly", uri) },
        Emulator("eden", "dev.legacy.eden_emulator", "Eden") { _, _, uri -> eden("dev.legacy.eden_emulator", uri) },
        // Kenji-NX: its LAUNCH_GAME action with the document in "bootPath"
        Emulator("kenjinx", "org.kenjinx.android", "Kenji-NX") { _, _, uri ->
            Intent("org.kenjinx.android.LAUNCH_GAME").setClassName("org.kenjinx.android", "org.kenjinx.android.MainActivity")
                .putExtra("bootPath", uri.toString()).grant(uri)
        },
        // Redream: same, it opens whatever string it is given as a path
        Emulator("redream", "io.recompiled.redream", "Redream") { _, path, uri ->
            Intent(Intent.ACTION_VIEW, Uri.parse(path)).setClassName("io.recompiled.redream", "io.recompiled.redream.MainActivity")
        },
    )


    /** An "open this game" request on [activity] with the document granted, replacing whatever that app had open. */
    private fun citraLike(pkg: String, activity: String, uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).setClassName(pkg, activity).grant(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun eden(pkg: String, uri: Uri): Intent =
        Intent("android.nfc.action.TECH_DISCOVERED").setClassName(pkg, "org.yuzu.yuzu_emu.activities.EmulationActivity").grant(uri)

    private fun xendroid(pkg: String, game: Game, uri: Uri): Intent =
        Intent("xendroid.intent.action.xendroid").setClassName(pkg, "xendroid.compose.EmulatorHostActivity")
            .putExtra("game_uri", uri.toString()).putExtra("game_name", game.title)
            .putExtra("disc_paths", arrayOf(uri.toString())).putExtra("disc_labels", arrayOf(game.title))
            .grant(uri)

    private fun retroarch(pkg: String, game: Game, path: String): Intent {
        val core = game.system.cores.firstOrNull { !it.external }?.id ?: "mgba"
        return Intent(Intent.ACTION_MAIN).setClassName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            .putExtra("ROM", path)
            .putExtra("LIBRETRO", "/data/data/$pkg/cores/${core}_libretro_android.so")
            .putExtra("CONFIGFILE", "/storage/emulated/0/Android/data/$pkg/files/retroarch.cfg")
            .putExtra("QUITFOCUS", "")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    /** Lets the emulator read this document through the storage access framework, as the folder you picked allows slot to. */
    private fun Intent.grant(uri: Uri): Intent = apply {
        data = uri
        clipData = android.content.ClipData.newRawUri("game", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    sealed interface Result {
        data class Started(val label: String) : Result
        data class Failed(val message: String) : Result
    }

    fun isInstalled(ctx: Context, pkg: String) = runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /** A real file path for a game found through the storage access framework (what the emulators want). */
    fun pathOf(game: Game): String? {
        val uri = Uri.parse(game.uri)
        if (uri.scheme == "file") return uri.path
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val volume = docId.substringBefore(':')
        val rest = docId.substringAfter(':', "")
        if (rest.isEmpty()) return null
        return if (volume.equals("primary", true)) "/storage/emulated/0/$rest" else "/storage/$volume/$rest"
    }

    /** Emulators to try, the game's chosen one first, then the system's other options. */
    private fun candidates(game: Game): List<CoreChoice> {
        val chosen = game.system.cores.firstOrNull { it.id == game.coreId }
        return listOfNotNull(chosen) + game.system.cores.filter { it !== chosen }
    }

    fun launch(ctx: Context, game: Game): Result {
        if (Apps.isApp(game)) {
            val pkg = Apps.packageOf(game)
            val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return Result.Failed("${game.title} isn't installed any more")
            return runCatching { ctx.startActivity(intent); Result.Started(game.title) }.getOrElse { Result.Failed("${game.title} wouldn't open") }
        }
        val path = pathOf(game) ?: return Result.Failed("Slot can't work out where ${game.title} is stored")
        val choices = candidates(game)
        for (c in choices) {
            val emu = emulators.firstOrNull { it.core == c.id && isInstalled(ctx, it.pkg) } ?: continue
            if (!isInstalled(ctx, emu.pkg)) continue
            return runCatching {
                ctx.startActivity(emu.build(game, path, Uri.parse(game.uri)))
                Result.Started(emu.label)
            }.getOrElse { Result.Failed("${emu.label} wouldn't open ${game.title}") }
        }
        val wanted = choices.firstOrNull()?.name ?: "an emulator"
        return Result.Failed("Install $wanted to play ${game.system.title} games")
    }
}
