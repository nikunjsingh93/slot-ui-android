package fyi.slot.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import fyi.slot.ui.CartGallery
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
