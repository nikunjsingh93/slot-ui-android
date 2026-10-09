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
    private class Found(val docUri: Uri, val name: String, val size: Long, val parentName: String)

    suspend fun scan(roots: List<Uri>, localRoot: java.io.File? = null, onBatch: ((List<Game>) -> Unit)? = null): List<Game> = withContext(Dispatchers.IO) {
        val found = ArrayList<Found>()
        for (root in roots) {
            val rootId = runCatching { DocumentsContract.getTreeDocumentId(root) }.getOrNull() ?: continue
            walk(root, rootId, "", 0, found)
        }
        localRoot?.let { walkLocal(it, 0, found) }
        val seen = HashSet<String>()
        val out = ArrayList<Game>(found.size)
        for (f in found) {
            coroutineContext.ensureActive()
            val g = runCatching { toGame(f) }.getOrNull() ?: continue
            if (seen.add(g.id)) { out += g; if (out.size % 25 == 0) onBatch?.invoke(out.toList()) }
        }
        out
    }

    private suspend fun walk(tree: Uri, docId: String, name: String, depth: Int, out: MutableList<Found>) {
        if (depth > 6) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
        )
        val dirs = ArrayList<Pair<String, String>>()
        ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                coroutineContext.ensureActive()
                val id = c.getString(0)
                val n = c.getString(1) ?: continue
                val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!n.startsWith(".") && n.lowercase() !in SKIP_DIRS) dirs += id to n
                } else {
                    val ext = n.substringAfterLast('.', "").lowercase()
                    if (ext == "zip" || GameSystem.byExtension(ext) != null || (ext == "bin" && guessBinSystem(name) != null)) {
                        out += Found(DocumentsContract.buildDocumentUriUsingTree(tree, id), n, c.getLong(3), name)
                    }
                }
            }
        }
        for ((id, n) in dirs) walk(tree, id, n, depth + 1, out)
    }

    private fun walkLocal(dir: java.io.File, depth: Int, out: MutableList<Found>) {
        if (depth > 6) return
        for (f in dir.listFiles() ?: return) {
            if (f.isDirectory) { if (!f.name.startsWith(".")) walkLocal(f, depth + 1, out); continue }
            val ext = f.extension.lowercase()
            if (ext == "zip" || GameSystem.byExtension(ext) != null || (ext == "bin" && guessBinSystem(dir.name) != null)) {
                out += Found(Uri.fromFile(f), f.name, f.length(), dir.name)
            }
        }
    }

    private fun guessBinSystem(folder: String): GameSystem? {
        val f = folder.lowercase()
        return if (f.contains("genesis") || f.contains("mega") || f == "md" || f.contains("sega")) GameSystem.GENESIS else null
    }

    private fun toGame(f: Found): Game? {
        val ext = f.name.substringAfterLast('.', "").lowercase()
        val stem = f.name.substringBeforeLast('.')
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
        private val SKIP_DIRS = setOf(
            "labels", "media", "bios", "saves", "states", "images", "boxart", "snaps", "thumbnails", "manuals",
            // systems slot does not emulate: don't waste time opening their (often huge) zip collections
            "n64", "psx", "ps1", "ps2", "ps3", "psp", "psv", "vita", "gc", "gamecube", "wii", "wiiu", "3ds", "nds", "ds",
            "dreamcast", "dc", "saturn", "xbox", "windows", "1windows", "pc", "dos", "scummvm", "switch", "gtav", "arcade", "mame", "fbneo",
        )

        fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
