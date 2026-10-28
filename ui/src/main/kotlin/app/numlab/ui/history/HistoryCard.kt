package app.numlab.ui.history

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numlab.domain.format.Fmt
import app.numlab.domain.format.Fmt.fmtE
import app.numlab.domain.interp.InterpMethod
import app.numlab.domain.model.RunInput
import app.numlab.domain.roots.RootMethod
import app.numlab.ui.R
import app.numlab.ui.components.ErrorBox
import app.numlab.ui.components.ErrorsPane
import app.numlab.ui.components.IterationsPane
import app.numlab.ui.components.Solved
import app.numlab.ui.components.Sparkline
import app.numlab.ui.components.StepsPane
import app.numlab.ui.components.WorkingPane
import app.numlab.ui.motion.LocalReduceMotion
import app.numlab.ui.motion.Motion
import app.numlab.ui.theme.Bricolage
import app.numlab.ui.theme.MonoStyle
import app.numlab.ui.theme.NumLabTheme
import app.numlab.ui.theme.TNUM
import app.numlab.ui.workbench.interpMethodShort
import app.numlab.ui.workbench.rootMethodShort

/** Card callbacks. */
class CardActions(
    val pin: () -> Unit,
    val open: (RunInput) -> Unit,
    val openGraph: (RunInput) -> Unit,
    val menu: @Composable (Solved?) -> Unit,
)

private enum class View { GRAPH, TABLE, WORKING }

/** Run with the method swapped (the "Try" chips). */
private fun RunInput.withMethod(m: String): RunInput = when (this) {
    is RunInput.Root -> RunInput.Root(input.copy(method = RootMethod.valueOf(m)))
    is RunInput.Interp -> RunInput.Interp(input.copy(method = InterpMethod.valueOf(m)))
}

private fun RunInput.methodName() = when (this) {
    is RunInput.Root -> input.method.name
    is RunInput.Interp -> input.method.name
}

private fun methodLabel(m: String) = RootMethod.entries.firstOrNull { it.name == m }?.let(::rootMethodShort)
    ?: interpMethodShort(InterpMethod.valueOf(m))

@Composable
private fun tint(m: String): Pair<Color, Color> {
    val c = NumLabTheme.colors
    return when (m) {
        "BISECTION" -> c.bisectionBg to c.bisectionInk
        "NEWTON" -> c.newtonBg to c.newtonInk
        "SECANT" -> c.secantBg to c.secantInk
        "LAGRANGE" -> c.lagrangeBg to c.lagrangeInk
        else -> c.newtonDdBg to c.newtonDdInk
    }
}

