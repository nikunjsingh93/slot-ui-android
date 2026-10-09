package fyi.slot.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import fyi.slot.ui.StateSlot
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Save states + battery saves for one game.
 *
 * Slot 0 is the automatic "resume" state written on eject, slot 1 is the quick-save slot and
 * slots 2-8 are manual. Each state is `slotN.state` + a `slotN.png` screenshot.
 */
class StateStore(ctx: Context, private val gameId: String) {
    private val dir = File(ctx.filesDir, "states/$gameId").also { it.mkdirs() }
    private val saves = File(ctx.filesDir, "saves").also { it.mkdirs() }

    private fun state(slot: Int) = File(dir, "slot$slot.state")
    private fun shot(slot: Int) = File(dir, "slot$slot.png")
    private val srm get() = File(saves, "$gameId.srm")
    private val undo get() = File(dir, "undo.state")

    fun hasState(slot: Int) = state(slot).exists()

    fun write(slot: Int, data: ByteArray, thumb: Bitmap?) {
        val tmp = File(dir, "slot$slot.state.tmp")
        tmp.writeBytes(data)
        state(slot).delete()
        tmp.renameTo(state(slot))
        if (thumb != null) shot(slot).outputStream().use { thumb.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    fun read(slot: Int): ByteArray? = state(slot).takeIf { it.exists() }?.readBytes()

    fun delete(slot: Int) {
        state(slot).delete(); shot(slot).delete()
    }

    fun stashUndo(data: ByteArray) = undo.writeBytes(data)
    fun readUndo(): ByteArray? = undo.takeIf { it.exists() }?.readBytes()

    fun readSram(): ByteArray? = srm.takeIf { it.exists() }?.readBytes()
    fun writeSram(data: ByteArray) {
        if (data.isEmpty()) return
        val tmp = File(saves, "$gameId.srm.tmp")
        tmp.writeBytes(data)
        srm.delete()
        tmp.renameTo(srm)
    }

    /** The list shown in the state switcher: auto slot first, then 1..8. */
    fun slots(): List<StateSlot> = (0..8).map { s ->
        val f = state(s)
        val thumb = shot(s).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        StateSlot(
            slot = s,
            label = if (s == 0) "Auto" else "Slot $s",
            thumb = thumb,
            time = if (f.exists()) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(f.lastModified())) else "",
        )
    }

    companion object {
        fun hasAuto(ctx: Context, gameId: String) = File(ctx.filesDir, "states/$gameId/slot0.state").exists()
    }
}
