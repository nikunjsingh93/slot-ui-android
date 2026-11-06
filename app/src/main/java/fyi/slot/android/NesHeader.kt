package fyi.slot.android

import android.content.Context
import java.io.File
import java.util.zip.CRC32

/**
 * Headerless NES ROMs (No-Intro style dumps, just PRG + CHR) can't be opened by FCEUmm, which needs the 16-byte
 * iNES header to know the mapper, sizes and mirroring. This puts the right NES 2.0 header in front, looked up by
 * the ROM's CRC32 in assets/nesheaders.txt (built from NewRisingSun's NES 2.0 database by tools/nes-header/build.js).
 */
object NesHeader {
    private var table: Map<String, String>? = null

    private fun table(ctx: Context): Map<String, String> = table ?: synchronized(this) {
        table ?: ctx.assets.open("nesheaders.txt").bufferedReader().useLines { lines ->
            lines.mapNotNull { l -> l.split(' ').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        }.also { table = it }
    }

    /** True when [f] already starts with a header (iNES/NES 2.0, FDS or UNIF), so there is nothing to do. */
    fun hasHeader(f: File): Boolean {
        val head = ByteArray(4)
        val n = f.inputStream().use { it.read(head) }
        if (n < 4) return false
        val s = String(head, Charsets.ISO_8859_1)
        return s == "NES\u001A" || s == "FDS\u001A" || s == "UNIF"
    }

    /** Gives [f] (a cache copy) its header if it has none. Fails with a readable message for a ROM we don't know. */
    fun fix(ctx: Context, f: File) {
        if (!f.extension.equals("nes", true) || hasHeader(f)) return
        val bytes = f.readBytes()
        val crc = CRC32().apply { update(bytes) }.value.toString(16).uppercase().padStart(8, '0')
        val hex = table(ctx)[crc] ?: error("This NES file has no header, and it isn't in the NES database. Try a headered (.nes with iNES header) copy.")
        val header = ByteArray(16) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val tmp = File(f.path + ".hdr")
        tmp.outputStream().use { it.write(header); it.write(bytes) }
        f.delete()
        tmp.renameTo(f)
    }
}
