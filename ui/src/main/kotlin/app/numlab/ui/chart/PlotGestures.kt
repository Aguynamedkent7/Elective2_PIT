package app.numlab.ui.chart

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.hypot

/** Maps data coordinates to pixels inside the plot area. */
class Mapper(
    val v0: Double, val v1: Double, val ylo: Double, val yhi: Double,
    val l: Float, val t: Float, val iw: Float, val ih: Float,
) {
    fun sx(x: Double) = (l + (x - v0) / (v1 - v0) * iw).toFloat()
    fun sy(y: Double) = (t + (yhi - y) / (yhi - ylo) * ih).toFloat()
    fun unX(px: Float) = v0 + (px - l) / iw * (v1 - v0)
    fun unY(py: Float) = yhi - (py - t) / ih * (yhi - ylo)
}

/** Callbacks the gesture layer drives; read through [State] so the detector never restarts. */
class PlotGestureTarget(
    val mapper: () -> Mapper,
    val points: () -> List<PlotPoint>,
    val lineX: () -> Double?,
    val setView: (Pair<Double, Double>) -> Unit,
    val view: () -> Pair<Double, Double>,
    val lockY: (Pair<Double, Double>?) -> Unit,
    val onDragPoint: ((Int, Double) -> Unit)?,
    val onDragLine: ((Double) -> Unit)?,
    val onTap: (Offset) -> Unit,
    val clearTip: () -> Unit,
)

/** Zooms the x-range about [xm] by [k], rejecting absurd spans. */
fun zoomAbout(view: Pair<Double, Double>, xm: Double, k: Double): Pair<Double, Double>? {
    val a = xm - (xm - view.first) * k
    val b = xm + (view.second - xm) * k
    return if (b - a < 1e-6 || b - a > 1e6) null else a to b
}

/** Pan (one finger), pinch zoom (two), drag points / the evaluation line, tap for a readout. */
fun Modifier.plotGestures(target: State<PlotGestureTarget>, slop: Float, hitRadius: Float): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val g = target.value
        val m = g.mapper()
        val hitPoint = if (g.onDragPoint == null) null else g.points()
            .filter { it.dragId != null }
            .minByOrNull { hypot(m.sx(it.x) - down.position.x, m.sy(it.y) - down.position.y) }
            ?.takeIf { hypot(m.sx(it.x) - down.position.x, m.sy(it.y) - down.position.y) < hitRadius }
        val hitLine = hitPoint == null && g.onDragLine != null &&
            g.lineX()?.let { abs(m.sx(it) - down.position.x) < hitRadius } == true
        if (hitPoint != null || hitLine) {
            g.lockY(m.ylo to m.yhi)
            g.clearTip()
            while (true) {
                val e = awaitPointerEvent()
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
                if (hitPoint != null) g.onDragPoint?.invoke(hitPoint.dragId!!, m.unY(c.position.y))
                else g.onDragLine?.invoke(m.unX(c.position.x).coerceIn(m.v0, m.v1))
                c.consume()
            }
            g.lockY(null)
            return@awaitEachGesture
        }
        val base = g.view()
        var moved = false
        var pinch: Triple<Float, Pair<Double, Double>, Double>? = null
        while (true) {
            val e = awaitPointerEvent()
            val pressed = e.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                val (a, b) = pressed
                val d = abs(a.position.x - b.position.x).coerceAtLeast(1f)
                if (pinch == null) pinch = Triple(d, g.view(), g.mapper().unX((a.position.x + b.position.x) / 2))
                val (d0, v, xm) = pinch
                zoomAbout(v, xm, (d0 / d).toDouble())?.let(g.setView)
                moved = true
                g.clearTip()
            } else if (pinch == null) {
                val dx = pressed[0].position.x - down.position.x
                if (abs(dx) > slop) moved = true
                if (moved) {
                    val sh = -dx / m.iw * (base.second - base.first)
                    g.setView(base.first + sh to base.second + sh)
                    g.clearTip()
                }
            }
            e.changes.forEach { it.consume() }
        }
        if (!moved) g.onTap(down.position)
    }
}
