package app.numlab.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.numlab.ui.R
import app.numlab.ui.components.RoundBtn
import app.numlab.ui.motion.LocalReduceMotion
import app.numlab.ui.motion.Motion
import app.numlab.ui.theme.NumLabTheme
import kotlin.math.abs
import kotlin.math.max

/** Readout shown after a tap (prototype tooltip). */
data class Tip(val px: Float, val py: Float, val x: Double, val y: Double, val title: String)

/**
 * Interactive function plot: pan, pinch-zoom (x), tap readout, draggable data points and
 * evaluation line, root marker with pop + pulse, and a one-time draw-on of the first curve.
 * A Compose port of the prototype's `chart2.jsx` (decision D3, see docs/DECISIONS.md).
 */
@Composable
fun Plot(
    curves: List<Curve>,
    points: List<PlotPoint>,
    overlays: List<Overlay>,
    domain: Pair<Double, Double>,
    description: String,
    modifier: Modifier = Modifier,
    padTop: Dp = 10.dp,
    padBottom: Dp = 22.dp,
    includeZero: Boolean = true,
    interactive: Boolean = true,
    controls: Boolean = true,
    controlsTop: Dp = 8.dp,
    onDragPoint: ((Int, Double) -> Unit)? = null,
    onDragLine: ((Double) -> Unit)? = null,
) {
    val reduce = LocalReduceMotion.current
    var view by remember(domain) { mutableStateOf(domain) }
    var lockY by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var tip by remember { mutableStateOf<Tip?>(null) }
    val drawOn = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) drawOn.animateTo(1f, tween(700, 150, Motion.Standard)) }
    val root = points.firstOrNull { it.kind == PointKind.ROOT || it.kind == PointKind.TARGET }
    val pop = remember(root?.kind, root?.label) { Animatable(if (reduce) 1f else 0f) }
    val ring = remember(root?.kind, root?.label) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(root?.kind, root?.label) {
        if (reduce || root == null) return@LaunchedEffect
        pop.animateTo(1f, tween(500, if (drawOn.value < 1f) 700 else 0, Motion.Spring))
        ring.animateTo(1f, tween(900, easing = Motion.Decelerate))
    }
    val text = rememberTextMeasurer()
    val colors = PlotColors(MaterialTheme.colorScheme, NumLabTheme.colors)

    BoxWithConstraints(modifier.semantics { contentDescription = description }) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val l = with(density) { 38.dp.toPx() }
        val r = with(density) { 14.dp.toPx() }
        val t = with(density) { padTop.toPx() }
        val b = with(density) { padBottom.toPx() }
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val iw = max(10f, w - l - r)
        val ih = max(10f, h - t - b)
        val n = (iw / 1.3f).toInt().coerceIn(80, 600)
        val samples = remember(view, curves, n) { curves.map { sample(it.f, view.first, view.second, n) } }
        val (ylo, yhi) = lockY ?: yRange(samples, curves.map { it.fit }, points, view.first, view.second, includeZero)
        val m = Mapper(view.first, view.second, ylo, yhi, l, t, iw, ih)
        val paths = remember(samples, m.ylo, m.yhi, m.v0, m.v1, iw, ih) { samples.map { s -> toPath(s, m) } }

        val target = rememberUpdatedState(
            PlotGestureTarget(
                mapper = { m }, points = { points }, lineX = { overlays.filterIsInstance<Overlay.VLine>().firstOrNull { it.draggable }?.x },
                setView = { view = it }, view = { view }, lockY = { lockY = it },
                onDragPoint = onDragPoint, onDragLine = onDragLine,
                onTap = { p -> tip = probe(curves, m, p) },
                clearTip = { tip = null },
            ),
        )
        val gestures = if (interactive) Modifier.plotGestures(target, with(density) { 3.dp.toPx() }, with(density) { 26.dp.toPx() }) else Modifier
        Canvas(Modifier.fillMaxSize().then(gestures)) {
            drawGridAndAxes(m, colors, text)
            drawOverlays(overlays, m)
            paths.forEachIndexed { i, p ->
                val c = curves[i]
                val path = if (i == 0 && drawOn.value < 1f) trim(p, drawOn.value) else p
                drawCurve(path, c, m)
            }
            drawPoints(points, m, colors, pop.value, ring.value)
            drawLabels(points, m, colors, text, size.width)
            tip?.let { drawTip(it, m, colors, text) }
        }
        if (controls) {
            Column(Modifier.align(Alignment.TopEnd).padding(top = controlsTop, end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val glass = NumLabTheme.colors.glass
                RoundBtn(Icons.Outlined.Add, stringResource(R.string.cd_zoom_in), {
                    zoomAbout(view, (view.first + view.second) / 2, 0.7)?.let { view = it }
                }, container = glass)
                RoundBtn(Icons.Outlined.Remove, stringResource(R.string.cd_zoom_out), {
                    zoomAbout(view, (view.first + view.second) / 2, 1.4)?.let { view = it }
                }, container = glass)
                RoundBtn(Icons.Outlined.Refresh, stringResource(R.string.cd_reset_view), { view = domain; tip = null }, container = glass)
            }
        }
    }
}

private fun probe(curves: List<Curve>, m: Mapper, p: Offset): Tip? {
    if (p.x < m.l || p.x > m.l + m.iw) return null
    val c = curves.firstOrNull { it.probe } ?: return null
    val x = m.unX(p.x)
    val y = sampleAt(c.f, x)
    return if (y.isFinite()) Tip(p.x, m.sy(y), x, y, c.label) else null
}

/** Polyline through finite samples, broken at gaps and at jumps over 4× the y-range (poles). */
private fun toPath(s: DoubleArray, m: Mapper): Path {
    val path = Path()
    var pen = false
    var prev = Double.NaN
    val span = m.yhi - m.ylo
    for (i in 0 until s.size / 2) {
        val y = s[2 * i + 1]
        if (!y.isFinite()) { pen = false; prev = Double.NaN; continue }
        if (prev.isFinite() && abs(y - prev) > span * 4) pen = false
        val px = m.sx(s[2 * i])
        val py = m.sy(y).coerceIn(-4000f, 4000f)
        if (pen) path.lineTo(px, py) else path.moveTo(px, py)
        pen = true
        prev = y
    }
    return path
}

private fun trim(p: Path, f: Float): Path {
    val pm = PathMeasure().apply { setPath(p, false) }
    return Path().also { pm.getSegment(0f, pm.length * f, it, true) }
}
