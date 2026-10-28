package app.numlab.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.numlab.domain.format.Fmt
import app.numlab.domain.roots.RootResult
import app.numlab.ui.R
import app.numlab.ui.motion.LocalReduceMotion
import app.numlab.ui.motion.Motion
import kotlin.math.log10
import kotlin.math.max

/**
 * Headline number that tweens from the previous value (560 ms, easeOutQuart; PLAN §7.4).
 * Announced politely to accessibility services with its final value.
 */
@Composable
fun BigNumber(value: Double, decimals: Int, style: TextStyle, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(1f) }
    var from by remember { mutableDoubleStateOf(0.0) }
    var to by remember { mutableDoubleStateOf(if (reduce) value else 0.0) }
    LaunchedEffect(value) {
        val current = from + (to - from) * progress.value
        if (reduce || !value.isFinite() || !current.isFinite()) {
            from = value; to = value
            return@LaunchedEffect
        }
        from = current
        to = value
        progress.snapTo(0f)
        progress.animateTo(1f, tween(560, easing = Motion.EaseOutQuart))
    }
    val shown = Fmt.fmt(if (to.isFinite()) from + (to - from) * progress.value else value, decimals)
    val final = Fmt.fmt(value, decimals)
    Text(
        shown,
        modifier.semantics { contentDescription = final; liveRegion = LiveRegionMode.Polite },
        style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** Error per iteration on a log scale (prototype `Spark`). */
@Composable
fun Sparkline(values: List<Double?>, color: Color, modifier: Modifier = Modifier) {
    val vs = values.filter { it != null && it.isFinite() }.map { log10(max(it!!, 1e-16)) }
    Canvas(modifier.size(92.dp, 34.dp)) {
        if (vs.size < 2) return@Canvas
        val lo = vs.min()
        val hi = max(vs.max(), lo + 1)
        val pad = 3.dp.toPx()
        fun pt(i: Int) = Offset(i.toFloat() / (vs.size - 1) * (size.width - 2 * pad) + pad,
            (pad + (hi - vs[i]) / (hi - lo) * (size.height - 2 * pad)).toFloat())
        val path = Path().apply { vs.indices.forEach { if (it == 0) moveTo(pt(0).x, pt(0).y) else lineTo(pt(it).x, pt(it).y) } }
        drawPath(path, color.copy(alpha = 0.9f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 3.dp.toPx(), pt(vs.lastIndex))
    }
}

/** "Converged" (green) or "Not converged after N iterations" (amber, not an error; PLAN §8). */
@Composable
fun ConvBadge(res: RootResult, short: Boolean = false) = if (res.converged) {
    Badge(stringResource(R.string.converged), BadgeKind.OK, icon = Icons.Outlined.Check)
} else {
    Badge(
        if (short) stringResource(R.string.not_converged_short) else stringResource(R.string.not_converged, res.iterations),
        BadgeKind.WARN, icon = Icons.Outlined.WarningAmber,
    )
}
