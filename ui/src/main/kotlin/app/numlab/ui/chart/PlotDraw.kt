package app.numlab.ui.chart

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numlab.domain.format.Fmt
import app.numlab.ui.theme.Geist
import app.numlab.ui.theme.GeistMono
import app.numlab.ui.theme.NumLabColors
import kotlin.math.max
import kotlin.math.min

/** Theme colours the plot draws with, resolved once per composition. */
class PlotColors(cs: ColorScheme, c: NumLabColors) {
    val line = cs.outline
    val ink = cs.onSurface
    val ink2 = cs.onSurfaceVariant
    val bg = cs.background
    val accent = cs.primary
    val hot = c.hot
    val tick = TextStyle(fontFamily = GeistMono, fontSize = 10.sp, color = ink2)
    val label = TextStyle(fontFamily = Geist, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ink)
    val tipText = TextStyle(fontFamily = GeistMono, fontSize = 11.sp, color = bg)
}

private val DASH = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))

fun DrawScope.drawGridAndAxes(m: Mapper, c: PlotColors, text: TextMeasurer) {
    val (xt, xs) = ticks(m.v0, m.v1, max(3, (m.iw / 72.dp.toPx()).toInt()))
    val (yt, ys) = ticks(m.ylo, m.yhi, max(3, (m.ih / 46.dp.toPx()).toInt()))
    val grid = c.line.copy(alpha = 0.7f)
    xt.forEach { v -> drawLine(grid, Offset(m.sx(v), m.t), Offset(m.sx(v), m.t + m.ih), 1.dp.toPx()) }
    yt.forEach { v -> drawLine(grid, Offset(m.l, m.sy(v)), Offset(m.l + m.iw, m.sy(v)), 1.dp.toPx()) }
    val axisY = if (m.ylo < 0 && m.yhi > 0) m.sy(0.0) else m.t + m.ih
    drawLine(c.ink2, Offset(m.l, axisY), Offset(m.l + m.iw, axisY), 1.4f.dp.toPx())
    xt.forEach { v ->
        val r = text.measure(tickLabel(v, xs), c.tick)
        drawText(r, topLeft = Offset(m.sx(v) - r.size.width / 2, m.t + m.ih + 4.dp.toPx()))
    }
    yt.forEach { v ->
        val r = text.measure(tickLabel(v, ys), c.tick)
        drawText(r, topLeft = Offset(m.l - 6.dp.toPx() - r.size.width, m.sy(v) - r.size.height / 2))
    }
}

fun DrawScope.drawOverlays(overlays: List<Overlay>, m: Mapper) = clipRect(m.l, m.t, m.l + m.iw, m.t + m.ih) {
    overlays.forEach { o ->
        when (o) {
            is Overlay.Band -> drawRect(o.color.copy(alpha = o.alpha), Offset(m.sx(o.x0), m.t),
                Size(max(1f, m.sx(o.x1) - m.sx(o.x0)), m.ih))
            is Overlay.Seg -> drawLine(o.color, Offset(m.sx(o.x1), m.sy(o.y1)), Offset(m.sx(o.x2), m.sy(o.y2)),
                o.width.dp.toPx(), StrokeCap.Round, if (o.dashed) DASH else null)
            is Overlay.VLine -> {
                val x = m.sx(o.x)
                drawLine(o.color.copy(alpha = 0.9f), Offset(x, m.t), Offset(x, m.t + m.ih), o.width.dp.toPx(),
                    pathEffect = if (o.dashed) DASH else null)
            }
        }
    }
}

fun DrawScope.drawCurve(p: Path, c: Curve, m: Mapper) = clipRect(m.l, m.t, m.l + m.iw, m.t + m.ih) {
    drawPath(p, c.color, style = Stroke(c.width.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
        pathEffect = if (c.dashed) PathEffect.dashPathEffect(floatArrayOf(18f, 15f)) else null))
}

