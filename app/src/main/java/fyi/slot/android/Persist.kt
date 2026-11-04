package fyi.slot.android

import android.content.Context
import fyi.slot.ui.Game
import fyi.slot.ui.GameSystem
import fyi.slot.ui.Settings
import fyi.slot.ui.ShaderChoice
import fyi.slot.ui.TouchMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Tiny JSON persistence shared by the launcher and the game process (both read/write the same files,
 * so it always goes through atomic writes).
 */
object Persist {
    private fun file(ctx: Context, name: String) = File(ctx.filesDir, name)

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    // --- settings ---------------------------------------------------------------------------

    fun loadSettings(ctx: Context): Settings {
        val f = file(ctx, "settings.json")
        if (!f.exists()) return Settings()
        return runCatching {
            val o = JSONObject(f.readText())
            val d = Settings()
            fun <E : Enum<E>> enumOf(key: String, values: Array<E>, default: E) =
                values.firstOrNull { it.name == o.optString(key) } ?: default
            Settings(
                gbaShader = enumOf("gbaShader", ShaderChoice.entries.toTypedArray(), d.gbaShader),
                gbShader = enumOf("gbShader", ShaderChoice.entries.toTypedArray(), d.gbShader),
                otherShader = enumOf("otherShader", ShaderChoice.entries.toTypedArray(), d.otherShader),
                colorCorrection = o.optBoolean("colorCorrection", d.colorCorrection),
                portraitScreenStep = o.optInt("portraitScreenStep", d.portraitScreenStep).coerceIn(0, 5),
                touchTweaks = o.optJSONObject("touchLayout")?.let { m -> m.keys().asSequence().associateWith { k -> m.getJSONArray(k).let { a -> List(a.length()) { a.getDouble(it).toFloat() } } } } ?: d.touchTweaks,
                gbPalette = o.optString("gbPalette", d.gbPalette).takeIf { it in fyi.slot.ui.GB_PALETTES } ?: d.gbPalette,
                fastForwardSpeed = o.optInt("fastForwardSpeed", d.fastForwardSpeed),
                muteFastForward = o.optBoolean("muteFastForward", d.muteFastForward),
                rewind = o.optBoolean("rewind", d.rewind),
                turbo = o.optBoolean("turbo", d.turbo),
                rumble = o.optBoolean("rumble", d.rumble),
                autoSaveOnEject = o.optBoolean("autoSaveOnEject", d.autoSaveOnEject),
                showTouchControls = enumOf("showTouchControls", TouchMode.entries.toTypedArray(), d.showTouchControls),
                sounds = o.optBoolean("sounds", d.sounds),
                swapFaceButtons = o.optBoolean("swapFaceButtons", d.swapFaceButtons),
                downloadCovers = o.optBoolean("downloadCovers", d.downloadCovers),
                cartSize = enumOf("cartSize", fyi.slot.ui.CartSize.entries.toTypedArray(), d.cartSize),
                shelfList = o.optBoolean("shelfList", d.shelfList),
                listSize = enumOf("listSize", fyi.slot.ui.ListSize.entries.toTypedArray(), d.listSize),
                menuSize = enumOf("menuSize", fyi.slot.ui.ListSize.entries.toTypedArray(), d.menuSize),
                uiScale = enumOf("uiScale", fyi.slot.ui.UiScale.entries.toTypedArray(), d.uiScale),
                drawerGrid = o.optBoolean("drawerGrid", d.drawerGrid),
                drawerSize = enumOf("drawerSize", fyi.slot.ui.DrawerSize.entries.toTypedArray(), d.drawerSize),
                brightness = o.optDouble("brightness", d.brightness.toDouble()).toFloat(),
                defaultCores = o.optJSONObject("defaultCores")?.let { m -> m.keys().asSequence().associateWith { m.getString(it) } } ?: d.defaultCores,
            )
        }.getOrDefault(Settings())
    }

