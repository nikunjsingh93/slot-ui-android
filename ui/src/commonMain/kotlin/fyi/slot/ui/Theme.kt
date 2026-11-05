package fyi.slot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import fyi.slot.ui.res.Res
import fyi.slot.ui.res.opensans
import org.jetbrains.compose.resources.Font

/**
 * Settings > Interface > Theme: the colours of the shelf, the bars, panels and menus. Carts and discs keep their own
 * colours in every theme.
 */
enum class AppTheme(
    val label: String,
    val housing: Color, val recess: Color, val opening: Color, val edge: Color, val drawer: Color,
) {
    Gray("Gray (classic)", Color(0xFF242429), Color(0xFF1A1A1D), Color(0xFF050508), Color(0xFF4D4D57), Color(0xFF0E0E11)),
    Black("Black (OLED)", Color(0xFF111113), Color(0xFF000000), Color(0xFF000000), Color(0xFF2E2E34), Color(0xFF000000)),
    Blue("Dark blue", Color(0xFF1B2740), Color(0xFF111A2C), Color(0xFF04070E), Color(0xFF3A4D74), Color(0xFF0A1120)),
}

/** Palette lifted from slot's default theme (crates/slot-store/src/theme.rs); the dark surfaces follow [theme]. */
object SlotColors {
    /** Set from the settings by [SlotRoot]; everything drawn with the colours below follows it. */
    var theme by mutableStateOf(AppTheme.Black)
    val Housing get() = theme.housing
    val Recess get() = theme.recess
    val Opening get() = theme.opening
    val Edge get() = theme.edge
    /** The app drawer's backdrop. */
    val DrawerBg get() = theme.drawer
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
