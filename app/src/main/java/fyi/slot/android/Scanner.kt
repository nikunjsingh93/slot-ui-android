package fyi.slot.android

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import fyi.slot.ui.Game
import fyi.slot.ui.GameSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/** Walks the SAF folders the user picked and turns ROM files into [Game]s (reading just the header). */
class Scanner(private val ctx: Context) {
    /** [system] is pre-assigned for games found in a folder named after a system that is run by an external emulator. */
    private class Found(val docUri: Uri, val name: String, val size: Long, val parentName: String, val system: GameSystem? = null)

    /** Disc images ship as .bin + .cue pairs; only list the .cue. Returns true when [name] should be skipped. */
    private fun shadowedBin(name: String, cueStems: Set<String>): Boolean =
        name.substringAfterLast('.', "").lowercase() == "bin" && name.substringBeforeLast('.').lowercase() in cueStems

    /** The system a file belongs to when it sits under a folder like `PSX/` or `N64/`. */
    private fun assigned(sys: GameSystem?, ext: String): GameSystem? = sys?.takeIf { it.external && ext in it.extensions }

    suspend fun scan(roots: List<Uri>, localRoot: java.io.File? = null, onBatch: ((List<Game>) -> Unit)? = null): List<Game> = withContext(Dispatchers.IO) {
        val found = ArrayList<Found>()
        for (root in roots) {
            val rootId = runCatching { DocumentsContract.getTreeDocumentId(root) }.getOrNull() ?: continue
            // the picked folder itself may be a system folder, e.g. ".../Games/PSX"
            val rootName = rootId.substringAfterLast('/').substringAfterLast(':')
            walk(root, rootId, rootName, 0, found, GameSystem.byFolder(rootName))
        }
        localRoot?.let { walkLocal(it, 0, found, null) }
        val seen = HashSet<String>()
        val out = ArrayList<Game>(found.size)
        for (f in found) {
            coroutineContext.ensureActive()
            val g = runCatching { toGame(f) }.getOrNull() ?: continue
            if (seen.add(g.id)) { out += g; if (out.size % 25 == 0) onBatch?.invoke(out.toList()) }
        }
        out
    }

