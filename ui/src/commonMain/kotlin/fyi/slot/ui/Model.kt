package fyi.slot.ui

import androidx.compose.ui.graphics.Color

/** Physical cart silhouette family. */
enum class CartShape { GBA, GB, GBC, NES, SNES, GENESIS, CARD, HUCARD, N64, DISC_PSX, DISC_PS2, DISC_GC, DISC_DC, UMD, NDS, N3DS, SWITCH, APP_CARD }

/** [external] cores open an installed emulator app; the others are libretro cores bundled in slot. */
data class CoreChoice(val id: String, val name: String, val tagline: String, val external: Boolean = false)

/** Opens the game in RetroArch with the same libretro core slot would use. */
val RETROARCH = CoreChoice("retroarch", "RetroArch", "Opens your installed RetroArch.", external = true)

enum class GameSystem(
    val id: String,
    val title: String,
    val short: String,
    val shape: CartShape,
    val extensions: Set<String>,
    /** Folder name on thumbnails.libretro.com */
    val thumbDir: String,
    baseCores: List<CoreChoice>,
    val lettering: String,
    val tint: Color,
    /** True when the game is handed to an installed emulator app instead of a bundled libretro core. */
    val external: Boolean = false,
    /** Extensions that identify the system on their own, wherever the file lives. */
    val uniqueExt: Set<String> = extensions,
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
    ),
    PSX(
        "psx", "PlayStation", "PSX", CartShape.DISC_PSX, setOf("cue", "chd", "pbp", "iso", "img", "m3u", "bin", "ecm"),
        "Sony - PlayStation",
        listOf(
            CoreChoice("pcsx_rearmed", "PCSX ReARMed", "Built in. Fast and light."),
            CoreChoice("swanstation", "SwanStation", "Built in. PGXP geometry fix and upscaling, heavier."),
            CoreChoice("duckstation", "DuckStation", "Opens your installed DuckStation.", external = true),
        ),
        "PlayStation", Color(0xFF9AA0B8),
        external = true, uniqueExt = emptySet(),
    ),
    PS2(
        "ps2", "PlayStation 2", "PS2", CartShape.DISC_PS2, setOf("iso", "chd", "cso", "zso", "cue", "bin", "gz"),
        "Sony - PlayStation 2",
        listOf(
            CoreChoice("armsx2", "ARMSX2", "Opens your installed ARMSX2.", external = true),
            CoreChoice("aethersx2", "AetherSX2", "Opens your installed AetherSX2 / NetherSX2.", external = true),
        ),
        "PlayStation 2", Color(0xFF2A4BA0),
        external = true, uniqueExt = setOf("cso", "zso"),
    ),
    N64(
        "n64", "Nintendo 64", "N64", CartShape.N64, setOf("z64", "n64", "v64", "zip", "7z", "ndd"),
        "Nintendo - Nintendo 64",
        listOf(
            CoreChoice("mupen64plus_next_gles3", "Mupen64Plus-Next", "Built in. Accurate N64."),
            CoreChoice("mupen64fz", "M64Plus FZ", "Opens your installed M64Plus FZ.", external = true),
        ),
        "NINTENDO 64", Color(0xFF8E8E96),
        external = true, uniqueExt = setOf("z64", "n64", "v64", "ndd"),
    ),
    GC(
        "gc", "GameCube", "GC", CartShape.DISC_GC, setOf("iso", "gcm", "ciso", "gcz", "rvz", "wbfs", "wia", "tgc"),
        "Nintendo - GameCube",
        listOf(
            CoreChoice("dolphin", "Dolphin", "Opens your installed Dolphin.", external = true),
            CoreChoice("dolphin_mmjr", "Dolphin MMJR", "Opens your installed Dolphin MMJR.", external = true),
        ),
        "GAMECUBE", Color(0xFF6A5ACD),
        external = true, uniqueExt = setOf("gcm", "ciso", "gcz", "rvz", "tgc"),
    ),
    PSP(
        "psp", "PSP", "PSP", CartShape.UMD, setOf("iso", "cso", "pbp", "chd"),
        "Sony - PlayStation Portable",
        listOf(
            CoreChoice("ppsspp", "PPSSPP", "Built in."),
            CoreChoice("ppsspp_app", "PPSSPP app", "Opens your installed PPSSPP.", external = true),
        ),
        "PlayStation Portable", Color(0xFF3E6BB0),
        external = true, uniqueExt = setOf("cso"),
    ),
    SWITCH(
        "switch", "Nintendo Switch", "Switch", CartShape.SWITCH, setOf("nsp", "xci", "nsz", "xcz", "nro"),
        "Nintendo - Nintendo Switch",
        listOf(
            CoreChoice("eden", "Eden", "Opens your installed Eden.", external = true),
            CoreChoice("kenjinx", "Kenji-NX", "Opens your installed Kenji-NX.", external = true),
        ),
        "NINTENDO SWITCH", Color(0xFFE60012),
        external = true, uniqueExt = setOf("nsp", "xci", "nsz", "xcz"),
    ),
    NDS(
        "nds", "Nintendo DS", "NDS", CartShape.NDS, setOf("nds", "dsi", "ids", "zip", "7z"),
        "Nintendo - Nintendo DS",
        listOf(
            CoreChoice("melondsds", "melonDS DS", "Built in."),
            CoreChoice("melonds_app", "melonDS", "Opens your installed melonDS.", external = true),
            CoreChoice("drastic", "DraStic", "Opens your installed DraStic.", external = true),
        ),
        "NINTENDO DS", Color(0xFF8C8C94),
        external = true, uniqueExt = setOf("nds", "dsi", "ids"),
    ),
    N3DS(
        "n3ds", "Nintendo 3DS", "3DS", CartShape.N3DS, setOf("3ds", "cci", "cia", "cxi", "3dsx", "z3dsx", "zcci", "zcxi", "app"),
        "Nintendo - Nintendo 3DS",
        listOf(
            CoreChoice("azahar", "Azahar", "Opens your installed Azahar.", external = true),
            CoreChoice("azaharplus", "AzaharPlus", "Opens your installed AzaharPlus.", external = true),
            CoreChoice("mandarine", "Mandarine", "Opens your installed Mandarine.", external = true),
            CoreChoice("citra_mmj", "Citra MMJ", "Opens your installed Citra MMJ.", external = true),
        ),
        "NINTENDO 3DS", Color(0xFFD03A3A),
        external = true, uniqueExt = setOf("3ds", "cia", "cxi", "3dsx", "z3dsx", "zcci", "zcxi"),
    ),
    DC(
        "dc", "Dreamcast", "DC", CartShape.DISC_DC, setOf("gdi", "cdi", "chd", "cue", "m3u"),
        "Sega - Dreamcast",
        listOf(
            CoreChoice("redream", "Redream", "Opens your installed Redream.", external = true),
            CoreChoice("flycast", "Flycast", "Built in, experimental: black screen on some devices."),
        ),
        "DREAMCAST", Color(0xFFE8631A),
        external = true, uniqueExt = setOf("gdi", "cdi"),
    ),
    XBOX(
        "xbox", "Xbox", "XBOX", CartShape.DISC_PS2, setOf("iso", "xiso", "cci"),
        "Microsoft - Xbox",
        listOf(CoreChoice("hakux", "hakuX", "Opens your installed hakuX.", external = true)),
        "XBOX", Color(0xFF3A8A3A),
        // (.cci is also a 3DS format: an Xbox .cci is found by its folder)
        external = true, uniqueExt = setOf("xiso"),
    ),
    PS3(
        "ps3", "PlayStation 3", "PS3", CartShape.DISC_PS2, setOf("iso", "pkg"),
        "Sony - PlayStation 3",
        listOf(CoreChoice("armsx3", "ARMSX3", "Opens your installed ARMSX3.", external = true)),
        "PlayStation 3", Color(0xFF30343C),
        external = true, uniqueExt = emptySet(),
    ),
    X360(
        "x360", "Xbox 360", "360", CartShape.DISC_PS2, setOf("iso", "xex", "zar", "god"),
        "Microsoft - Xbox 360",
        listOf(CoreChoice("xendroid", "Xendroid+", "Opens your installed Xendroid+.", external = true)),
        "XBOX 360", Color(0xFF7CB342),
        external = true, uniqueExt = setOf("xex", "zar"),
    ),
    /** Android apps you chose to show as plain apps. */
    APPS(
        "apps", "Apps", "Apps", CartShape.APP_CARD, emptySet(),
        "", listOf(CoreChoice("app", "Android app", "Opens the app.", external = true)),
        "APP", Color(0xFF8BD3A4),
        external = true, uniqueExt = emptySet(),
    ),
    /** Android games you chose to show. */
    INSTALLED(
        "installed", "Installed", "Installed", CartShape.APP_CARD, emptySet(),
        "", listOf(CoreChoice("app", "Android game", "Opens the game.", external = true)),
        "GAME", Color(0xFFE5534B),
        external = true, uniqueExt = emptySet(),
    );
    /** The emulators to choose from: systems with a built-in core also offer RetroArch, if it is installed. */
    val cores: List<CoreChoice> = if (baseCores.any { !it.external }) baseCores + RETROARCH else baseCores

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
        /** The system a file extension identifies by itself (ambiguous ones like .iso need a folder hint). */
        fun byExtension(ext: String) = entries.firstOrNull { ext.lowercase() in it.uniqueExt }

        /** Folder names people actually use, lower-cased with everything but letters and digits removed. */
        private val ALIASES: Map<GameSystem, List<String>> by lazy {
            mapOf(
                GBA to listOf("gba", "gameboyadvance", "gbadvance", "nintendogba", "agb"),
                GBC to listOf("gbc", "gameboycolor", "gameboycolour", "gbcolor", "nintendogbc"),
                GB to listOf("gb", "gameboy", "dmg", "nintendogb", "gameboymonochrome"),
                NES to listOf("nes", "famicom", "fc", "nintendoentertainmentsystem", "nintendones", "famicomnes"),
                SNES to listOf("snes", "sfc", "superfamicom", "supernintendo", "supernes", "supernintendoentertainmentsystem", "nintendosnes"),
                GENESIS to listOf("md", "genesis", "megadrive", "segagenesis", "segamegadrive", "mdgenesis", "smd"),
                SMS to listOf("sms", "mastersystem", "segamastersystem", "markiii"),
                GG to listOf("gg", "gamegear", "segagamegear"),
                PCE to listOf("pce", "pcengine", "turbografx", "turbografx16", "tg16", "tgfx16", "necpcengine"),
                PSX to listOf("psx", "ps1", "psone", "playstation", "playstation1", "sonyplaystation", "sonyps1", "sonypsx"),
                PS2 to listOf("ps2", "playstation2", "sonyplaystation2", "sonyps2"),
                PSP to listOf("psp", "playstationportable", "sonypsp", "sonyplaystationportable"),
                NDS to listOf("nds", "ds", "nintendods", "dsi", "nintendodsi"),
                N3DS to listOf("3ds", "n3ds", "nintendo3ds", "new3ds"),
                SWITCH to listOf("switch", "nintendoswitch", "nsw", "ns"),
                N64 to listOf("n64", "nintendo64", "nintendon64"),
                GC to listOf("gc", "ngc", "gamecube", "nintendogamecube", "nintendogc"),
                DC to listOf("dc", "dreamcast", "segadreamcast", "segadc", "sdc"),
                XBOX to listOf("xbox", "xboxoriginal", "originalxbox", "microsoftxbox", "xbx"),
                PS3 to listOf("ps3", "playstation3", "sonyplaystation3", "sonyps3"),
                X360 to listOf("x360", "xbox360", "360", "microsoftxbox360", "xb360"),
            )
        }

        /**
         * Works out which system a folder holds from whatever it is called: "PSX", "ps1", "Sony PlayStation",
         * "Sega Dreamcast (Games)", "N64 Roms"... Exact names and single words win; long names are matched
         * as substrings, longest alias first, so "PlayStation 2" is not mistaken for "PlayStation".
         */
        fun byFolder(name: String): GameSystem? {
            val lower = name.trim().lowercase()
            val norm = lower.filter { it.isLetterOrDigit() }
            if (norm.isEmpty()) return null
            val tokens = lower.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            // adjacent word pairs, so "game cube" / "play station 2" / "mega drive" still match
            val joined = tokens.windowed(2) { it.joinToString("") } + tokens.windowed(3) { it.joinToString("") }
            val exact = ALIASES.entries.firstOrNull { (_, a) -> norm in a }?.key
            if (exact != null) return exact
            var best: Pair<GameSystem, Int>? = null
            for ((sys, aliases) in ALIASES) for (a in aliases) {
                val hit = a in tokens || a in joined || (a.length >= 5 && norm.contains(a))
                if (hit && (best == null || a.length > best.second)) best = sys to a.length
            }
            return best?.first
        }
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
    /** True when the core was picked for this game specifically (START on its cart), rather than the system default. */
    val corePinned: Boolean = false,
    val lastPlayed: Long = 0,
    val hasSave: Boolean = false,
) {
    val title: String get() = cleanLabel(stem)
    val core: CoreChoice get() = system.cores.firstOrNull { it.id == coreId } ?: system.cores.first()
    /** True when this game opens in an installed emulator app rather than a core bundled in slot. */
    val external: Boolean get() = core.external
}

