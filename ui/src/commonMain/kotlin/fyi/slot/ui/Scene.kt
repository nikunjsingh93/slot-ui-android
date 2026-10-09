package fyi.slot.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

// Constants below are slot's own (crates/slot-ui/src/{shelf,slot_chrome}.rs and slot/src/app.rs).
const val SIDE_SCALE = 0.78f
const val SIDE_ALPHA = 0.55f

/** Spring stiffness of the shelf: critically damped, so no overshoot. */
const val SHELF_OMEGA = 16f

/** How far neighbours slide away while a cart is being inserted (in design units). */
const val PART = 130f

const val INSERT_S = 0.73f
const val SEATED_AT = INSERT_S - 0.28f
const val EJECT_S = SEATED_AT
const val EJECT_HOLD_S = 0.35f

private const val CATCH_IN = 0.42f
private const val CATCH_OUT = 0.62f
private const val CREEP = 0.03f

/** Screen geometry. [u] is pixels per slot design unit; slot's native canvas is 640x480. */
class Metrics(val w: Float, val h: Float, val cartFactor: Float = 1f) {
    val portrait = w < h * 1.05f
    val u: Float = if (portrait) w / 400f else h / 480f
    val cx = w / 2f

    val bandH = 58f * u
    val bandTop = h - bandH
    val topBar = (if (portrait) 78f else 46f) * u

    // Slot (the mouth) geometry, mirrored from slot_chrome.rs
    val lip = 2f * u
    val mouthW = 254f * u
    val bayW = mouthW + 18f * u
    val bayX = cx - bayW / 2f
    val mouthX = cx - mouthW / 2f
    val bayY = bandTop + lip
    val slitH = 9f * u
    val slitY = bayY + 5f * u
    val recessH = 42f * u
    val scoopY = slitY + slitH
    val scoopD = recessH - (scoopY - bayY)
    val scoopW = mouthW * 0.88f
    val rim = 2f * u
    val seatedY = bayY + 4f * u
    /** The strip between the top bar and the slot where carts stand; the title sits in the strip below it. */
    val titleH = 40f * u
    val shelfTop = topBar
    val shelfBottom = bandTop - titleH
    val availH = shelfBottom - shelfTop - 8f * u

    /** Slot draws GBA-sized carts 360 units wide and the taller ones a little smaller; Settings > Cart size scales that. */
    fun cartScale(spec: CartSpec): Float =
        (min(1.5f * u, 340f * u / spec.h) * cartFactor).coerceAtMost(min((w * .92f) / spec.w, availH / spec.h))

    /** The common baseline (cart foot): carts are centred in the strip above the title. */
    fun foot(spec: CartSpec): Float {
        val h0 = spec.h * cartScale(spec)
        return (shelfTop + shelfBottom) / 2f + h0 / 2f
    }

    fun scoopDepth(dx: Float): Float = scoopD * (1f - (abs(dx) / (scoopW / 2f)).pow(4f)).coerceAtLeast(0f)
}

/** Runs [block] inside an offscreen layer when [alpha] < 1, so overlapping parts of one cart don't show through. */
inline fun DrawScope.layered(alpha: Float, bounds: Rect, block: DrawScope.() -> Unit) {
    if (alpha >= .995f) { block(); return }
    if (alpha <= .004f) return
    drawIntoCanvas { c ->
        c.saveLayer(bounds, Paint().also { it.alpha = alpha })
        block()
        c.restore()
    }
}

fun smootherstep(u: Float): Float { val x = u.coerceIn(0f, 1f); return x * x * x * (x * (x * 6f - 15f) + 10f) }

/** slot's `travel`: a fast approach, a slow "catch" as the pins meet the contacts, then the push home. */
fun travel(seat: Float, catch: Float): Float = when {
    seat < CATCH_IN -> catch * smootherstep(seat / CATCH_IN)
    seat < CATCH_OUT -> catch + CREEP * (seat - CATCH_IN) / (CATCH_OUT - CATCH_IN)
    else -> {
        val caught = catch + CREEP
        caught + (1f - caught) * smootherstep((seat - CATCH_OUT) / (1f - CATCH_OUT))
    }
}

/** Where a shelf cart sits: [cx] is its centre x, [footY] its baseline. */
class CartPose(val cx: Float, val footY: Float, val scale: Float, val alpha: Float)

/** A cart [offset] places away from the selection (fractional while scrolling); [recede] pushes neighbours aside during insertion. */
fun shelfPose(m: Metrics, spec: CartSpec, offset: Float, recede: Float = 0f): CartPose {
    val s0 = m.cartScale(spec)
    val cw = spec.w * s0
    val t = min(abs(offset), 1f)
    val scale = s0 * (1f + (SIDE_SCALE - 1f) * t)
    val alpha = (1f + (SIDE_ALPHA - 1f) * t) * (1f - recede.coerceIn(0f, 1f))
    val away = sign(offset) * (1f + abs(offset))
    return CartPose(m.cx + offset * cw + away * PART * m.u * recede, m.foot(spec), scale, alpha)
}

