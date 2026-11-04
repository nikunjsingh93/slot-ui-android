package fyi.slot.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import fyi.slot.ui.CartGallery
import fyi.slot.ui.CoverMatch
import fyi.slot.ui.Game
import fyi.slot.ui.GameSystem
import fyi.slot.ui.Pad
import fyi.slot.ui.SlotRoot
import fyi.slot.ui.SlotState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize

private const val FRAME = 16_666_667L

/** Renders [content], stepping a scripted timeline; each [Step] may mutate state then capture a frame. */
@OptIn(ExperimentalComposeUiApi::class)
class Shoot(val out: File, val name: String, val w: Int, val h: Int, content: @Composable () -> Unit) {
    private val scene = ImageComposeScene(w, h, Density(1f), content = content)
    private var t = 0L
    private var shot = 0

    init { advance(40) }

    fun advance(frames: Int) { repeat(frames) { scene.render(t); t += FRAME } }
    fun ms(ms: Int) = advance((ms * 60 / 1000).coerceAtLeast(1))

    fun snap(label: String = "") {
        val img = scene.render(t); t += FRAME
        val file = File(out, "${name}${if (label.isEmpty()) "" else "_$label"}.png")
        file.writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        println("wrote ${file.absolutePath}")
    }

    fun close() = scene.close()
}

fun sample(sys: GameSystem, stem: String, code: String = "", cgb: Int = -1, gbTitle: String = "") =
    Game(id = "${sys.id}-$stem", stem = stem, system = sys, uri = "", code = code, cgbFlag = cgb, gbTitle = gbTitle)

val gbaLib = listOf(
    sample(GameSystem.GBA, "Advance Wars (USA)", "AWRE"),
    sample(GameSystem.GBA, "Castlevania - Aria of Sorrow (USA)", "A2CE"),
    sample(GameSystem.GBA, "Drill Dozer (USA)", "V49E"),
    sample(GameSystem.GBA, "Fire Emblem (USA)", "AE7E"),
    sample(GameSystem.GBA, "Metroid Fusion (USA)", "AMTE"),
    sample(GameSystem.GBA, "Pokemon - Emerald Version (USA, Europe)", "BPEE"),
    sample(GameSystem.GBA, "Pokemon - Sapphire Version (USA)", "AXPE"),
    sample(GameSystem.GBA, "WarioWare, Inc. - Mega Microgame\$ (USA)", "RZWE"),
)

fun library(): List<Game> = gbaLib + listOf(
    sample(GameSystem.GB, "Tetris (World)", cgb = 0),
    sample(GameSystem.GB, "Pokemon - Red Version (USA)", gbTitle = "POKEMON RED", cgb = 0),
    sample(GameSystem.GBC, "Pokemon - Crystal Version (USA)", "BYTE", cgb = 0xC0),
    sample(GameSystem.GBC, "Legend of Zelda, The - Oracle of Ages (USA)", "AZ7E", cgb = 0xC0),
    sample(GameSystem.NES, "Super Mario Bros. 3 (USA)"),
    sample(GameSystem.SNES, "Super Metroid (Japan, USA) (En,Ja)"),
    sample(GameSystem.GENESIS, "Sonic The Hedgehog 2 (World)"),
)

