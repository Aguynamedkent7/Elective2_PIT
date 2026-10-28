package app.numlab.ui.canvas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import app.numlab.domain.format.Fmt.fmt
import app.numlab.domain.interp.InterpResult
import app.numlab.domain.roots.RootMethod
import app.numlab.domain.roots.RootResult
import app.numlab.ui.chart.Curve
import app.numlab.ui.chart.Overlay
import app.numlab.ui.chart.PlotPoint
import app.numlab.ui.chart.PointKind
import app.numlab.ui.chart.domainFor
import app.numlab.ui.motion.LocalReduceMotion
import app.numlab.ui.motion.Motion
import kotlin.math.max

/** What the plot shows for one state of the canvas. */
data class GraphSpec(
    val curves: List<Curve>,
    val points: List<PlotPoint>,
    val overlays: List<Overlay>,
    val domain: Pair<Double, Double>,
    val includeZero: Boolean,
)

/** Plot palette passed in from the theme. */
data class GraphInk(val curve: Color, val hot: Color, val accent: Color, val ink: Color, val ink2: Color)

/**
 * Roots: curve, iteration trail, bracket band (bisection), tangent (Newton) or secant line, and
 * the current iterate, which slides from the previous one (PLAN §7.4). Root marker at the end.
 */
@Composable
fun rootSpec(res: RootResult, k: Int, stepMs: Int, dp: Int, ink: GraphInk): GraphSpec {
    val rows = res.rows
    val cur = rows[k]
    val reduce = LocalReduceMotion.current
    val slide = remember { Animatable(1f) }
    val from = remember { doubleArrayOf(cur.x, cur.fx, cur.lo ?: 0.0, cur.hi ?: 0.0) }
    val to = remember { doubleArrayOf(cur.x, cur.fx, cur.lo ?: 0.0, cur.hi ?: 0.0) }
    LaunchedEffect(k, res) {
        val p = slide.value.toDouble()
        for (i in 0..3) from[i] = from[i] + (to[i] - from[i]) * p
        to[0] = cur.x; to[1] = cur.fx; to[2] = cur.lo ?: 0.0; to[3] = cur.hi ?: 0.0
        if (reduce) { slide.snapTo(1f); return@LaunchedEffect }
        slide.snapTo(0f)
        slide.animateTo(1f, tween(minOf(stepMs, 320), easing = Motion.Standard))
    }
    val p = slide.value.toDouble()
    fun v(i: Int) = from[i] + (to[i] - from[i]) * p
    val pts = rows.take(k).mapIndexed { i, r ->
        PlotPoint(PointKind.ITER, r.x, r.fx, alpha = 0.35f + 0.4f * i / max(1, k))
    }.toMutableList()
    pts += if (k == rows.lastIndex) PlotPoint(PointKind.ROOT, v(0), v(1), "x* ≈ ${fmt(cur.x, minOf(dp, 6))}")
    else PlotPoint(PointKind.DATA, v(0), v(1), "n = ${cur.n}", color = ink.hot)
    val ov = mutableListOf<Overlay>()
    when {
        res.method == RootMethod.BISECTION -> {
            ov += Overlay.Band(v(2), v(3), ink.accent)
            ov += Overlay.VLine(v(0), ink.hot)
        }
        res.method == RootMethod.NEWTON && k >= 1 -> {
            val pr = rows[k - 1]
            ov += Overlay.Seg(pr.x, 0.0, pr.x, pr.fx, ink.ink2, dashed = true, width = 1.4f)
            ov += Overlay.Seg(pr.x, pr.fx, v(0), 0.0, ink.hot)
        }
        res.method == RootMethod.SECANT && k >= 2 -> {
            val p0 = rows[k - 2]
            ov += Overlay.Seg(p0.x, p0.fx, v(0), 0.0, ink.hot)
        }
    }
    val domain = remember(res) { domainFor(listOf(res.root) + rows.map { it.x } + res.used) }
    return GraphSpec(listOf(Curve({ res.f.eval(it) }, ink.curve, 3f)), pts, ov, domain, true)
}

/** Interpolation: P(x), dashed true f(x), draggable nodes, draggable evaluation line. */
fun interpSpec(res: InterpResult, ink: GraphInk, domain: Pair<Double, Double>): GraphSpec {
    val pts = res.points.mapIndexed { i, p -> PlotPoint(PointKind.DATA, p.x, p.y, dragId = i) }.toMutableList()
    pts += PlotPoint(PointKind.TARGET, res.xe, res.pxe, "P(${fmt(res.xe, 3)})")
    res.metrics?.let { pts += PlotPoint(PointKind.TRUE, res.xe, it.yTrue) }
    val curves = mutableListOf(Curve(res.p, ink.curve, 3f, fit = res.fTrue == null, label = "P(x)"))
    res.fTrue?.let { f -> curves += Curve({ f.eval(it) }, ink.ink2, 2f, dashed = true, probe = false) }
    return GraphSpec(curves, pts, listOf(Overlay.VLine(res.xe, ink.hot, draggable = true)), domain, false)
}