/** The selected cart on its way into (or out of) the slot. [restCx] is where it sat on the shelf. */
fun seatedPose(m: Metrics, spec: CartSpec, restCx: Float, seat: Float): CartPose {
    val s0 = m.cartScale(spec)
    val w0 = spec.w * s0
    val foot0 = m.foot(spec)
    val sw = spec.w * m.u
    val sh = spec.h * m.u
    val seatedFoot = m.seatedY + sh
    val catch = ((m.bandTop - foot0) / (seatedFoot - foot0)).coerceIn(0.05f, 0.95f)
    val tr = travel(seat.coerceIn(0f, 1f), catch)
    val k = min(tr / catch, 1f)
    val cw = w0 + (sw - w0) * k
    val centre = restCx + (m.cx - restCx) * k
    val foot = foot0 + (seatedFoot - foot0) * tr
    return CartPose(centre, foot, cw / spec.w, 1f)
}

fun DrawScope.drawCartPose(painter: CartPainter, pose: CartPose, pad: Float) {
    val s = pose.scale
    val w = painter.w * s
    val h = painter.h * s
    val left = pose.cx - w / 2f
    val top = pose.footY - h
    layered(pose.alpha, Rect(left - pad, top - pad, left + w + pad, top + h + pad * 1.5f)) {
        translate(left, top) {
            scale(s, s, pivot = Offset.Zero) { with(painter) { paint() } }
        }
    }
}

// --- the mouth -------------------------------------------------------------------------------------

/** Everything of the slot that sits *behind* an inserted cart (slot_chrome.rs `draw_slot_back`). */
fun DrawScope.drawSlotBack(m: Metrics) {
    drawRect(SlotColors.Housing, Offset(m.bayX, m.bandTop), Size(m.bayW, m.lip))
    drawRect(SlotColors.Edge, Offset(m.mouthX, m.bandTop), Size(m.mouthW, m.lip))
    drawRect(SlotColors.Recess, Offset(m.bayX, m.bayY), Size(m.bayW, m.recessH))
    drawRect(SlotColors.Opening, Offset(m.mouthX, m.slitY), Size(m.mouthW, m.slitH))
    val hw = m.scoopW / 2f
    val step = max(1f, m.u * .5f)
    var x = -hw
    while (x < hw) {
        drawRect(SlotColors.Opening, Offset(m.cx + x, m.scoopY), Size(step + .5f, m.scoopDepth(x + step / 2f)))
        x += step
    }
}

/** The housing that covers the lower part of an inserted cart, with the curved basin cut out of it. */
fun DrawScope.drawSlotFront(m: Metrics, front: Path) {
    drawPath(front, SlotColors.Housing)
    drawRect(
        Brush.verticalGradient(0f to Color.White.copy(alpha = .05f), .6f to Color.Transparent, startY = m.bandTop, endY = m.bandTop + m.bandH),
        Offset(0f, m.bandTop), Size(m.w, m.bandH),
    )
    // lit rim along the basin
    val hw = m.scoopW / 2f
    val p = Path()
    val step = max(1f, m.u * .5f)
    var x = -hw
    var first = true
    while (x <= hw) {
        val y = m.scoopY + m.scoopDepth(x)
        if (first) { p.moveTo(m.cx + x, y); first = false } else p.lineTo(m.cx + x, y)
        x += step
    }
    drawPath(p, SlotColors.Edge, style = Stroke(m.rim))
    drawLine(SlotColors.Edge, Offset(0f, m.bandTop + m.lip / 2f), Offset(m.bayX, m.bandTop + m.lip / 2f), strokeWidth = m.lip)
    drawLine(SlotColors.Edge, Offset(m.bayX + m.bayW, m.bandTop + m.lip / 2f), Offset(m.w, m.bandTop + m.lip / 2f), strokeWidth = m.lip)
}

/** The front-housing shape: the whole band minus the bay window above the basin and the basin itself. */
fun buildSlotFront(m: Metrics): Path {
    val band = Path().apply { addRect(Rect(0f, m.bandTop, m.w, m.h)) }
    val window = Path().apply { addRect(Rect(m.bayX, m.bandTop, m.bayX + m.bayW, m.scoopY)) }
    val hw = m.scoopW / 2f
    val basin = Path()
    val step = max(1f, m.u * .5f)
    basin.moveTo(m.cx - hw, m.scoopY)
    var x = -hw
    while (x <= hw) { basin.lineTo(m.cx + x, m.scoopY + m.scoopDepth(x)); x += step }
    basin.lineTo(m.cx + hw, m.scoopY)
    basin.close()
    return Path.combine(PathOperation.Difference, Path.combine(PathOperation.Difference, band, window), basin)
}

fun DrawScope.drawShelfBackdrop(m: Metrics) {
    drawRect(SlotColors.Recess)
    drawRect(
        Brush.radialGradient(
            0f to SlotColors.Housing.copy(alpha = .55f), 1f to Color.Transparent,
            center = Offset(m.cx, m.h * .42f), radius = m.w * .55f,
        ),
    )
    drawVignette()
}