fun main(args: Array<String>) {
    fyi.slot.ui.autoScaleBigScreens = false
    val out = File(args.firstOrNull() ?: "build/previews").also { it.mkdirs() }
    val which = args.getOrNull(1) ?: "all"

    if (which == "all" || which == "gallery") {
        val s = Shoot(out, "gallery", 1500, 800) { CartGallery(library() + sample(GameSystem.SMS, "Alex Kidd in Miracle World (USA, Europe)") + sample(GameSystem.GG, "Sonic The Hedgehog (World)") + sample(GameSystem.PCE, "Bonk's Adventure (USA)")) }
        s.snap(); s.close()
    }

    if (which == "all" || which == "home") {
        val state = SlotState().apply { setLibrary(library()); selected = 5 }
        val s = Shoot(out, "home", 960, 720) { SlotRoot(state) }
        // 4:3 like the RG SP at 960x720
        s.ms(900); s.snap("landscape")
        state.press(Pad.Right); s.ms(120); s.snap("moving"); s.ms(800); s.snap("settled")
        state.press(Pad.R1); s.ms(900); s.snap("gb")
        state.press(Pad.R1); s.ms(900); s.snap("gbc")
        s.close()
    }

    if (which == "all" || which == "wide") {
        val state = SlotState().apply { setLibrary(library()); selected = 5 }
        val s = Shoot(out, "wide", 1920, 1080) { SlotRoot(state) }
        s.ms(900); s.snap()
        state.press(Pad.Start); s.ms(250); s.snap("flip_mid"); s.ms(900); s.snap("board")
        state.press(Pad.Right); s.ms(500); s.snap("board2")
        s.close()
    }

    if (which == "all" || which == "menus") menuShots(out)
    if (which == "all" || which == "extra") extraShots(out)
    if (which == "all" || which == "device") deviceShots(out)
    if (which == "anim") animShots(out)
    if (which == "drawer") drawerShots(out)
    if (which == "apps") appShots(out)
    if (which == "folders") folderCheck()
    if (which == "covermatch") coverMatchCheck(out)
    if (which == "consoles") consoleShots(out)
    if (which == "readme") readmeShots(out)
    if (which == "list") listShots(out)

    if (which == "all" || which == "phone") {
        val state = SlotState().apply { setLibrary(library()); selected = 5; gamepad = false }
        val s = Shoot(out, "phone", 540, 1170) { SlotRoot(state) }
        s.ms(900); s.snap()
        s.close()
    }
}

private fun fakeThumb(seed: Int): androidx.compose.ui.graphics.ImageBitmap {
    val bmp = androidx.compose.ui.graphics.ImageBitmap(240, 160)
    val c = androidx.compose.ui.graphics.Canvas(bmp)
    val p = androidx.compose.ui.graphics.Paint()
    val sky = androidx.compose.ui.graphics.Color.hsv((seed * 47f) % 360f, .45f, .9f)
    p.color = sky; c.drawRect(0f, 0f, 240f, 160f, p)
    p.color = androidx.compose.ui.graphics.Color.hsv(((seed * 47f) + 120f) % 360f, .6f, .5f); c.drawRect(0f, 100f, 240f, 160f, p)
    p.color = androidx.compose.ui.graphics.Color.White; c.drawRect(100f, 70f + seed * 3f % 20, 124f, 100f, p)
    return bmp
}

fun menuShots(out: File) {
    val game = library()[5]
    val state = SlotState().apply {
        setLibrary(library()); screen = fyi.slot.ui.Screen.Playing; playing = game
        for (i in 0..7) states.add(fyi.slot.ui.StateSlot(i, if (i == 0) "Auto" else "Slot $i", if (i < 4) fakeThumb(i) else null, if (i < 4) "Oct 9, 1${i}:2${i}" else ""))
    }
    val s = Shoot(out, "menu", 1280, 720) {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF6BB5F2))) { SlotRoot(state) }
    }
    s.ms(300); s.snap("playing")
    state.press(Pad.Menu, now = 1000); s.ms(300); s.snap("quick")
    state.press(Pad.Down); state.press(Pad.Down); state.press(Pad.Down); state.press(Pad.Down); state.press(Pad.Down); state.press(Pad.A); s.ms(300); s.snap("screen")
    state.closeOverlay(); s.ms(100); state.press(Pad.Menu, now = 2000); s.ms(100)
    state.press(Pad.Menu, now = 2200); s.ms(500); s.snap("states")
    state.press(Pad.Right); state.press(Pad.Right); s.ms(500); s.snap("states2")
    s.close()

    val st2 = SlotState().apply { setLibrary(library()); gamepad = true; screen = fyi.slot.ui.Screen.Browse; openSettings() }
    val s2 = Shoot(out, "settings", 1280, 720) { SlotRoot(st2) }
    s2.ms(400); s2.snap()
    s2.close()

    val st3 = SlotState().apply { setLibrary(library()); gamepad = false; screen = fyi.slot.ui.Screen.Playing; playing = game }
    val s3 = Shoot(out, "touch", 1280, 590) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF6BB5F2))) { SlotRoot(st3) } }
    s3.ms(300); s3.snap()
    s3.close()

    val st4 = SlotState().apply { setLibrary(library()); selected = 5 }
    val s4 = Shoot(out, "insert", 1280, 720) { SlotRoot(st4) }
    s4.ms(600)
    st4.insert(st4.current!!, true)
    s4.ms(150); s4.snap("a"); s4.ms(150); s4.snap("b"); s4.ms(150); s4.snap("c"); s4.ms(250); s4.snap("d")
    s4.close()
}

