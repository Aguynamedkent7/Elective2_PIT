package app.numlab.ui.canvas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numlab.domain.format.Fmt
import app.numlab.ui.R
import app.numlab.ui.chart.Plot
import app.numlab.ui.chart.domainFor
import app.numlab.ui.components.ErrorBox
import app.numlab.ui.components.RoundBtn
import app.numlab.ui.components.ShareMenu
import app.numlab.ui.motion.LocalReduceMotion
import app.numlab.ui.theme.MonoStyle
import app.numlab.ui.theme.NumLabTheme
import app.numlab.ui.workbench.HomeActions
import app.numlab.ui.workbench.Module
import app.numlab.ui.workbench.Outcome
import app.numlab.ui.workbench.WorkbenchState
import app.numlab.ui.workbench.solvedOf
import kotlinx.coroutines.delay

/** Iteration player state, hoisted so the sheet and the plot agree. */
class Player(val last: Int) {
    var k by mutableStateOf<Int?>(null)
    var playing by mutableStateOf(false)
    val index get() = (k ?: last).coerceIn(0, last)
    val stepMs get() = (2600 / maxOf(1, last)).coerceIn(150, 650)
    fun go(i: Int) { playing = false; k = i.coerceIn(0, last) }
    fun toggle() {
        if (playing) { playing = false; return }
        if (index >= last) k = 0
        playing = true
    }
}

/** Full-screen graph with a draggable bottom sheet (PLAN §6.3). [onBack] plays the reverse reveal. */
@Composable
fun CanvasScreen(st: WorkbenchState, a: HomeActions, snackbar: SnackbarHostState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val cs = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val solved = solvedOf(st)
    val rootRes = (st.rootOutcome as? Outcome.Ok)?.value?.takeIf { st.module == Module.ROOTS && solved != null }
    val interpRes = (st.interpOutcome as? Outcome.Ok)?.value?.takeIf { st.module == Module.INTERP && solved != null }
    val player = remember(rootRes) { Player(maxOf(0, (rootRes?.rows?.size ?: 1) - 1)) }
    var autoplayed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(player) {
        if (!autoplayed && rootRes != null && player.last > 0 && !reduce) { player.k = 0; player.playing = true }
        autoplayed = true
    }
    LaunchedEffect(player, player.playing) {
        while (player.playing) {
            delay(player.stepMs.toLong())
            if (player.index >= player.last) player.playing = false else player.k = player.index + 1
        }
    }
    val ink = GraphInk(NumLabTheme.colors.curve, NumLabTheme.colors.hot, cs.primary, cs.onSurface, cs.onSurfaceVariant)
    val xs = interpRes?.points?.map { it.x }
    val interpDomain = remember(xs) { xs?.let { domainFor(it, 0.3) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheet = rememberSheet(maxHeight, LocalDensity.current)
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val padB = with(LocalDensity.current) { minOf(sheet.height.value + 30.dp.toPx(), maxHeight.toPx() - 190.dp.toPx()).toDp() }
        val spec = when {
            rootRes != null -> rootSpec(rootRes, player.index, player.stepMs, st.decimals, ink)
            interpRes != null -> interpSpec(interpRes, ink, interpDomain!!)
            else -> null
        }
        if (spec != null) {
            val desc = if (rootRes != null) stringResource(R.string.graph_a11y_root, Fmt.fmt(rootRes.root, st.decimals))
            else stringResource(R.string.graph_a11y_interp, Fmt.fmt(interpRes!!.xe, 3), Fmt.fmt(interpRes.pxe, st.decimals))
            Plot(
                spec.curves, spec.points, spec.overlays, spec.domain, desc, Modifier.fillMaxSize(),
                padTop = top + 74.dp, padBottom = padB, includeZero = spec.includeZero, controlsTop = top + 76.dp,
                onDragPoint = interpRes?.let { r -> { i, y -> a.vm.dragPoint(r.points[i].x, y) } },
                onDragLine = interpRes?.let { { x -> a.vm.setXEval(x) } },
            )
        } else {
            st.visibleError?.let { ErrorBox(it.message, Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 80.dp)) }
        }
        TopBar(st, a, solved)
        CanvasSheet(st, a, sheet, player, rootRes, interpRes, Modifier.align(Alignment.BottomCenter))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = with(LocalDensity.current) { sheet.height.value.toDp() } + 8.dp))
    }
}

@Composable
private fun TopBar(st: WorkbenchState, a: HomeActions, solved: app.numlab.ui.components.Solved?) {
    val glass = NumLabTheme.colors.glass
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RoundBtn(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cd_back), a.closeGraph, container = glass)
        Surface(Modifier.weight(1f).heightIn(min = 44.dp), CircleShape, glass) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                val title = if (st.module == Module.ROOTS) st.root.fx.ifBlank { "…" }
                else (st.interpOutcome as? Outcome.Ok)?.value?.points?.size?.let { stringResource(R.string.canvas_title_points, it) } ?: "…"
                Text(title, style = MonoStyle.copy(fontSize = 12.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        RoundBtn(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.cd_undo), a.vm::undo, enabled = st.canUndo, container = glass)
        RoundBtn(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.cd_redo), a.vm::redo, enabled = st.canRedo, container = glass)
        ShareMenu(solved, { a.vm.save(true) }, a.copied, container = glass)
    }
}