    fun saveSettings(ctx: Context, s: Settings) {
        val o = JSONObject()
            .put("gbaShader", s.gbaShader.name).put("gbShader", s.gbShader.name).put("otherShader", s.otherShader.name)
            .put("colorCorrection", s.colorCorrection).put("gbPalette", s.gbPalette).put("portraitScreenStep", s.portraitScreenStep).put("touchLayout", JSONObject(s.touchTweaks.mapValues { JSONArray(it.value.map { v -> v.toDouble() }) }))
            .put("fastForwardSpeed", s.fastForwardSpeed).put("muteFastForward", s.muteFastForward)
            .put("rewind", s.rewind).put("turbo", s.turbo).put("rumble", s.rumble)
            .put("autoSaveOnEject", s.autoSaveOnEject).put("showTouchControls", s.showTouchControls.name)
            .put("sounds", s.sounds).put("swapFaceButtons", s.swapFaceButtons)
            .put("downloadCovers", s.downloadCovers).put("brightness", s.brightness.toDouble())
            .put("cartSize", s.cartSize.name).put("drawerGrid", s.drawerGrid).put("drawerSize", s.drawerSize.name).put("shelfList", s.shelfList).put("listSize", s.listSize.name).put("menuSize", s.menuSize.name).put("uiScale", s.uiScale.name)
            .put("defaultCores", JSONObject(s.defaultCores))
        writeAtomic(file(ctx, "settings.json"), o.toString())
    }

    // --- library roots (SAF tree URIs) ----------------------------------------------------------

    fun loadRoots(ctx: Context): List<String> {
        val f = file(ctx, "roots.json")
        if (!f.exists()) return emptyList()
        return runCatching { JSONArray(f.readText()).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList())
    }

    fun saveRoots(ctx: Context, roots: List<String>) = writeAtomic(file(ctx, "roots.json"), JSONArray(roots).toString())

    // --- library cache ---------------------------------------------------------------------------

    fun loadLibrary(ctx: Context): List<Game> {
        val f = file(ctx, "library.json")
        if (!f.exists()) return emptyList()
        val meta = loadMeta(ctx)
        return runCatching {
            val a = JSONArray(f.readText())
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                val sys = GameSystem.byId(o.getString("system")) ?: return@List null
                val id = o.getString("id")
                Game(
                    id = id, stem = o.getString("stem"), system = sys, uri = o.getString("uri"),
                    size = o.optLong("size"), code = o.optString("code"), gbTitle = o.optString("gbTitle"),
                    cgbFlag = o.optInt("cgb", -1), japan = o.optBoolean("japan"),
                    entryName = o.optString("entry"),
                    coreId = meta[id]?.let { m -> m.core.takeIf { isPinned(m, sys) } }, corePinned = meta[id]?.let { m -> isPinned(m, sys) } ?: false,
                    lastPlayed = meta[id]?.played ?: 0L,
                    hasSave = StateStore.hasAuto(ctx, id),
                )
            }.filterNotNull()
        }.getOrDefault(emptyList())
    }

    fun saveLibrary(ctx: Context, games: List<Game>) {
        val a = JSONArray()
        for (g in games) a.put(
            JSONObject().put("id", g.id).put("stem", g.stem).put("system", g.system.id).put("uri", g.uri)
                .put("size", g.size).put("code", g.code).put("gbTitle", g.gbTitle).put("cgb", g.cgbFlag)
                .put("japan", g.japan).put("entry", g.entryName),
        )
        writeAtomic(file(ctx, "library.json"), a.toString())
    }

    // --- per-game metadata ---------------------------------------------------------------------------

    /** [pinned] is null in files written before the default-emulator setting existed. */
    class Meta(val core: String?, val played: Long, val pinned: Boolean? = null)

    /** A game keeps its own core only if it was picked for it. Older files can't say, so a core other than the system's first is taken as a pick. */
    private fun isPinned(m: Meta, sys: GameSystem): Boolean = m.core != null && (m.pinned ?: (m.core != sys.cores.first().id))

    private fun loadMeta(ctx: Context): Map<String, Meta> {
        val f = file(ctx, "meta.json")
        if (!f.exists()) return emptyMap()
        return runCatching {
            val o = JSONObject(f.readText())
            o.keys().asSequence().associateWith { k ->
                val m = o.getJSONObject(k)
                Meta(m.optString("core").ifEmpty { null }, m.optLong("played"), if (m.has("pinned")) m.optBoolean("pinned") else null)
            }
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    fun updateMeta(ctx: Context, gameId: String, core: String? = null, played: Long? = null, pinned: Boolean? = null) {
        val cur = loadMeta(ctx).toMutableMap()
        val old = cur[gameId]
        cur[gameId] = Meta(core ?: old?.core, played ?: old?.played ?: 0L, pinned ?: old?.pinned)
        val o = JSONObject()
        for ((k, v) in cur) o.put(k, JSONObject().put("core", v.core ?: "").put("played", v.played).apply { v.pinned?.let { put("pinned", it) } })
        writeAtomic(file(ctx, "meta.json"), o.toString())
    }
}
