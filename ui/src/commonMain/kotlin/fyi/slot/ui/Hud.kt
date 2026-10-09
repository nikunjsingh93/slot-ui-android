package fyi.slot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** Pixels per slot design unit (slot's native canvas is 640x480; 1 unit = 1px there). */
val LocalU = compositionLocalOf { 1f }

@Composable
fun Float.udp(): Dp { val v = this * LocalU.current; return with(LocalDensity.current) { v.toDp() } }

@Composable
fun Float.usp(): TextUnit { val v = this * LocalU.current; return with(LocalDensity.current) { v.toSp() } }

@Composable
fun slotText(size: Float, color: Color = SlotColors.Ink, bold: Boolean = false, align: TextAlign = TextAlign.Start, spacing: Float = 0f): TextStyle =
    TextStyle(
        fontFamily = rememberSlotFont(), fontSize = size.usp(), color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = align,
        letterSpacing = if (spacing != 0f) spacing.usp() else TextUnit.Unspecified,
    )

@Composable
fun SlotText(
    text: String, size: Float, modifier: Modifier = Modifier, color: Color = SlotColors.Ink, bold: Boolean = false,
    align: TextAlign = TextAlign.Start, spacing: Float = 0f, maxLines: Int = 1, softWrap: Boolean = true,
) = BasicText(
    text, modifier, slotText(size, color, bold, align, spacing),
    maxLines = maxLines, softWrap = softWrap, overflow = if (softWrap) TextOverflow.Ellipsis else TextOverflow.Visible,
)

/** The cream key-cap + label used by slot's hint legend. */
@Composable
fun Hint(key: String, label: String, modifier: Modifier = Modifier, dim: Boolean = false) {
    Row(modifier.alpha(if (dim) .45f else 1f), verticalAlignment = Alignment.CenterVertically) {
        KeyCap(key)
        Spacer(Modifier.width(5f.udp()))
        SlotText(label, 14f, bold = false)
    }
}

@Composable
fun KeyCap(key: String) {
    val round = 5f.udp()
    Box(
        Modifier.height(20f.udp()).clip(RoundedCornerShape(round)).background(SlotColors.Ink)
            .padding(horizontal = 5f.udp()),
        contentAlignment = Alignment.Center,
    ) {
        when (key) {
            "◀▶", "LR" -> ArrowsGlyph()
            "▲▼", "UD" -> ArrowsGlyph(vertical = true)
            else -> SlotText(key, 12.5f, color = SlotColors.CapInk, bold = true, align = TextAlign.Center)
        }
    }
}

@Composable
private fun ArrowsGlyph(vertical: Boolean = false) {
    val u = LocalU.current
    Canvas(Modifier.size(width = (if (vertical) 9f else 22f).udp(), height = (if (vertical) 14f else 10f).udp())) {
        val c = SlotColors.CapInk
        val s = 4.2f * u
        fun tri(cx: Float, cy: Float, dx: Float, dy: Float) {
            val p = Path()
            if (dx != 0f) {
                p.moveTo(cx, cy - s); p.lineTo(cx + dx * s, cy); p.lineTo(cx, cy + s)
            } else {
                p.moveTo(cx - s, cy); p.lineTo(cx, cy + dy * s); p.lineTo(cx + s, cy)
            }
            p.close()
            drawPath(p, c)
        }
        if (vertical) {
            tri(size.width / 2, size.height * .22f, 0f, -1f)
            tri(size.width / 2, size.height * .78f, 0f, 1f)
        } else {
            tri(size.width * .22f, size.height / 2, -1f, 0f)
            tri(size.width * .78f, size.height / 2, 1f, 0f)
        }
    }
}

@Composable
fun BatteryGauge(hud: HudState) {
    val u = LocalU.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 22f.udp(), height = 11f.udp())) {
            val stroke = 1.3f * u
            val body = Size(size.width - 2.5f * u, size.height)
            drawRoundRect(
                SlotColors.Ink.copy(alpha = .85f), Offset.Zero, body, CornerRadius(2f * u), style = Stroke(stroke),
            )
            drawRoundRect(
                SlotColors.Ink.copy(alpha = .85f), Offset(size.width - 2f * u, size.height * .3f),
                Size(2f * u, size.height * .4f), CornerRadius(.8f * u),
            )
            val pct = hud.batteryPercent.coerceIn(0, 100) / 100f
            val fill = if (hud.charging) SlotColors.Accent else if (hud.batteryPercent <= 15) Color(0xFFE5534B) else SlotColors.Ink
            val pad = stroke * 1.8f
            drawRoundRect(
                fill, Offset(pad, pad), Size((body.width - 2 * pad) * pct, body.height - 2 * pad), CornerRadius(1f * u),
            )
        }
        Spacer(Modifier.width(6f.udp()))
        SlotText("${hud.batteryPercent}%", 13f, color = SlotColors.Ink.copy(alpha = .8f))
    }
}

/** A row of system pills along the top; the active one takes the system tint. */
@Composable
fun SystemPills(systems: List<GameSystem>, active: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6f.udp()), verticalAlignment = Alignment.CenterVertically) {
        systems.forEachIndexed { i, s ->
            val on = i == active
            Box(
                Modifier.height(22f.udp()).clip(RoundedCornerShape(11f.udp()))
                    .background(if (on) SlotColors.Ink else SlotColors.Housing)
                    .padding(horizontal = (if (on) 11f else 8f).udp()),
                contentAlignment = Alignment.Center,
            ) {
                SlotText(
                    if (on) s.title else s.short, if (on) 13.5f else 12f,
                    color = if (on) SlotColors.CapInk else SlotColors.Ink.copy(alpha = .55f), bold = on,
                )
            }
        }
    }
}

fun DrawScope.drawVignette(color: Color = Color.Black) {
    val r = maxOf(size.width, size.height) * .75f
    drawRect(
        androidx.compose.ui.graphics.Brush.radialGradient(
            0f to Color.Transparent, .55f to Color.Transparent, 1f to color.copy(alpha = .55f),
            center = Offset(size.width / 2, size.height * .46f), radius = r,
        ),
    )
}