/**
 * Takes the release clutter off a file name before it is shown or matched: a release number in front ("3DS0016 - "),
 * a "-decrypted" tail and download-site names ("Switch-xci.com"). Regions and [tags] stay for the matcher.
 */
fun tidyStem(stem: String): String = stem
    .replace(RELEASE_NUMBER, "")
    .replace(DECRYPTED_TAIL, "")
    .replace(SITE_NAME, "")
    .trim()

// Compiled once: Game.title runs this for every comparison when a shelf is sorted, and compiling a case-insensitive
// pattern costs milliseconds on Android (it made the return from a game to the shelf stall for two seconds).
private val RELEASE_NUMBER = Regex("""^(?:3DS|NDS|NSW|DS)?\d{3,5}\s*-\s+""", RegexOption.IGNORE_CASE)
private val DECRYPTED_TAIL = Regex("""[\s_-]*decrypted$""", RegexOption.IGNORE_CASE)
private val SITE_NAME = Regex("""\s*[A-Za-z0-9][\w-]*\.(?:com|net|org|io|ru|xyz)\b""", RegexOption.IGNORE_CASE)
private val SPACES = Regex("""\s+""")

/** Strip (region) and [tags] and collapse spaces, as slot's cart.rs clean_label does. */
fun cleanLabel(stem: String): String = cleanLabelRaw(tidyStem(stem))

private fun cleanLabelRaw(stem: String): String {
    val sb = StringBuilder()
    var depth = 0
    for (ch in stem) when (ch) {
        '(', '[' -> depth++
        ')', ']' -> depth = (depth - 1).coerceAtLeast(0)
        else -> if (depth == 0) sb.append(ch)
    }
    val out = sb.toString().split(SPACES).filter { it.isNotEmpty() && it != "-" }.joinToString(" ")
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
