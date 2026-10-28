package app.numlab.ui.canvas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numlab.domain.format.Fmt
import app.numlab.domain.interp.InterpResult
import app.numlab.domain.roots.RootResult
import app.numlab.ui.R
import app.numlab.ui.components.BigNumber
import app.numlab.ui.components.ErrorsPane
import app.numlab.ui.components.IterationsPane
import app.numlab.ui.components.RoundBtn
import app.numlab.ui.components.SectionLabel
import app.numlab.ui.components.StepsPane
import app.numlab.ui.components.WorkingPane
import app.numlab.ui.motion.LocalReduceMotion
import app.numlab.ui.motion.Motion
import app.numlab.ui.theme.MonoStyle
import app.numlab.ui.workbench.HomeActions
import app.numlab.ui.workbench.InterpInputs
import app.numlab.ui.workbench.Module
import app.numlab.ui.workbench.RootInputs
import app.numlab.ui.workbench.SummaryChips
import app.numlab.ui.components.Solved
import app.numlab.ui.workbench.WorkbenchState
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Sheet height in px with three anchors: peek, half, full (PLAN §6.3). */
@Stable
class SheetState(val peek: Float, val half: Float, val full: Float) {
    val height = Animatable(0f)
    val snaps get() = listOf(peek, half, full)
    fun nearest(v: Float, velocity: Float): Float = when {
        velocity < -1200 -> snaps.firstOrNull { it > v + 1 } ?: full // flung up
        velocity > 1200 -> snaps.lastOrNull { it < v - 1 } ?: peek
        else -> snaps.minBy { abs(it - v) }
    }
}

@Composable
fun rememberSheet(maxHeight: Dp, density: Density): SheetState {
    val reduce = LocalReduceMotion.current
    val s = with(density) { remember(maxHeight) { SheetState(218.dp.toPx(), maxHeight.toPx() * 0.56f, maxHeight.toPx() * 0.9f) } }
    LaunchedEffect(s) {
        if (reduce) s.height.snapTo(s.peek) else s.height.animateTo(s.peek, tween(520, 240, Motion.EaseOutQuart))
    }
    return s
}

private enum class Tab { INPUTS, TABLE, WORKING }

