package fyi.slot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class IconKind { Gear, Folder, Close, Back, Play }

@Composable
fun IconButton(kind: IconKind, size: Float = 36f, onClick: () -> Unit) {
    Box(
        Modifier.size(size.udp()).clip(CircleShape).background(SlotColors.Recess).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val u = LocalU.current
        Canvas(Modifier.size((size * .52f).udp())) { drawIcon(kind, SlotColors.Ink.copy(alpha = .9f), u) }
    }
}

fun DrawScope.drawIcon(kind: IconKind, color: Color, u: Float) {
    val s = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    val sw = s * .11f
    when (kind) {
        IconKind.Gear -> {
            drawCircle(color, s * .22f, c, style = Stroke(sw))
            for (i in 0 until 8) {
                val a = i * PI.toFloat() / 4f
                val p0 = Offset(c.x + cos(a) * s * .33f, c.y + sin(a) * s * .33f)
                val p1 = Offset(c.x + cos(a) * s * .48f, c.y + sin(a) * s * .48f)
                drawLine(color, p0, p1, sw * 1.5f, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            drawCircle(color, s * .33f, c, style = Stroke(sw * .9f))
        }
        IconKind.Folder -> {
            val top = s * .26f
            drawRoundRect(color, Offset(s * .06f, top + s * .10f), Size(s * .88f, s * .56f), CornerRadius(s * .08f), style = Stroke(sw))
            drawRoundRect(color, Offset(s * .06f, top), Size(s * .36f, s * .14f), CornerRadius(s * .05f), style = Stroke(sw))
        }
        IconKind.Close -> {
            drawLine(color, Offset(s * .15f, s * .15f), Offset(s * .85f, s * .85f), sw * 1.2f, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, Offset(s * .85f, s * .15f), Offset(s * .15f, s * .85f), sw * 1.2f, androidx.compose.ui.graphics.StrokeCap.Round)
        }
        IconKind.Back -> {
            drawLine(color, Offset(s * .8f, s * .5f), Offset(s * .2f, s * .5f), sw * 1.2f, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, Offset(s * .45f, s * .22f), Offset(s * .2f, s * .5f), sw * 1.2f, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, Offset(s * .45f, s * .78f), Offset(s * .2f, s * .5f), sw * 1.2f, androidx.compose.ui.graphics.StrokeCap.Round)
        }
        IconKind.Play -> {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(s * .25f, s * .15f); lineTo(s * .85f, s * .5f); lineTo(s * .25f, s * .85f); close()
            }
            drawPath(p, color)
        }
    }
}
