package app.numlab.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * First-appearance rise: translate up [rise] and fade in, delayed by [index]·[staggerMs]
 * (PLAN §7.3). Values are read in the draw phase, so ticks don't recompose the content.
 */
fun Modifier.riseIn(index: Int, staggerMs: Int = 55, durationMs: Int = 420, rise: Float = 12f): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val p = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        delay(index.toLong() * staggerMs)
        p.animateTo(1f, tween(durationMs, easing = Motion.Decelerate))
    }
    val risePx = rise.dp
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * risePx.toPx()
    }
}
