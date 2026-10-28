package app.numlab.ui.workbench

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.numlab.ui.R
import app.numlab.ui.components.ErrorBox
import app.numlab.ui.components.IterationsPane
import app.numlab.ui.components.LogoLockup
import app.numlab.ui.components.RoundBtn
import app.numlab.ui.components.Seg
import app.numlab.ui.motion.LocalReduceMotion
import app.numlab.ui.motion.Motion
import app.numlab.ui.motion.riseIn
import app.numlab.ui.theme.NumLabTheme
import androidx.compose.ui.graphics.Color

/** Callbacks from Home (state lives in [WorkbenchViewModel]). */
class HomeActions(
    val vm: WorkbenchViewModel,
    val openHistory: () -> Unit,
    val openSettings: () -> Unit,
    val openGraph: (Offset) -> Unit,
    val closeGraph: () -> Unit,
    val copied: (Int) -> Unit,
)

/** Input-first Home (PLAN §6.2): header, module switch, inputs card, result card, Graph/Save bar. */
@Composable
fun HomeScreen(st: WorkbenchState, a: HomeActions, snackbar: SnackbarHostState) {
    val cs = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(st, a, Modifier.riseIn(0))
            Seg(listOf(Module.ROOTS to stringResource(R.string.module_roots), Module.INTERP to stringResource(R.string.module_interp)),
                st.module, a.vm::setModule,
                Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).widthIn(max = 640.dp).align(Alignment.CenterHorizontally).riseIn(1),
                track = cs.surface, thumb = cs.background)
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedContent(st.module, Modifier.riseIn(2), label = "module", transitionSpec = {
                        val d = if (reduce) 0 else 260
                        (fadeIn(tween(d)) + slideInHorizontally(tween(d, easing = Motion.Decelerate)) { 8 * it / 360 })
                            .togetherWith(fadeOut(tween(d / 2)))
                    }) { m ->
                        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), cs.surface) {
                            Box(Modifier.padding(20.dp)) {
                                if (m == Module.ROOTS) {
                                    RootInputs(st.root, st.brackets, st.decimals, a.vm::setRoot, a.vm::applyPreset, a.vm::applyBracket, a.vm::setDecimals)
                                } else {
                                    InterpInputs(st.interp, a.vm::setInterp)
                                }
                            }
                        }
                    }
                    val solved = solvedOf(st)
                    AnimatedVisibility(solved != null, enter = if (reduce) fadeIn(tween(120)) else
                        fadeIn(tween(320)) + slideInVertically(tween(320, easing = Motion.Decelerate)) { it / 8 },
                        exit = fadeOut(tween(120))) {
                        solved?.let { ResultCard(it, st.decimals, { a.vm.save(true) }, a.copied) }
                    }
                    st.visibleError?.let { err ->
                        ErrorBox(err.message)
                        if (err.rows.isNotEmpty()) {
                            IterationsPane(err.rows, st.root.tol.toDoubleOrNull() ?: 0.0, st.decimals, null, null, maxHeight = 260.dp)
                        }
                    }
                    // Clears the pinned Graph/Save bar, which grows with font scale (PLAN §6.7).
                    Spacer(Modifier.height(170.dp * maxOf(1f, LocalDensity.current.fontScale * 0.8f)))
                }
            }
        }
        BottomBar(st, a, snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Header(st: WorkbenchState, a: HomeActions, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LogoLockup(Modifier.weight(1f))
        RoundBtn(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.cd_undo), a.vm::undo, enabled = st.canUndo)
        RoundBtn(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.cd_redo), a.vm::redo, enabled = st.canRedo)
        BadgedBox(badge = {
            if (st.historyCount > 0) Badge(containerColor = NumLabTheme.colors.hot, contentColor = Color.White) {
                Text(st.historyCount.toString())
            }
        }) { RoundBtn(Icons.Outlined.History, stringResource(R.string.cd_history), a.openHistory) }
        RoundBtn(Icons.Outlined.Settings, stringResource(R.string.cd_settings), a.openSettings)
    }
}