    private suspend fun walk(tree: Uri, docId: String, name: String, depth: Int, out: MutableList<Found>, sys: GameSystem?) {
        if (depth > 6) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
        )
        val dirs = ArrayList<Pair<String, String>>()
        class Entry(val id: String, val name: String, val size: Long)
        val files = ArrayList<Entry>()
        ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                coroutineContext.ensureActive()
                val id = c.getString(0)
                val n = c.getString(1) ?: continue
                val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!n.startsWith(".") && n.lowercase() !in SKIP_DIRS) dirs += id to n
                } else files += Entry(id, n, c.getLong(3))
            }
        }
        val cueStems = files.filter { it.name.lowercase().endsWith(".cue") }.map { it.name.substringBeforeLast('.').lowercase() }.toSet()
        for (f in files) {
            val ext = f.name.substringAfterLast('.', "").lowercase()
            val s = assigned(sys, ext)
            if (f.name.startsWith(".") || isBios(f.name)) continue
            val listed = s != null || ext == "zip" || GameSystem.byExtension(ext) != null || (ext == "bin" && guessBinSystem(name) != null)
            if (!listed || shadowedBin(f.name, cueStems)) continue
            out += Found(DocumentsContract.buildDocumentUriUsingTree(tree, f.id), f.name, f.size, name, s)
        }
        for ((id, n) in dirs) walk(tree, id, n, depth + 1, out, GameSystem.byFolder(n) ?: sys)
    }

    private fun walkLocal(dir: java.io.File, depth: Int, out: MutableList<Found>, inherited: GameSystem?) {
        if (depth > 6) return
        val sys = GameSystem.byFolder(dir.name) ?: inherited
        val all = dir.listFiles() ?: return
        val cueStems = all.filter { it.extension.lowercase() == "cue" }.map { it.nameWithoutExtension.lowercase() }.toSet()
        for (f in all) {
            if (f.isDirectory) { if (!f.name.startsWith(".")) walkLocal(f, depth + 1, out, sys); continue }
            if (f.name.startsWith(".") || isBios(f.name)) continue
            val ext = f.extension.lowercase()
            val s = assigned(sys, ext)
            if ((s != null || ext == "zip" || GameSystem.byExtension(ext) != null || (ext == "bin" && guessBinSystem(dir.name) != null)) && !shadowedBin(f.name, cueStems)) {
                out += Found(Uri.fromFile(f), f.name, f.length(), dir.name, s)
            }
        }
    }

    private fun guessBinSystem(folder: String): GameSystem? {
        return if (GameSystem.byFolder(folder) == GameSystem.GENESIS) GameSystem.GENESIS else null
    }

    private fun toGame(f: Found): Game? {
        val ext = f.name.substringAfterLast('.', "").lowercase()
        val stem = f.name.substringBeforeLast('.')
        f.system?.let { s ->
            // run by an external emulator: nothing to read from the file, and zips are passed along untouched
            return Game(id = s.id + "_" + sha1(f.docUri.toString()).take(12), stem = stem, system = s, uri = f.docUri.toString(), size = f.size)
        }
        var entry = ""
        var system: GameSystem?
        val header = ByteArray(0x150)
        var read = 0
        if (ext == "zip") {
            ctx.contentResolver.openInputStream(f.docUri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zin ->
                    while (true) {
                        val e = zin.nextEntry ?: break
                        val en = e.name.substringAfterLast('/')
                        val ee = en.substringAfterLast('.', "").lowercase()
                        if (!e.isDirectory && GameSystem.byExtension(ee) != null) {
                            entry = e.name
                            read = zin.readUpTo(header)
                            break
                        }
                    }
                }
            }
            if (entry.isEmpty()) return null
            system = GameSystem.byExtension(entry.substringAfterLast('.', ""))
        } else {
            system = GameSystem.byExtension(ext) ?: if (ext == "bin") guessBinSystem(f.parentName) else null
            if (system == null) return null
            ctx.contentResolver.openInputStream(f.docUri)?.use { read = it.readUpTo(header) }
        }
        system ?: return null

        var code = ""
        var gbTitle = ""
        var cgb = -1
        var japan = false
        when (system) {
            GameSystem.GBA -> if (read >= 0xB0) code = ascii(header, 0xAC, 4).takeIf(::isCode) ?: ""
            GameSystem.GB, GameSystem.GBC -> if (read >= 0x150) {
                code = ascii(header, 0x13F, 4).takeIf(::isCode) ?: ""
                gbTitle = ascii(header, 0x134, 11).trim('\u0000', ' ')
                cgb = header[0x143].toInt() and 0xff
                japan = header[0x14A].toInt() == 0
                // A colour-only cartridge is a GBC game even when the file is named .gb.
                if (cgb == 0xC0) system = GameSystem.GBC
            }
            else -> {}
        }
        return Game(
            id = system.id + "_" + sha1(f.docUri.toString()).take(12),
            stem = stem, system = system, uri = f.docUri.toString(), size = f.size,
            code = code, gbTitle = gbTitle, cgbFlag = cgb, japan = japan, entryName = entry,
        )
    }

    private fun isCode(s: String) = s.length == 4 && s.all { it.isUpperCase() || it.isDigit() }
    private fun ascii(b: ByteArray, off: Int, n: Int) = String(CharArray(n) { (b[off + it].toInt() and 0x7f).toChar() })

    private fun InputStream.readUpTo(buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    companion object {
        /** BIOS and firmware images that sit beside the games (scph5501.bin, dc_boot.bin, gba_bios.bin...) are not games. */
        private val BIOS_STEM = Regex("""scph[-_ ]?\d+[a-z]?|(.*[-_ (])?bios([-_ ).].*)?|dc_(boot|flash|nvmem)|mcpx.*""", RegexOption.IGNORE_CASE)
        fun isBios(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in setOf("bin", "rom", "mec", "nvm", "img") && BIOS_STEM.matches(name.substringBeforeLast('.'))
        }

        private val SKIP_DIRS = setOf(
            "labels", "media", "bios", "saves", "states", "images", "boxart", "snaps", "thumbnails", "manuals",
            // systems slot does not emulate: don't waste time opening their (often huge) zip collections
            "ps3", "psv", "vita", "wii", "wiiu", "3ds", "nds", "ds",
            "saturn", "xbox", "windows", "1windows", "pc", "dos", "scummvm", "switch", "gtav", "arcade", "mame", "fbneo",
        )

        fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
