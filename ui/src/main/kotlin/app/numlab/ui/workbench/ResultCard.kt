package app.numlab.ui.workbench

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numlab.domain.format.Fmt
import app.numlab.domain.format.Fmt.fmtE
import app.numlab.domain.interp.InterpMethod
import app.numlab.domain.interp.InterpResult
import app.numlab.domain.roots.RootMethod
import app.numlab.domain.roots.RootResult
import app.numlab.ui.R
import app.numlab.ui.components.Badge
import app.numlab.ui.components.BadgeKind
import app.numlab.ui.components.BigNumber
import app.numlab.ui.components.ConvBadge
import app.numlab.ui.components.ErrorsPane
import app.numlab.ui.components.IterationsPane
import app.numlab.ui.components.Pill
import app.numlab.ui.components.SectionLabel
import app.numlab.ui.components.ShareMenu
import app.numlab.ui.components.Solved
import app.numlab.ui.components.Sparkline
import app.numlab.ui.components.StepsPane
import app.numlab.ui.components.WorkingPane
import app.numlab.ui.motion.LocalReduceMotion
import app.numlab.ui.motion.Motion
import app.numlab.ui.theme.MonoStyle
import app.numlab.ui.theme.NumLabTheme

fun rootMethodShort(m: RootMethod) = when (m) {
    RootMethod.BISECTION -> R.string.method_bisection
    RootMethod.NEWTON -> R.string.method_newton
    RootMethod.SECANT -> R.string.method_secant
}

fun interpMethodShort(m: InterpMethod) = if (m == InterpMethod.LAGRANGE) R.string.method_lagrange else R.string.method_newton_dd

private enum class Pane { TABLE, WORKING }

/** Home result card: headline, badge, sparkline and the inline Iterations/Working panes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultCard(solved: Solved, decimals: Int, onSave: () -> Unit, onCopied: (Int) -> Unit, modifier: Modifier = Modifier) {
    var pane by rememberSaveable { mutableStateOf<Pane?>(null) }
    var sel by rememberSaveable { mutableStateOf<Int?>(null) }
    val cs = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    Surface(modifier.fillMaxWidth(), RoundedCornerShape(28.dp), cs.surface) {
        Column(Modifier.padding(20.dp).animateContentSize(tween(if (reduce) 0 else 260, easing = Motion.Standard))) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    SectionLabel(
                        when (solved) {
                            is Solved.Root -> stringResource(R.string.root_label, stringResource(rootMethodShort(solved.res.method)))
                            is Solved.Interp -> stringResource(R.string.p_label, Fmt.fmt(solved.res.xe, 4),
                                stringResource(interpMethodShort(solved.res.method)))
                        },
                    )
                    BigNumber(
                        if (solved is Solved.Root) solved.res.root else (solved as Solved.Interp).res.pxe, decimals,
                        MonoStyle.copy(fontSize = 38.sp, fontWeight = FontWeight.Medium, color = cs.onSurface),
                        Modifier.padding(top = 4.dp),
                    )
                }
                ShareMenu(solved, onSave, onCopied)
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SummaryChips(solved)
                }
                if (solved is Solved.Root && solved.res.rows.size > 2) {
                    Sparkline(solved.res.rows.map { it.abs }, NumLabTheme.colors.curve)
                }
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val root = solved is Solved.Root
                Pill(stringResource(if (root) R.string.tab_iterations else R.string.tab_steps),
                    { pane = if (pane == Pane.TABLE) null else Pane.TABLE }, active = pane == Pane.TABLE, icon = Icons.Outlined.TableChart)
                Pill(stringResource(if (root) R.string.tab_working else R.string.tab_errors),
                    { pane = if (pane == Pane.WORKING) null else Pane.WORKING }, active = pane == Pane.WORKING,
                    icon = Icons.AutoMirrored.Outlined.Notes)
            }
            pane?.let { p ->
                Column(Modifier.padding(top = 12.dp)) {
                    when (solved) {
                        is Solved.Root -> {
                            val rows = solved.res.rows
                            val k = (sel ?: rows.lastIndex).coerceIn(0, rows.lastIndex)
                            if (p == Pane.TABLE) IterationsPane(rows, solved.res.tol, decimals, k, { sel = it }, maxHeight = 260.dp)
                            else WorkingPane(solved.res, k) { sel = it }
                        }
                        is Solved.Interp ->
                            if (p == Pane.TABLE) StepsPane(solved.res, decimals, maxHeight = 260.dp) else ErrorsPane(solved.res)
                    }
                }
            }
        }
    }
}

/** Convergence badge + "N iterations · last change …", or the interpolation error summary. */
@Composable
fun SummaryChips(solved: Solved) {
    val cs = MaterialTheme.colorScheme
    when (solved) {
        is Solved.Root -> {
            ConvBadge(solved.res)
            Text(stringResource(R.string.iterations_last_change, solved.res.iterations, fmtE(solved.res.finalAbs)),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        is Solved.Interp -> {
            val m = solved.res.metrics
            if (m != null) Badge(stringResource(R.string.abs_error_badge, fmtE(m.abs)), BadgeKind.NEUTRAL)
            else Badge(stringResource(R.string.degree, solved.res.n), BadgeKind.NEUTRAL)
            Text(m?.absRel?.let { stringResource(R.string.percent_off, Fmt.toFixed(it, 3)) } ?: stringResource(R.string.no_reference),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

fun solvedOf(st: WorkbenchState): Solved? = when (st.module) {
    Module.ROOTS -> (st.rootOutcome as? Outcome.Ok)?.takeIf { st.rootSyntax == null && st.root.fx.isNotBlank() }?.let { Solved.Root(it.value) }
    Module.INTERP -> (st.interpOutcome as? Outcome.Ok)?.takeIf { pointCount(st.interp.rows) >= 2 }?.let { Solved.Interp(it.value, st.interp.xEval) }
}

