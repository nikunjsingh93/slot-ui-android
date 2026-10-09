package fyi.slot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset

/** Dev-only: lays out one sample cart per system, used by the headless preview renderer. */
@Composable
fun CartGallery(samples: List<Game>) {
    val assets = rememberCartAssets()
    Canvas(Modifier.fillMaxSize().background(SlotColors.Recess)) {
        val cols = 5
        val cell = size.width / cols
        for ((i, g) in samples.withIndex()) {
            val p = assets.painterFor(g, null)
            val s = (cell * .8f) / p.w
            val col = i % cols
            val row = i / cols
            translate(col * cell + cell * .1f, row * (size.height / 3f) + 30f) {
                scale(s, s, pivot = Offset.Zero) { with(p) { paint() } }
            }
        }
    }
}
