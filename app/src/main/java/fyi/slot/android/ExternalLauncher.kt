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
        // Redream: same, it opens whatever string it is given as a path
        Emulator("redream", "io.recompiled.redream", "Redream") { _, path, uri ->
            Intent(Intent.ACTION_VIEW, Uri.parse(path)).setClassName("io.recompiled.redream", "io.recompiled.redream.MainActivity")
        },
    )


    private fun xendroid(pkg: String, game: Game, uri: Uri): Intent =
        Intent("xendroid.intent.action.xendroid").setClassName(pkg, "xendroid.compose.EmulatorHostActivity")
            .putExtra("game_uri", uri.toString()).putExtra("game_name", game.title)
            .putExtra("disc_paths", arrayOf(uri.toString())).putExtra("disc_labels", arrayOf(game.title))
            .grant(uri)
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