@Composable
fun CanvasSheet(
    st: WorkbenchState, a: HomeActions, sheet: SheetState, player: Player,
    rootRes: RootResult?, interpRes: InterpResult?, modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    var tab by rememberSaveable { mutableStateOf(Tab.INPUTS) }
    fun snap(to: Float) = scope.launch { if (reduce) sheet.height.snapTo(to) else sheet.height.animateTo(to, tween(380, easing = Motion.EaseOutQuart)) }
    val drag = rememberDraggableState { d -> scope.launch { sheet.height.snapTo((sheet.height.value - d).coerceIn(sheet.peek - 48f, sheet.full)) } }
    Surface(
        modifier.fillMaxWidth().layout { m, c ->
            val h = sheet.height.value.toInt().coerceAtLeast(0)
            val p = m.measure(c.copy(minHeight = h, maxHeight = h))
            layout(p.width, h) { p.place(0, 0) }
        }.shadow(16.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
        RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), cs.surface,
    ) {
        Column {
            Column(
                Modifier.fillMaxWidth()
                    .draggable(drag, Orientation.Vertical, onDragStopped = { v -> snap(sheet.nearest(sheet.height.value, v)) })
                    .clickable(onClickLabel = null) {
                        val cur = sheet.height.value
                        snap(if (cur < sheet.half - 20) sheet.half else if (cur < sheet.full - 20) sheet.full else sheet.peek)
                    }
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp).size(40.dp, 6.dp).background(cs.outline, CircleShape))
                SheetHeader(st, rootRes, interpRes)
            }
            if (rootRes != null) RootPlayer(rootRes, player) else if (interpRes != null) EvalSlider(interpRes, a)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val labels = if (st.module == Module.ROOTS) listOf(R.string.tab_inputs, R.string.tab_iterations, R.string.tab_working)
                else listOf(R.string.tab_inputs, R.string.tab_steps, R.string.tab_errors)
                Tab.entries.forEachIndexed { i, t ->
                    Surface(
                        { tab = t; if (sheet.height.value < sheet.half - 30) snap(sheet.half) },
                        Modifier.weight(1f).heightIn(min = 44.dp), shape = CircleShape,
                        color = if (tab == t) cs.surfaceVariant else cs.surface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(stringResource(labels[i]), style = MaterialTheme.typography.labelLarge,
                                color = if (tab == t) cs.onSurface else cs.onSurfaceVariant)
                        }
                    }
                }
            }
            AnimatedContent(tab, Modifier.weight(1f), label = "tab", transitionSpec = {
                (fadeIn(tween(320)) + slideInVertically(tween(320, easing = Motion.Decelerate)) { it / 40 }).togetherWith(fadeOut(tween(100)))
            }) { t ->
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp).navigationBarsPadding()) {
                    when (t) {
                        Tab.INPUTS -> if (st.module == Module.ROOTS) {
                            RootInputs(st.root, st.brackets, st.decimals, a.vm::setRoot, a.vm::applyPreset, a.vm::applyBracket, a.vm::setDecimals)
                        } else {
                            InterpInputs(st.interp, a.vm::setInterp)
                        }
                        Tab.TABLE -> rootRes?.let { IterationsPane(it.rows, it.tol, st.decimals, player.index, player::go, maxHeight = 2000.dp) }
                            ?: interpRes?.let { StepsPane(it, st.decimals, maxHeight = 2000.dp) }
                        Tab.WORKING -> rootRes?.let { WorkingPane(it, player.index, player::go) } ?: interpRes?.let { ErrorsPane(it) }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(st: WorkbenchState, rootRes: RootResult?, interpRes: InterpResult?) {
    val cs = MaterialTheme.colorScheme
    if (rootRes == null && interpRes == null) {
        Text(stringResource(R.string.fix_input_sheet), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant)
        return
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            SectionLabel(if (rootRes != null) stringResource(R.string.root_label_short) else stringResource(R.string.p_label_short, Fmt.fmt(interpRes!!.xe, 4)))
            BigNumber(rootRes?.root ?: interpRes!!.pxe, st.decimals, MonoStyle.copy(fontSize = 32.sp, color = cs.onSurface))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (rootRes != null) {
                app.numlab.ui.components.ConvBadge(rootRes, short = true)
                Text(stringResource(R.string.iterations_n, rootRes.iterations), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            } else {
                SummaryChips(Solved.Interp(interpRes!!, st.interp.xEval))
            }
        }
    }
}

@Composable
private fun RootPlayer(res: RootResult, p: Player) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundBtn(Icons.Outlined.SkipPrevious, stringResource(R.string.cd_prev_iter), { p.go(p.index - 1) }, enabled = p.index > 0, container = cs.surface)
        FilledIconButton(p::toggle, Modifier.size(48.dp), colors = IconButtonDefaults.filledIconButtonColors(cs.onSurface, cs.surface)) {
            Icon(if (p.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                stringResource(if (p.playing) R.string.cd_pause else R.string.cd_play))
        }
        RoundBtn(Icons.Outlined.SkipNext, stringResource(R.string.cd_next_iter), { p.go(p.index + 1) }, enabled = p.index < p.last, container = cs.surface)
        val label = stringResource(R.string.cd_iteration_slider)
        Slider(p.index.toFloat(), { p.go(Math.round(it)) }, Modifier.weight(1f).semantics { contentDescription = label },
            valueRange = 0f..maxOf(1, p.last).toFloat())
        Text("${res.rows[p.index].n}/${res.rows[p.last].n}", Modifier.width(52.dp).padding(start = 6.dp),
            style = MonoStyle.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
    }
}

@Composable
private fun EvalSlider(res: InterpResult, a: HomeActions) {
    val cs = MaterialTheme.colorScheme
    val lo = res.points.first().x
    val hi = res.points.last().x
    val label = stringResource(R.string.cd_eval_slider)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("x", style = MonoStyle.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
        Slider(res.xe.coerceIn(lo, hi).toFloat(), { a.vm.setXEval(it.toDouble()) },
            Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = label }, valueRange = lo.toFloat()..hi.toFloat())
        Text(Fmt.fmt(res.xe, 3), Modifier.width(60.dp), style = MonoStyle.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
    }
}
