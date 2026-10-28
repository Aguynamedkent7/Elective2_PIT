package app.numlab.ui.workbench

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.numlab.ui.canvas.CanvasScreen
import app.numlab.ui.motion.LocalReduceMotion
import app.numlab.ui.motion.Motion
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/**
 * Home plus the graph canvas. The canvas is an overlay of this destination rather than its own
 * NavHost route so the circular reveal can show Home underneath (docs/DECISIONS.md).
 */
@Composable
fun WorkbenchRoute(vm: WorkbenchViewModel, openHistory: () -> Unit, openSettings: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val homeSnack = remember { SnackbarHostState() }
    val canvasSnack = remember { SnackbarHostState() }
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val reveal = remember { Animatable(if (st.graphOpen) 1f else 0f) }

    val show: (Int, SnackAction?) -> Unit = { msg, action ->
        val host = if (vm.state.value.graphOpen) canvasSnack else homeSnack
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val r = host.showSnackbar(res.getString(msg), action?.let { res.getString(it.label) },
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed && action == SnackAction.VIEW_HISTORY) openHistory()
        }
    }
    LaunchedEffect(vm) { vm.effect.collect { e -> if (e is Effect.Snack) show(e.message, e.action) } }
    LaunchedEffect(st.graphOpen) {
        if (st.graphOpen && reveal.value < 1f) {
            if (reduce) reveal.snapTo(1f) else reveal.animateTo(1f, tween(440, easing = Motion.Decelerate))
        }
    }
    val close: () -> Unit = {
        scope.launch {
            if (reduce) reveal.snapTo(0f) else reveal.animateTo(0f, tween(380, easing = Motion.Accelerate))
            vm.closeGraph()
        }
    }
    val actions = remember(vm) {
        HomeActions(vm, openHistory, openSettings, openGraph = { vm.openGraph(it) }, closeGraph = { close() },
            copied = { show(it, null) })
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val p = reveal.value
        Box(Modifier.fillMaxSize().then(if (st.graphOpen) Modifier.clearAndSetSemantics {} else Modifier).graphicsLayer {
            if (!reduce) { scaleX = 1f - 0.06f * p; scaleY = 1f - 0.06f * p }
            alpha = 1f - 0.5f * p
        }) { HomeScreen(st, actions, homeSnack) }
        if (st.graphOpen || p > 0f) {
            val density = LocalDensity.current
            val w = with(density) { maxWidth.toPx() }
            val h = with(density) { maxHeight.toPx() }
            val o = vm.graphOrigin ?: Offset(w / 2, h - with(density) { 60f * this.density })
            val radius = hypot(max(o.x, w - o.x), max(o.y, h - o.y)) + 24f
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    if (p >= 1f) drawContent()
                    else clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(o, radius * p)) }) {
                        this@drawWithContent.drawContent()
                    }
                }.background(MaterialTheme.colorScheme.background),
            ) { CanvasScreen(st, actions, canvasSnack, onBack = close) }
        }
    }
}
