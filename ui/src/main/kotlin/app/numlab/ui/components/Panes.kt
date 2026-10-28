package app.numlab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numlab.domain.format.Fmt
import app.numlab.domain.format.Fmt.fmtE
import app.numlab.domain.interp.InterpMethod
import app.numlab.domain.interp.InterpResult
import app.numlab.domain.roots.RootResult
import app.numlab.domain.roots.RootRow
import app.numlab.domain.roots.explainStep
import app.numlab.ui.R
import app.numlab.ui.theme.MonoStyle
import app.numlab.ui.theme.NumLabTheme

/** Iterations table; tap a row to select it. Also used for rows kept from a failed run. */
@Composable
fun IterationsPane(rows: List<RootRow>, tol: Double, dp: Int, active: Int?, onRow: ((Int) -> Unit)?, maxHeight: Dp = 320.dp) {
    val (cols, cells) = rootTable(tol, rows, dp)
    DataTable(cols, cells, active = active, onRow = onRow, maxHeight = maxHeight, dense = true)
}

/** Lagrange basis terms, or the divided-difference table plus the Newton form. */
@Composable
fun StepsPane(res: InterpResult, dp: Int, maxHeight: Dp = 320.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val (cols, rows) = if (res.method == InterpMethod.LAGRANGE) basisTable(res, dp) else ddTable(res)
        DataTable(cols, rows, maxHeight = maxHeight, dense = true)
        if (res.method == InterpMethod.NEWTON_DD) Block(stringResource(R.string.newton_form), res.polyForm, scroll = true)
    }
}

/** Per-iteration explanation with previous/next (PLAN §4.5). */
@Composable
fun WorkingPane(res: RootResult, index: Int, onIndex: (Int) -> Unit) {
    val w = explainStep(res, index)
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.iteration_n, res.rows[index].n), Modifier.weight(1f))
            RoundBtn(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.cd_prev_iter),
                { onIndex(index - 1) }, enabled = index > 0, container = cs.surfaceVariant)
            RoundBtn(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.cd_next_iter),
                { onIndex(index + 1) }, enabled = index < res.rows.lastIndex, container = cs.surfaceVariant)
        }
        w.lines.forEach { (title, body) -> Block(title, body) }
        w.note?.let { note ->
            val c = NumLabTheme.colors
            Row(Modifier.fillMaxWidth().background(c.goodBg, RoundedCornerShape(16.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = c.good)
                Text(note, color = c.good, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Expanded polynomial and error metrics with the theoretical bound (PLAN §4.4). */
@Composable
fun ErrorsPane(res: InterpResult, emptyText: String = stringResource(R.string.no_metrics)) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Block(stringResource(R.string.p_expanded), res.poly, scroll = true)
        val m = res.metrics
        if (m == null) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Metric(stringResource(R.string.metric_abs), stringResource(R.string.metric_abs_cap), fmtE(m.abs))
        Metric(stringResource(R.string.metric_rel), stringResource(R.string.metric_rel_cap),
            m.rel?.let { "${Fmt.toFixed(it, 4)} %" } ?: "—")
        Metric(stringResource(R.string.metric_arel), stringResource(R.string.metric_arel_cap),
            m.absRel?.let { "${Fmt.toFixed(it, 4)} %" } ?: "—")
        m.bound?.let { b ->
            val holds = m.abs <= b.value * 1.0001 + 1e-12
            Metric(stringResource(R.string.metric_bound), stringResource(R.string.metric_bound_cap, b.k), fmtE(b.value)) {
                if (holds) Badge(stringResource(R.string.within_bound), BadgeKind.OK, icon = Icons.Outlined.Check)
                else Badge(stringResource(R.string.above_bound), BadgeKind.WARN, icon = Icons.Outlined.WarningAmber)
            }
            if (b.extrapolating) {
                Badge(stringResource(R.string.extrapolating), BadgeKind.WARN, icon = Icons.Outlined.WarningAmber)
            }
        }
    }
}

@Composable
private fun Block(title: String, body: String, scroll: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(cs.surfaceVariant, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        Text(body, (if (scroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier).padding(top = 4.dp),
            style = MonoStyle.copy(fontSize = 13.sp, lineHeight = 20.sp), color = cs.onSurface, softWrap = !scroll)
    }
}

@Composable
private fun Metric(label: String, cap: String, value: String, extra: (@Composable () -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(cs.surfaceVariant, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(cap, style = MonoStyle.copy(fontSize = 11.sp), color = cs.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MonoStyle.copy(fontSize = 14.sp))
            extra?.invoke()
        }
    }
}