fun extraShots(out: File) {
    // empty library, phone + landscape
    val empty = SlotState().apply { gamepad = false }
    val e = Shoot(out, "empty", 960, 540) { SlotRoot(empty) }
    e.ms(300); e.snap(); e.close()

    // a real-looking cover on a cart, hold-A progress, and the GB shelf on a phone
    val state = SlotState().apply { setLibrary(library()); selected = 5; gamepad = false }
    val cov = fakeThumb(3)
    state.covers[state.current!!.id] = cov
    val s = Shoot(out, "phone2", 540, 1170) { SlotRoot(state) }
    s.ms(700); s.snap("gba")
    state.switchSystem(1); s.ms(700); s.snap("gb")
    state.switchSystem(1); state.switchSystem(1); s.ms(800); s.snap("nes")
    s.close()

    val st = SlotState().apply { setLibrary(library()); selected = 5 }
    val h = Shoot(out, "hold", 960, 540) { SlotRoot(st) }
    h.ms(500); st.press(Pad.A); h.ms(450); h.snap("holding"); h.close()
}

fun deviceShots(out: File) {
    // the RG476H: 1280x960 (4:3)
    val st = SlotState().apply { setLibrary(library()); selected = 1; systemIndex = 1 }
    val s = Shoot(out, "rg476h", 1280, 960) { SlotRoot(st) }
    s.ms(700); s.snap("gb")
    st.switchSystem(1); s.ms(700); s.snap("gbc")
    st.switchSystem(1); st.switchSystem(1); s.ms(700); s.snap("snes")
    s.close()
}

fun animShots(out: File) {
    val st = SlotState().apply { setLibrary(gbaLib); selected = 2; setLibrary(gbaLib); }
    st.select(2)
    val s = Shoot(out, "anim", 960, 720) { SlotRoot(st) }
    s.ms(500)
    s.snap("a0_rest")
    st.press(Pad.Right, now = 1000)
    st.release(Pad.Right, 1010)
    s.ms(50); s.snap("a1_right_50ms")
    s.ms(70); s.snap("a2_right_120ms")
    s.ms(120); s.snap("a3_right_240ms")
    s.ms(500)
    st.press(Pad.Left, now = 3000); st.release(Pad.Left, 3010)
    s.ms(60); s.snap("b1_left_60ms")
    s.ms(150); s.snap("b2_left_210ms")
    s.ms(600)
    // insert
    st.insert(st.current!!, true)
    var t = 0
    for (ms in listOf(80, 80, 80, 80, 80, 80, 80, 80)) { s.ms(ms); t += ms; s.snap("c_insert_%03d".format(t)) }
    s.close()
}

fun consoleShots(out: File) {
    val games = listOf(
        sample(GameSystem.PSX, "Final Fantasy VII (USA) (Disc 1)"),
        sample(GameSystem.PSX, "Disney's Tarzan (USA)"),
        sample(GameSystem.PS2, "Gran Turismo 4"),
        sample(GameSystem.PS2, "SSX 3 (USA)"),
        sample(GameSystem.N64, "Banjo-Kazooie (USA) (Rev 1)"),
        sample(GameSystem.N64, "AeroGauge (USA)"),
        sample(GameSystem.GC, "Super Mario Sunshine (USA)"),
        sample(GameSystem.GC, "Legend of Zelda, The - The Wind Waker (USA)"),
    )
    val s = Shoot(out, "consoles", 1500, 800) { fyi.slot.ui.CartGallery(games) }
    s.snap(); s.close()
    // with cover art
    val st = SlotState().apply { setLibrary(games); systemIndex = 2 }
    st.covers[st.shelf[0].id] = fakeThumb(5)
    val h = Shoot(out, "consoles_shelf", 1280, 960) { SlotRoot(st) }
    h.ms(600); h.snap("n64"); st.switchSystem(1); h.ms(600); h.snap("gc")
    st.switchSystem(-2); st.covers[st.shelf[0].id] = fakeThumb(2); h.ms(600); h.snap("ps2")
    st.press(Pad.Start); h.ms(1000); h.snap("ps2_picker")
    h.close()
}

