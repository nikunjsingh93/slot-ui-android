package fyi.slot.ui

import androidx.compose.ui.graphics.Color

enum class Finish { Solid, Translucent, Glitter }

data class ShellStyle(val color: Color, val finish: Finish = Finish.Solid)

private fun sh(rgb: Int, finish: Finish = Finish.Solid) =
    ShellStyle(Color(0xFF000000.toInt() or rgb), finish)

enum class GbClass { Original, DualMode, ColourOnly }

fun gbClass(game: Game): GbClass = when (game.cgbFlag) {
    0xC0 -> GbClass.ColourOnly
    0x80 -> GbClass.DualMode
    -1 -> if (game.system == GameSystem.GBC) GbClass.ColourOnly else GbClass.Original
    else -> GbClass.Original
}

/** GB carts with the notched shoulder (DMG & dual-mode) vs. the rounded GBC shell. */
fun isNotchedGb(game: Game) = gbClass(game) != GbClass.ColourOnly

/** Colour tables ported from slot's crates/slot-ui/src/shell.rs. */
object Shells {
    val Default = sh(0x35353a)
    private val Clear = sh(0xd9dbd8, Finish.Translucent)
    private val Yoshi = sh(0x2f8f4e)
    val Dmg = sh(0x9a978f)
    val DualMode = sh(0x333031)
    val GbClear = sh(0x7c7a8a, Finish.Translucent)
    private val Crystal = sh(0x86b9bf, Finish.Glitter)
    private val Kirby = sh(0xec94b4, Finish.Translucent)

    private val gbaExact = mapOf(
        "AXV" to sh(0xc2332e, Finish.Translucent), "AXP" to sh(0x2f5cc0, Finish.Translucent),
        "BPE" to sh(0x249c60, Finish.Translucent), "BPR" to sh(0xd85224), "BPG" to sh(0x63b044),
        "U3I" to Clear, "U32" to Clear, "U33" to Clear, "V49" to sh(0xa9513b),
        "KYGE" to Yoshi, "KYGP" to Yoshi, "RZW" to sh(0x5f6264, Finish.Translucent),
        "RZWJ" to sh(0xeceee8),
    )

    private val gbCodes = mapOf(
        "AAU" to sh(0xb38b3a), "AAUJ" to DualMode, "AAX" to sh(0xa9aaa7), "AAXJ" to DualMode,
        "BYT" to Crystal, "BXT" to Crystal, "KTN" to Kirby, "KKK" to Kirby,
        "KCE" to sh(0x1f9fb6, Finish.Translucent), "VCA" to sh(0xf0a95e, Finish.Translucent),
        "VPHJ" to sh(0xe2b413), "BMG" to DualMode,
    )

    private val gbOverseas = mapOf(
        "POKEMON RED" to sh(0xc0282c), "POKEMON BLU" to sh(0x2b3a88), "POKEMON YEL" to sh(0xe9a826),
    )

    private fun byCode(code: String, table: Map<String, ShellStyle>): ShellStyle? =
        table[code.take(4)] ?: table[code.take(3)]

    fun forGame(game: Game): ShellStyle = when (game.system.shape) {
        CartShape.GBA -> byCode(game.code, gbaExact)
            ?: if (game.code.startsWith("M")) sh(0xc6c6c9) else Default
        CartShape.GB, CartShape.GBC -> byCode(game.code, gbCodes)
            ?: gbOverseas[game.gbTitle].takeIf { !game.japan }
            ?: when (gbClass(game)) {
                GbClass.Original -> Dmg
                GbClass.DualMode -> DualMode
                GbClass.ColourOnly -> GbClear
            }
        CartShape.NES -> sh(0xa8a8a4)
        CartShape.SNES -> sh(0x9d9da6)
        CartShape.GENESIS -> sh(0x26262b)
        CartShape.CARD -> if (game.system == GameSystem.GG) sh(0x22222a) else sh(0xb8322a)
        CartShape.HUCARD -> sh(0xe9e6dc)
        CartShape.N64 -> sh(0x8f8f95)
        CartShape.DISC_PSX -> sh(0x2a2b31)
        CartShape.DISC_PS2 -> sh(0x14203f)
        CartShape.DISC_GC -> sh(0xc4c7d2)
        CartShape.DISC_DC -> sh(0xdfe1e8)
        CartShape.UMD -> sh(0x6f7482, Finish.Translucent)
        CartShape.APP_CARD -> if (game.system == GameSystem.INSTALLED) sh(0x25262b) else sh(0xe6e8ee)
    }

    /** FNV-1a hash of the title -> hue, exactly as cart.rs label_colour does. */
    fun labelColor(title: String): Color {
        var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (b in title.encodeToByteArray()) {
            h = h xor (b.toLong() and 0xff)
            h *= 0x100000001b3L
        }
        val hue = (h.toULong() % 360uL).toFloat()
        return Color.hsv(hue, 0.52f, 0.74f)
    }
}