/** Notebook card (PLAN §6.4), tinted by method. "Try" previews another method inline. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryCard(card: Card, whenLabel: String, decimals: Int, a: CardActions) {
    val item = card.item
    var tryM by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var view by rememberSaveable(item.id) { mutableStateOf<View?>(null) }
    var sel by rememberSaveable(item.id) { mutableStateOf<Int?>(null) }
    val run = tryM?.let { item.run.withMethod(it) } ?: item.run
    val result = if (tryM == null) card.result else remember(run) { solve(run) }
    val solved = result.getOrNull()
    val (bg, ink) = tint(run.methodName())
    val reduce = LocalReduceMotion.current
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), bg, ink) {
        Column(Modifier.padding(20.dp).animateContentSize(tween(if (reduce) 0 else 260, easing = Motion.Standard))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(methodLabel(run.methodName())), ink)
                Text(whenLabel, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                val spin by animateFloatAsState(if (item.pinned) 0f else 45f, tween(if (reduce) 0 else 280, easing = Motion.Spring), label = "pin")
                IconButton(a.pin) {
                    Icon(if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        stringResource(if (item.pinned) R.string.cd_unpin else R.string.cd_pin),
                        Modifier.graphicsLayer { rotationZ = spin; val s = 1f + (45f - spin) / 45f * 0.1f; scaleX = s; scaleY = s },
                        tint = ink.copy(alpha = if (item.pinned) 1f else 0.6f))
                }
                a.menu(card.result.getOrNull())
            }
            Text(
                when (run) {
                    is RunInput.Root -> stringResource(R.string.card_fx, run.input.fx)
                    is RunInput.Interp -> stringResource(R.string.card_points,
                        (solved as? Solved.Interp)?.res?.points?.size ?: run.input.rows.size, run.input.xEval)
                },
                Modifier.padding(top = 8.dp), style = MonoStyle.copy(fontSize = 13.sp), color = ink.copy(alpha = 0.75f), maxLines = 1,
            )
            result.exceptionOrNull()?.let { ErrorBox(it.message.orEmpty(), Modifier.padding(top = 12.dp)) }
            if (solved != null) {
                Summary(solved, ink)
                Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val root = solved is Solved.Root
                    ViewChip(R.string.tab_graph, Icons.AutoMirrored.Outlined.ShowChart, view == View.GRAPH, ink) { view = if (view == View.GRAPH) null else View.GRAPH }
                    ViewChip(if (root) R.string.tab_table else R.string.tab_steps, Icons.Outlined.TableChart, view == View.TABLE, ink) {
                        view = if (view == View.TABLE) null else View.TABLE
                    }
                    ViewChip(if (root) R.string.tab_working else R.string.tab_errors, Icons.AutoMirrored.Outlined.Notes, view == View.WORKING, ink) {
                        view = if (view == View.WORKING) null else View.WORKING
                    }
                }
                view?.let { v ->
                    Surface(Modifier.fillMaxWidth().padding(top = 12.dp), RoundedCornerShape(16.dp), MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.onSurface) {
                        Box(Modifier.padding(10.dp)) {
                            when (v) {
                                View.GRAPH -> MiniGraph(solved, Modifier.fillMaxWidth().height(210.dp))
                                View.TABLE -> when (solved) {
                                    is Solved.Root -> IterationsPane(solved.res.rows, solved.res.tol, decimals,
                                        (sel ?: solved.res.rows.lastIndex).coerceIn(0, solved.res.rows.lastIndex), { sel = it }, maxHeight = 250.dp)
                                    is Solved.Interp -> StepsPane(solved.res, decimals, maxHeight = 250.dp)
                                }
                                View.WORKING -> when (solved) {
                                    is Solved.Root -> WorkingPane(solved.res, (sel ?: solved.res.rows.lastIndex).coerceIn(0, solved.res.rows.lastIndex)) { sel = it }
                                    is Solved.Interp -> ErrorsPane(solved.res, stringResource(R.string.no_metrics_history))
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.try_label), style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.6f))
                val methods = if (item.run is RunInput.Root) RootMethod.entries.map { it.name } else InterpMethod.entries.map { it.name }
                methods.filter { it != item.run.methodName() }.forEach { m ->
                    ViewChip(methodLabel(m), Icons.Outlined.Bolt, tryM == m, ink) { tryM = if (tryM == m) null else m }
                }
                }
                Surface({ a.open(run) }, shape = CircleShape, color = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.open), fontWeight = FontWeight.SemiBold)
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(solved: Solved, ink: Color) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.fmt(if (solved is Solved.Root) solved.res.root else (solved as Solved.Interp).res.pxe, 6),
                style = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, lineHeight = 44.sp,
                    fontFeatureSettings = TNUM), maxLines = 1)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically) {
                val small = MaterialTheme.typography.bodySmall
                when (solved) {
                    is Solved.Root -> {
                        Chip(stringResource(if (solved.res.converged) R.string.converged else R.string.not_converged_short), ink)
                        Text(stringResource(R.string.iterations_last_change, solved.res.iterations, fmtE(solved.res.finalAbs)), style = small, color = ink.copy(alpha = 0.7f))
                    }
                    is Solved.Interp -> {
                        Chip(stringResource(R.string.degree, solved.res.n), ink)
                        val m = solved.res.metrics
                        Text(
                            when {
                                m == null -> stringResource(R.string.no_error_reference)
                                m.bound != null -> stringResource(R.string.card_error_bound, fmtE(m.abs), fmtE(m.bound!!.value))
                                else -> stringResource(R.string.card_error, fmtE(m.abs))
                            },
                            style = small, color = ink.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
        if (solved is Solved.Root && solved.res.rows.size > 2) Sparkline(solved.res.rows.map { it.abs }, ink)
    }
}

@Composable
private fun Chip(text: String, ink: Color) = Text(
    text, Modifier.background(ink.copy(alpha = 0.15f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = LocalContentColor.current,
)

@Composable
private fun ViewChip(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, ink: Color, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(onClick, shape = CircleShape, color = if (active) cs.onSurface else ink.copy(alpha = 0.11f),
        contentColor = if (active) cs.surface else ink) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.height(16.dp))
            Text(stringResource(label), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
    }
}