fun appShots(out: File) {
    val games = listOf(
        sample(GameSystem.PSP, "Virtua Tennis - World Tour (USA)"),
        sample(GameSystem.PSP, "God of War - Chains of Olympus (USA)"),
        sample(GameSystem.DC, "SoulCalibur (USA)"),
        sample(GameSystem.DC, "Crazy Taxi 2 (USA)"),
        sample(GameSystem.APPS, "Chrome"),
        sample(GameSystem.APPS, "Settings"),
        sample(GameSystem.INSTALLED, "Genshin Impact"),
        sample(GameSystem.INSTALLED, "Minecraft"),
    )
    val s = Shoot(out, "apps", 1500, 800) { fyi.slot.ui.CartGallery(games) }
    s.snap(); s.close()
    val st = SlotState().apply { setLibrary(games); systemIndex = 2 }
    st.covers[st.shelf[0].id] = fakeThumb(7)
    val h = Shoot(out, "apps_shelf", 1280, 960) { SlotRoot(st) }
    h.ms(600); h.snap("apps"); st.switchSystem(1); h.ms(600); h.snap("installed")
    h.close()
}

fun folderCheck() {
    val names = listOf("DC", "dc", "Dreamcast", "Sega Dreamcast (Games)", "SEGA_DC", "PSX", "ps1", "Sony PlayStation", "PlayStation 2", "PS2", "playstation portable", "PSP", "N64", "Nintendo 64", "GameCube", "Game Cube", "NGC", "gc", "GBA", "Game Boy Advance", "Game Boy", "gbc", "SNES", "Super Nintendo", "Mega Drive", "Genesis", "Roms", "Games", "1Windows", "GTAV", "Sega Master System", "TurboGrafx-16", "PC Engine", "FC", "NES")
    for (n in names) println("%-26s -> %s".format(n, GameSystem.byFolder(n)?.id))
}

fun drawerShots(out: File) {
    val names = listOf("Calculator", "Calendar", "Chrome", "Clock", "Files", "Gallery", "Maps", "Messages", "Music", "Phone", "Photos", "Settings", "YouTube", "Minecraft", "Genshin Impact", "Winlator", "Moonlight", "Steam Link")
    val host = object : fyi.slot.ui.SlotHost {
        override fun listApps() = names.mapIndexed { i, n -> fyi.slot.ui.AppEntry("pkg.$i", n, n in setOf("Minecraft", "Genshin Impact")) }
    }
    val st = SlotState(host).apply { setLibrary(library()); selected = 1; openDrawer() }
    names.indices.forEach { st.appIcons["pkg.$it"] = fakeThumb(it) }
    val s = Shoot(out, "drawer", 1280, 960) { SlotRoot(st) }
    s.ms(400); s.snap("grid")
    st.press(Pad.Down); st.press(Pad.Right); s.ms(200); s.snap("grid_cursor")
    st.press(Pad.R1); s.ms(300); s.snap("grid_large")
    st.press(Pad.X); s.ms(300); s.snap("list")
    s.close()

    val sizes = SlotState().apply { setLibrary(gbaLib + library()); selected = 5 }
    for (z in fyi.slot.ui.CartSize.entries) {
        sizes.settings = sizes.settings.copy(cartSize = z)
        val h = Shoot(out, "size_${z.name}", 1280, 960) { SlotRoot(sizes) }
        h.ms(500); h.snap(); h.close()
    }
    val umd = SlotState().apply { setLibrary(listOf(sample(GameSystem.PSP, "Virtua Tennis - World Tour (USA)"), sample(GameSystem.PSP, "Daxter (USA)"))) }
    val u = Shoot(out, "umd", 1280, 960) { SlotRoot(umd) }
    u.ms(500); u.snap(); u.close()
}

