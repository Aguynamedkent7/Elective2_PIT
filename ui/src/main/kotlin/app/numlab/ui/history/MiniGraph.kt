package app.numlab.ui.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.numlab.domain.format.Fmt.fmt
import app.numlab.ui.R
import app.numlab.ui.chart.Curve
import app.numlab.ui.chart.Plot
import app.numlab.ui.chart.PlotPoint
import app.numlab.ui.chart.PointKind
import app.numlab.ui.chart.domainFor
import app.numlab.ui.components.Solved
import app.numlab.ui.theme.NumLabTheme

/** Static graph for a history card: no pan/drag so the list keeps scrolling; zoom buttons stay. */
@Composable
fun MiniGraph(solved: Solved, modifier: Modifier = Modifier) {
    val curve = NumLabTheme.colors.curve
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    when (solved) {
        is Solved.Root -> {
            val r = solved.res
            val pts = r.rows.map { PlotPoint(PointKind.ITER, it.x, it.fx, alpha = 0.5f) } +
                PlotPoint(PointKind.ROOT, r.root, r.fRoot, "x* ≈ ${fmt(r.root, 5)}")
            val domain = remember(r) { domainFor(listOf(r.root) + r.rows.map { it.x } + r.used) }
            Plot(listOf(Curve({ r.f.eval(it) }, curve, 2.8f)), pts, emptyList(), domain,
                stringResource(R.string.graph_a11y_root, fmt(r.root, 6)), modifier, interactive = false)
        }
        is Solved.Interp -> {
            val r = solved.res
            val pts = r.points.map { PlotPoint(PointKind.DATA, it.x, it.y) } +
                PlotPoint(PointKind.TARGET, r.xe, r.pxe, "P(${fmt(r.xe, 3)})") +
                listOfNotNull(r.metrics?.let { PlotPoint(PointKind.TRUE, r.xe, it.yTrue) })
            val curves = listOf(Curve(r.p, curve, 2.8f, fit = r.fTrue == null, label = "P(x)")) +
                listOfNotNull(r.fTrue?.let { f -> Curve({ f.eval(it) }, ink2, 2f, dashed = true, probe = false) })
            val domain = remember(r) { domainFor(r.points.map { it.x }, 0.3) }
            Plot(curves, pts, emptyList(), domain, stringResource(R.string.graph_a11y_interp, fmt(r.xe, 3), fmt(r.pxe, 6)),
                modifier, includeZero = false, interactive = false)
        }
    }
}
