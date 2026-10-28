package app.numlab.ui.workbench

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.numlab.ui.R
import app.numlab.ui.components.rememberConfirm
import app.numlab.ui.components.rememberTick
import app.numlab.ui.motion.LocalReduceMotion
import app.numlab.ui.motion.Motion

/** Pinned Graph + Save bar with the lock caption and the ready pulse (PLAN §6.2, §7.4). */
@Composable
fun BottomBar(st: WorkbenchState, a: HomeActions, snackbar: SnackbarHostState, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val lock = st.lock
    val reduce = LocalReduceMotion.current
    val tick = rememberTick()
    val confirm = rememberConfirm()
    val pulse = remember { Animatable(1f) }
    var wasLocked by remember { mutableStateOf(lock != null) }
    LaunchedEffect(lock == null) {
        if (lock == null && wasLocked) {
            tick()
            if (!reduce) { pulse.snapTo(0f); pulse.animateTo(1f, tween(1200, easing = Motion.Decelerate)) }
        }
        wasLocked = lock != null
    }
    var center by remember { mutableStateOf(Offset.Zero) }
    Column(
        modifier.fillMaxWidth()
            // Opaque from the end of the top padding down, so a caption wrapped by large fonts never sits on content.
            .background(Brush.verticalGradient(listOf(cs.background.copy(alpha = 0f), cs.background), endY = with(LocalDensity.current) { 40.dp.toPx() }))
            .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SnackbarHost(snackbar)
        if (lock != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(13.dp), tint = cs.onSurfaceVariant)
                Text(stringResource(lock.caption), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val label = stringResource(R.string.cd_show_graph)
            Button(
                { a.openGraph(center) },
                Modifier.weight(1f).heightIn(min = 56.dp)
                    .onGloballyPositioned { center = it.boundsInRoot().center }
                    .semantics { contentDescription = label }
                    .drawBehind {
                        val p = pulse.value
                        if (p < 1f) {
                            val grow = 22.dp.toPx() * p
                            drawRoundRect(cs.primary.copy(alpha = 0.6f * (1 - p)), Offset(-grow, -grow),
                                androidx.compose.ui.geometry.Size(size.width + 2 * grow, size.height + 2 * grow),
                                CornerRadius(size.height / 2 + grow), style = Stroke(4.dp.toPx()))
                        }
                    },
                enabled = lock == null, shape = CircleShape,
                colors = ButtonDefaults.buttonColors(disabledContainerColor = cs.surface, disabledContentColor = cs.onSurfaceVariant),
            ) {
                AnimatedContent(lock == null, label = "graph-icon", transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(180))).togetherWith(fadeOut(tween(180)))
                }) { open ->
                    val turn by transition.animateFloat({ tween(180) }, label = "turn") { if (it == EnterExitState.Visible) 0f else 90f }
                    Icon(if (open) Icons.AutoMirrored.Outlined.ShowChart else Icons.Outlined.Lock, null,
                        Modifier.graphicsLayer { rotationZ = turn })
                }
                Text(stringResource(R.string.graph), Modifier.padding(horizontal = 10.dp), fontWeight = FontWeight.SemiBold)
                if (lock == null) Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
            }
            Button(
                { confirm(); a.vm.save(true) }, Modifier.heightIn(min = 56.dp), enabled = lock == null, shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = cs.surface, contentColor = cs.onSurface,
                    disabledContainerColor = cs.surface, disabledContentColor = cs.onSurfaceVariant),
            ) {
                Icon(Icons.Outlined.BookmarkBorder, stringResource(R.string.cd_save))
                Text(stringResource(R.string.save), Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
