package fyi.slot.android

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import fyi.slot.ui.AppEntry
import fyi.slot.ui.AppKind
import fyi.slot.ui.Game
import fyi.slot.ui.GameSystem
import org.json.JSONObject
import java.io.File

/**
 * Android apps and games shown as cartridges. Nothing is shown until you pick it in
 * Settings > Apps & games: an app goes on the Apps shelf, a game on Installed.
 */
object Apps {
    const val PREFIX = "app:"

    fun isApp(game: Game) = game.uri.startsWith(PREFIX)
    fun packageOf(game: Game) = game.uri.removePrefix(PREFIX)

    /** Every app with a launcher icon (except slot itself), alphabetical. */
    fun list(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != ctx.packageName }
            .distinctBy { it.packageName }
            .map { ai -> AppEntry(ai.packageName, ai.loadLabel(pm).toString(), looksLikeGame(ai), canUninstall(ai)) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun looksLikeGame(ai: ApplicationInfo): Boolean =
        (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
            (Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME)

    /** Ordinary apps can be removed; system apps (unless they have a user-installed update) cannot. */
    private fun canUninstall(ai: ApplicationInfo): Boolean =
        (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    fun label(ctx: Context, pkg: String): String? = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    }.getOrNull()

    /** The cartridges for the apps the user chose, skipping any that have since been uninstalled. */
    fun games(ctx: Context, kinds: Map<String, AppKind>): List<Game> = kinds.mapNotNull { (pkg, kind) ->
        val system = when (kind) {
            AppKind.App -> GameSystem.APPS
            AppKind.Game -> GameSystem.INSTALLED
            AppKind.Hidden -> return@mapNotNull null
        }
        val name = label(ctx, pkg) ?: return@mapNotNull null
        Game(id = "app_$pkg", stem = name, system = system, uri = PREFIX + pkg)
    }

    /** The app's launcher icon, drawn into a bitmap the cart label can show. */
    fun icon(ctx: Context, pkg: String, size: Int = 512): ImageBitmap? = runCatching {
        val d = ctx.packageManager.getApplicationIcon(pkg)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        bmp.asImageBitmap()
    }.getOrNull()

    // --- persistence -----------------------------------------------------------------------------

    private fun file(ctx: Context) = File(ctx.filesDir, "apps.json")

    fun loadKinds(ctx: Context): Map<String, AppKind> {
        val f = file(ctx)
        if (!f.exists()) return emptyMap()
        return runCatching {
            val o = JSONObject(f.readText())
            o.keys().asSequence().mapNotNull { k ->
                AppKind.entries.firstOrNull { it.name == o.optString(k) }?.takeIf { it != AppKind.Hidden }?.let { k to it }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    fun saveKinds(ctx: Context, kinds: Map<String, AppKind>) {
        val o = JSONObject()
        for ((pkg, kind) in kinds) if (kind != AppKind.Hidden) o.put(pkg, kind.name)
        file(ctx).writeText(o.toString())
    }
}
