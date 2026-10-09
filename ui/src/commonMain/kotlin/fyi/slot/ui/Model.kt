package fyi.slot.ui

import androidx.compose.ui.graphics.Color

/** Physical cart silhouette family. */
enum class CartShape { GBA, GB, GBC, NES, SNES, GENESIS, CARD, HUCARD }

data class CoreChoice(val id: String, val name: String, val tagline: String)

enum class GameSystem(
    val id: String,
    val title: String,
    val short: String,
    val shape: CartShape,
    val extensions: Set<String>,
    /** Folder name on thumbnails.libretro.com */
    val thumbDir: String,
    val cores: List<CoreChoice>,
    val lettering: String,
    val tint: Color,
) {
    GBA(
        "gba", "Game Boy Advance", "GBA", CartShape.GBA, setOf("gba"),
        "Nintendo - Game Boy Advance",
        listOf(
            CoreChoice("mgba", "mGBA", "Accurate and fast. Best default."),
            CoreChoice("gpsp", "gpSP", "Lightweight. Great on weak hardware."),
        ),
        "GAME BOY ADVANCE", Color(0xFF6A5ACD),
    ),
    GBC(
        "gbc", "Game Boy Color", "GBC", CartShape.GBC, setOf("gbc"),
        "Nintendo - Game Boy Color",
        listOf(
            CoreChoice("gambatte", "Gambatte", "Accurate GB/GBC with built-in palettes."),
            CoreChoice("mgba", "mGBA", "Accurate, with GBA-style colour correction."),
        ),
        "GAME BOY COLOR", Color(0xFFB04A9A),
    ),
    GB(
        "gb", "Game Boy", "GB", CartShape.GB, setOf("gb"),
        "Nintendo - Game Boy",
        listOf(
            CoreChoice("gambatte", "Gambatte", "Accurate GB/GBC with built-in palettes."),
            CoreChoice("mgba", "mGBA", "Accurate, with Super Game Boy borders."),
        ),
        "GAME BOY", Color(0xFF8A8F7B),
    ),
    NES(
        "nes", "Nintendo Entertainment System", "NES", CartShape.NES, setOf("nes", "fds", "unf"),
        "Nintendo - Nintendo Entertainment System",
        listOf(CoreChoice("fceumm", "FCEUmm", "Fast and compatible.")),
        "NES", Color(0xFFB5B5B0),
    ),
    SNES(
        "snes", "Super Nintendo", "SNES", CartShape.SNES, setOf("sfc", "smc", "swc", "fig"),
        "Nintendo - Super Nintendo Entertainment System",
        listOf(CoreChoice("snes9x", "Snes9x", "Fast and compatible.")),
        "SUPER NINTENDO", Color(0xFF9A9AA4),
    ),
    GENESIS(
        "md", "Sega Genesis", "MD", CartShape.GENESIS, setOf("md", "gen", "smd"),
        "Sega - Mega Drive - Genesis",
        listOf(CoreChoice("genesis_plus_gx", "Genesis Plus GX", "Accurate Sega 8/16-bit.")),
        "SEGA GENESIS", Color(0xFF2B2B30),
    ),
    SMS(
        "sms", "Sega Master System", "SMS", CartShape.CARD, setOf("sms"),
        "Sega - Master System - Mark III",
        listOf(CoreChoice("genesis_plus_gx", "Genesis Plus GX", "Accurate Sega 8/16-bit.")),
        "MASTER SYSTEM", Color(0xFFC0392B),
    ),
    GG(
        "gg", "Sega Game Gear", "GG", CartShape.CARD, setOf("gg"),
        "Sega - Game Gear",
        listOf(CoreChoice("genesis_plus_gx", "Genesis Plus GX", "Accurate Sega 8/16-bit.")),
        "GAME GEAR", Color(0xFF1F1F24),
    ),
    PCE(
        "pce", "PC Engine", "PCE", CartShape.HUCARD, setOf("pce", "sgx"),
        "NEC - PC Engine - TurboGrafx 16",
        listOf(CoreChoice("mednafen_pce_fast", "Beetle PCE Fast", "Fast HuCard emulation.")),
        "PC ENGINE", Color(0xFFE08A1E),
    );

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
        fun byExtension(ext: String) = entries.firstOrNull { ext.lowercase() in it.extensions }
    }
}

data class Game(
    val id: String,
    val stem: String,
    val system: GameSystem,
    val uri: String,
    val size: Long = 0,
    /** GBA game code or GB cartridge code (e.g. "BPE"), used to pick the shell. */
    val code: String = "",
    /** Raw GB header title, used for the overseas Pokémon shells. */
    val gbTitle: String = "",
    /** Byte at 0x143 in a GB ROM: 0x80 dual mode, 0xC0 colour only. */
    val cgbFlag: Int = -1,
    val japan: Boolean = false,
    /** For ROMs inside a .zip: the entry name, otherwise empty. */
    val entryName: String = "",
    val coreId: String? = null,
    val lastPlayed: Long = 0,
    val hasSave: Boolean = false,
) {
    val title: String get() = cleanLabel(stem)
    val core: CoreChoice get() = system.cores.firstOrNull { it.id == coreId } ?: system.cores.first()
}

/** Strip (region) and [tags] and collapse spaces, as slot's cart.rs clean_label does. */
fun cleanLabel(stem: String): String {
    val sb = StringBuilder()
    var depth = 0
    for (ch in stem) when (ch) {
        '(', '[' -> depth++
        ')', ']' -> depth = (depth - 1).coerceAtLeast(0)
        else -> if (depth == 0) sb.append(ch)
    }
    val out = sb.toString().split(Regex("\\s+")).filter { it.isNotEmpty() && it != "-" }.joinToString(" ")
    return out.ifEmpty { stem }
}

/** The (region) / [tag] fragments of a ROM name, e.g. ["USA", "Rev 1"]. */
fun labelTags(stem: String): List<String> {
    val out = mutableListOf<String>()
    var depth = 0
    val cur = StringBuilder()
    for (ch in stem) {
        when (ch) {
            '(', '[' -> { depth++; if (depth == 1) { cur.clear(); continue } }
            ')', ']' -> {
                depth = (depth - 1).coerceAtLeast(0)
                if (depth == 0) { cur.toString().trim().takeIf { it.isNotEmpty() }?.let(out::add); continue }
            }
        }
        if (depth >= 1) cur.append(ch)
    }
    return out
}