/** Runs the cover matcher against directory listings saved as `<out>/<n>.html` (curl of the libretro Named_Boxarts index). */
fun coverMatchCheck(out: File) {
    val cases = mapOf(
        "dc" to listOf("Virtua Tennis - Sega Professional Tennis (USA)", "Crazy Taxi 2 (USA)", "SoulCalibur (USA)"),
        "n64" to listOf("Wave Race 64 (USA) (Rev A)", "Wave Race 64", "Wave Race", "1080 Snowboarding (Japan, USA) (En,Ja)", "Bug s Life, A (USA)", "Banjo-Kazooie (USA) (Rev 1)", "RR64 - Ridge Racer 64 (USA)"),
        "ps2" to listOf("Gran Turismo 4", "SSX 3 (USA)", "WRC - Rally Evolved (Europe, Australia) (En,Fr,De,Es,It,Pt,No,Fi) (v2.00)", "Tom and Jerry in War of the Whiskers (USA) (En,Es)"),
        "psp" to listOf("Virtua Tennis - World Tour (USA)"),
    )
    for ((n, stems) in cases) {
        val names = CoverMatch.parseListing(File(out, "$n.html").readText())
        println("$n: ${names.size} names")
        for (s in stems) println("   $s  ->  ${CoverMatch.best(s, names)}")
    }
}

/** Screenshots for the README: the real screens, with made-up games so no real titles or box art appear. */
fun readmeShots(out: File) {
    val made = listOf(
        GameSystem.GBA to listOf("Aurora Drift", "Brass Lantern", "Cinder Valley", "Dune Courier", "Echo Harbor", "Fable Forge", "Glass Orchard", "Harbor Lights"),
        GameSystem.SNES to listOf("Iron Meadow", "Jade Comet", "Kite Runner 64", "Lunar Garden", "Moss Kingdom"),
        GameSystem.PSX to listOf("Neon Overpass", "Orbit Rally", "Paper Tigers", "Quartz Circuit", "Rust Bucket Racing"),
        GameSystem.N64 to listOf("Sky Ferry", "Tidal Sprint", "Umbra Peaks", "Velvet Raceway", "Wild Pines"),
        GameSystem.PSP to listOf("Xeno Trails", "Yonder Isle", "Zephyr Street", "Amber Signal"),
        GameSystem.DC to listOf("Blue Harbor", "Coral Coast Taxi", "Dawn Fighters", "Ember League"),
    )
    val lib = made.flatMap { (sys, titles) -> titles.map { sample(sys, it) } }
    fun shelf(name: String, systemIndex: Int, selected: Int, w: Int = 1280, h: Int = 960, list: Boolean = false, act: (SlotState, Shoot) -> Unit = { _, _ -> }) {
        val st = SlotState().apply {
            setLibrary(lib); this.systemIndex = systemIndex; setLibrary(lib); select(selected); gamepad = true
            if (list) settings = settings.copy(shelfList = true)
        }
        val s = Shoot(out, name, w, h) { SlotRoot(st) }
        s.ms(900); act(st, s); s.snap(); s.close()
    }
    shelf("readme_gba", 0, 3)
    shelf("readme_snes", 1, 2)
    shelf("readme_psx", 2, 1)
    shelf("readme_n64", 3, 2)
    shelf("readme_psp", 4, 1)
    shelf("readme_dc", 5, 1)
    shelf("readme_list", 0, 3, list = true)
    shelf("readme_picker", 2, 1) { st, s -> st.press(Pad.Start); s.ms(1100) }
    shelf("readme_settings", 0, 3) { st, s -> st.openSettings(); s.ms(500) }
    shelf("readme_portrait", 0, 3, w = 720, h = 1280) { st, _ -> st.gamepad = false }
}

fun listShots(out: File) {
    val titles = listOf("Aurora Drift", "Brass Lantern", "Cinder Valley", "Dune Courier", "Echo Harbor", "Fable Forge", "Glass Orchard", "Harbor Lights", "Iron Meadow", "Jade Comet", "Kite Runner", "Lunar Garden")
    val lib = titles.map { sample(GameSystem.GBA, it) } + listOf("Neon Overpass", "Orbit Rally").map { sample(GameSystem.PSX, it) }
    val st = SlotState().apply { setLibrary(lib); select(4); gamepad = true; settings = settings.copy(shelfList = true) }
    val s = Shoot(out, "readme_list", 1280, 960) { SlotRoot(st) }
    s.ms(900); s.snap()
    st.press(Pad.Down); s.ms(90); s.snap("mid"); s.close()
}
