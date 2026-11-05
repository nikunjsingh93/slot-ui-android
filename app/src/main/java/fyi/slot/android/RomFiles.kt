package fyi.slot.android

import android.content.Context
import android.net.Uri
import fyi.slot.ui.Game
import java.io.File
import java.util.zip.ZipInputStream

/** Copies a ROM out of the SAF tree (and out of any zip) into a real file the libretro core can open. */
object RomFiles {
    fun materialize(ctx: Context, game: Game): File {
        // an N64 or DS game kept in a zip: the core wants the bare ROM, so unpack the first ROM inside it
        val direct = ExternalLauncher.pathOf(game)?.let(::File)?.takeIf { it.canRead() }
        if (game.entryName.isEmpty() && direct != null && direct.extension.equals("zip", true)) {
            val dir = File(ctx.cacheDir, "rom").also { it.mkdirs() }
            dir.listFiles()?.filter { !it.name.startsWith(game.id) }?.sortedByDescending { it.lastModified() }?.drop(1)?.forEach { it.delete() }
            ZipInputStream(direct.inputStream().buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val ext = e.name.substringAfterLast('.', "").lowercase()
                    if (!e.isDirectory && ext in setOf("z64", "n64", "v64", "ndd", "bin", "nds", "dsi")) {
                        val out = File(dir, "${game.id}.$ext")
                        if (!out.exists() || out.length() == 0L) out.outputStream().use { z.copyTo(it) }
                        return out
                    }
                }
            }
            error("No ROM found inside ${direct.name}")
        }
        // disc images and N64 files are used where they are (cue sheets need the files beside them, images can be GBs)
        if (game.entryName.isEmpty() && game.system.external) {
            ExternalLauncher.pathOf(game)?.let(::File)?.takeIf { it.canRead() }?.let { return it }
        }
        val dir = File(ctx.cacheDir, "rom").also { it.mkdirs() }
        val ext = (game.entryName.ifEmpty { Uri.parse(game.uri).lastPathSegment ?: "" }).substringAfterLast('.', game.system.extensions.first())
        val out = File(dir, "${game.id}.$ext")
        // Keep only the most recent couple of ROMs around.
        dir.listFiles()?.filter { it != out }?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }
        if (out.exists() && out.length() > 0 && (game.size == 0L || game.entryName.isNotEmpty() || out.length() == game.size)) return out

        val tmp = File(dir, "${game.id}.tmp")
        val input = ctx.contentResolver.openInputStream(Uri.parse(game.uri)) ?: error("Can't open ${game.stem}")
        input.use { raw ->
            if (game.entryName.isNotEmpty()) {
                ZipInputStream(raw.buffered()).use { zin ->
                    while (true) {
                        val e = zin.nextEntry ?: error("ROM missing from zip")
                        if (e.name == game.entryName) {
                            tmp.outputStream().use { zin.copyTo(it) }
                            break
                        }
                    }
                }
            } else {
                tmp.outputStream().use { raw.copyTo(it) }
            }
        }
        out.delete()
        tmp.renameTo(out)
        return out
    }
}