fun DrawScope.drawPoints(points: List<PlotPoint>, m: Mapper, c: PlotColors, pop: Float, ring: Float) =
    clipRect(m.l, m.t, m.l + m.iw, m.t + m.ih) {
        points.forEach { p ->
            val o = Offset(m.sx(p.x), m.sy(p.y))
            val col = p.color
            when (p.kind) {
                PointKind.ITER -> drawCircle((col ?: c.ink2).copy(alpha = p.alpha), 3.5f.dp.toPx(), o)
                PointKind.TRUE -> drawCircle(col ?: c.ink, 5.5f.dp.toPx(), o, style = Stroke(2.dp.toPx()))
                PointKind.DATA -> {
                    if (p.dragId != null) drawCircle((col ?: c.accent).copy(alpha = 0.16f), 13.dp.toPx(), o)
                    drawCircle(c.bg, 7.1f.dp.toPx(), o)
                    drawCircle(col ?: c.accent, 6.dp.toPx(), o)
                }
                PointKind.ROOT, PointKind.TARGET -> {
                    if (ring in 0.001f..0.999f) {
                        val a = if (ring < 0.08f) ring / 0.08f * 0.9f else 0.9f * (1 - ring)
                        drawCircle(c.hot.copy(alpha = a), 9.dp.toPx() * (0.7f + 2.7f * ring), o, style = Stroke(2.dp.toPx()))
                    }
                    val s = pop
                    drawCircle(c.hot.copy(alpha = 0.2f), 13.dp.toPx() * s, o)
                    drawCircle(c.bg, 7.6f.dp.toPx() * s, o)
                    drawCircle(c.hot, 6.5f.dp.toPx() * s, o)
                }
            }
        }
    }

fun DrawScope.drawLabels(points: List<PlotPoint>, m: Mapper, c: PlotColors, text: TextMeasurer, width: Float) {
    points.forEach { p ->
        val label = p.label ?: return@forEach
        val x = m.sx(p.x)
        if (x < m.l || x > m.l + m.iw) return@forEach
        val r = text.measure(label, c.label)
        val left = x > width - 130.dp.toPx()
        val tx = if (left) x - 12.dp.toPx() - r.size.width else x + 12.dp.toPx()
        val ty = max(m.t + 2.dp.toPx(), m.sy(p.y) - 12.dp.toPx() - r.size.height)
        val pad = 3.dp.toPx()
        drawRoundRect(c.bg.copy(alpha = 0.85f), Offset(tx - pad, ty - pad / 2),
            Size(r.size.width + 2 * pad, r.size.height + pad), CornerRadius(6.dp.toPx()))
        drawText(r, topLeft = Offset(tx, ty))
    }
}

fun DrawScope.drawTip(tip: Tip, m: Mapper, c: PlotColors, text: TextMeasurer) {
    drawLine(c.ink.copy(alpha = 0.5f), Offset(tip.px, m.t), Offset(tip.px, m.t + m.ih), 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    drawCircle(c.bg, 4.5f.dp.toPx(), Offset(tip.px, tip.py))
    drawCircle(c.ink, 4.5f.dp.toPx(), Offset(tip.px, tip.py), style = Stroke(2.dp.toPx()))
    val body = "${tip.title}\nx = ${Fmt.fmt(tip.x, 5)}\ny = ${Fmt.fmt(tip.y, 5)}"
    val r = text.measure(body, c.tipText)
    val pad = 8.dp.toPx()
    val bw = r.size.width + 2 * pad
    val bh = r.size.height + 2 * pad
    val left = min(size.width - bw - 6.dp.toPx(), max(6.dp.toPx(), tip.px + 12.dp.toPx()))
    val top = min(size.height - bh - 6.dp.toPx(), max(m.t, tip.py - bh - 8.dp.toPx()))
    drawRoundRect(c.ink, Offset(left, top), Size(bw, bh), CornerRadius(12.dp.toPx()))
    drawText(r, topLeft = Offset(left + pad, top + pad))
}

