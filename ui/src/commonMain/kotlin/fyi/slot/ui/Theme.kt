package fyi.slot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import fyi.slot.ui.res.Res
import fyi.slot.ui.res.opensans
import org.jetbrains.compose.resources.Font

/** Palette lifted from slot's default theme (crates/slot-store/src/theme.rs). */
object SlotColors {
    val Housing = Color(0xFF242429)
    val Recess = Color(0xFF1A1A1D)
    val Opening = Color(0xFF050508)
    val Edge = Color(0xFF4D4D57)
    val Ink = Color(0xFFF6F4EF)
    val CapInk = Color(0xFF1A1917)
    val Gold = Color(0xFFD4A93C)
    val Board = Color(0xFF2C9652)
    val Trace = Color(0xFF5AB474)
    val GoldPad = Color(0xFFE6B446)
    val Accent = Color(0xFF8BD3A4)
}

@Composable
fun rememberSlotFont(): FontFamily {
    val regular = Font(Res.font.opensans, FontWeight.Normal)
    val bold = Font(Res.font.opensans, FontWeight.Bold)
    return remember(regular, bold) { FontFamily(regular, bold) }
}
