package fyi.slot.android

import android.content.Context
import fyi.slot.ui.Game
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Moves game saves between devices as one zip. Game ids differ per device (they hash the file location), so entries
 * are named by system and game name instead:
 *   saves/<system>/<game>.srm            the game's own (battery) save
 *   states/<system>/<game>/slotN.state   save states, with slotN.png screenshots
 * Save states only load in the same core, so they are most useful between devices running the same version of slot.
 */
object SaveTransfer {
    class Result(val games: Int, val files: Int, val skipped: Int)

    private fun safe(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    private fun srm(ctx: Context, g: Game) = File(ctx.filesDir, "saves/${g.id}.srm")
    private fun stateDir(ctx: Context, g: Game) = File(ctx.filesDir, "states/${g.id}")

    fun hasSave(ctx: Context, g: Game) = srm(ctx, g).exists() || stateDir(ctx, g).listFiles { f -> f.name.startsWith("slot") }?.isNotEmpty() == true

    private fun ZipOutputStream.put(name: String, f: File) {
        putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(this) }; closeEntry()
    }

    private fun write(ctx: Context, games: List<Game>, out: OutputStream): Result {
        var files = 0
        var count = 0
        ZipOutputStream(out).use { z ->
            for (g in games) {
                var any = false
                val s = srm(ctx, g)
                if (s.exists()) { z.put("saves/${g.system.id}/${safe(g.stem)}.srm", s); files++; any = true }
                stateDir(ctx, g).listFiles()?.filter { it.name.startsWith("slot") }?.forEach {
                    z.put("states/${g.system.id}/${safe(g.stem)}/${it.name}", it); files++; any = true
                }
                if (any) count++
            }
        }
        return Result(count, files, 0)
    }

    /** Every emulated game that has a save. */
    fun exportAll(ctx: Context, games: List<Game>, out: OutputStream) = write(ctx, games.filter { !it.external }, out)

    fun exportGame(ctx: Context, game: Game, out: OutputStream) = write(ctx, listOf(game), out)

    private class Item(val system: String, val stem: String, val kind: String, val file: String, val bytes: ByteArray)

    private fun read(input: InputStream): List<Item> {
        val items = ArrayList<Item>()
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val p = e.name.split('/')
                val bytes = z.readBytes()
                if (p.size == 3 && p[0] == "saves" && p[2].endsWith(".srm")) items += Item(p[1], p[2].removeSuffix(".srm"), "srm", "", bytes)
                else if (p.size == 4 && p[0] == "states" && p[3].matches(Regex("slot\\d+\\.(state|png)"))) items += Item(p[1], p[2], "state", p[3], bytes)
            }
        }
        return items
    }

    private fun apply(ctx: Context, g: Game, item: Item) {
        if (item.kind == "srm") {
            val f = srm(ctx, g)
            f.parentFile?.mkdirs()
            if (f.exists()) f.copyTo(File(f.parentFile, g.id + ".srm.bak"), overwrite = true)
            f.writeBytes(item.bytes)
        } else {
            val d = stateDir(ctx, g).also { it.mkdirs() }
            File(d, item.file).writeBytes(item.bytes)
        }
    }

    /** Puts a whole export back: each save goes to the game with the same system and name on this device. */
    fun importAll(ctx: Context, games: List<Game>, input: InputStream): Result {
        val byKey = games.filter { !it.external }.associateBy { it.system.id + "/" + safe(it.stem) }
        val matched = HashSet<String>()
        var files = 0
        var skipped = 0
        for (item in read(input)) {
            val g = byKey[item.system + "/" + safe(item.stem)]
            if (g == null) { skipped++; continue }
            apply(ctx, g, item); files++; matched += g.id
        }
        return Result(matched.size, files, skipped)
    }

    /** Puts one game's save into [game], whatever the file inside is called (the game may be named differently here). */
    fun importGame(ctx: Context, game: Game, input: InputStream): Result {
        val items = read(input)
        // a zip of several games: use only the one that matches by name, otherwise a single game's content as is
        val names = items.map { it.system + "/" + it.stem }.distinct()
        val use = if (names.size <= 1) items else items.filter { it.system == game.system.id && safe(it.stem) == safe(game.stem) }
        for (item in use) apply(ctx, game, item)
        return Result(if (use.isEmpty()) 0 else 1, use.size, items.size - use.size)
    }
}
