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
    private class Emulator(val core: String, val pkg: String, val label: String, val build: (Game, String) -> Intent)

    private val emulators = listOf(
        // DuckStation: its (exported) EmulationActivity takes the path in "bootPath"
        Emulator("duckstation", "com.github.stenzek.duckstation", "DuckStation") { _, path ->
            Intent().setClassName("com.github.stenzek.duckstation", "com.github.stenzek.duckstation.EmulationActivity").putExtra("bootPath", path)
        },
        // AetherSX2: same interface
        Emulator("aethersx2", "xyz.aethersx2.android", "AetherSX2") { _, path ->
            Intent().setClassName("xyz.aethersx2.android", "xyz.aethersx2.android.EmulationActivity").putExtra("bootPath", path)
        },
        // M64Plus FZ: an "open this file" request, zips and all
        Emulator("mupen64fz", "org.mupen64plusae.v3.fzurita.pro", "M64Plus FZ Pro") { _, path ->
            Intent(Intent.ACTION_VIEW, Uri.fromFile(File(path)))
                .setClassName("org.mupen64plusae.v3.fzurita.pro", "paulscode.android.mupen64plusae.SplashActivity")
        },
        // Dolphin: an "open this file" request whose data is the plain path
        Emulator("dolphin", "org.dolphinemu.dolphinemu", "Dolphin") { _, path ->
            Intent(Intent.ACTION_VIEW, Uri.parse(path)).setClassName("org.dolphinemu.dolphinemu", "org.dolphinemu.dolphinemu.ui.main.MainActivity")
        },
        Emulator("dolphin_mmjr", "org.dolphinemu.mmjr", "Dolphin MMJR") { _, path ->
            Intent(Intent.ACTION_VIEW, Uri.parse(path)).setClassName("org.dolphinemu.mmjr", "org.dolphinemu.dolphinemu.ui.main.MainActivity")
        },
    )

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
        val path = pathOf(game) ?: return Result.Failed("Slot can't work out where ${game.title} is stored")
        val choices = candidates(game)
        for (c in choices) {
            val emu = emulators.firstOrNull { it.core == c.id } ?: continue
            if (!isInstalled(ctx, emu.pkg)) continue
            return runCatching {
                ctx.startActivity(emu.build(game, path))
                Result.Started(emu.label)
            }.getOrElse { Result.Failed("${emu.label} wouldn't open ${game.title}") }
        }
        val wanted = choices.firstOrNull()?.name ?: "an emulator"
        return Result.Failed("Install $wanted to play ${game.system.title} games")
    }
}
