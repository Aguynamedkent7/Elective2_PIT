package app.numlab.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numlab.ui.R
import app.numlab.ui.motion.Motion
import app.numlab.ui.theme.Bricolage
import app.numlab.ui.theme.LogoDot
import app.numlab.ui.theme.LogoTile
import kotlinx.coroutines.delay

// Geometry from docs/assets/logo.svg (64-unit box).
private val CURVE = PathParser().parsePathString("M14.5 13L13.5 50C17 47 20 41 22 35C23.5 30.5 26.5 31 30 30.3C34.5 29.5 37 26.5 38.3 21.5").toPath()
private val BASE = PathParser().parsePathString("M39.5 50.5H54.5").toPath()
private val ONE = PathParser().parsePathString("M47.5 50.5L49 12L44.3 18.6").toPath()
private val DOT = Offset(30f, 30.3f)

private const val EXIT_AT = 2.35f
private const val EXIT_MS = 520

/** Progress of a [start, start+dur] window on the timeline [t], eased. */
private fun win(t: Float, start: Float, dur: Float, e: Easing = LinearEasing) = e.transform(((t - start) / dur).coerceIn(0f, 1f))

/** `dot-pop`: 0 → 1.25 at 60 % → 1. */
private fun dotPop(p: Float) = if (p < 0.6f) 1.25f * p / 0.6f else 1.25f - 0.25f * (p - 0.6f) / 0.4f

/**
 * In-app brand splash (PLAN §7.2): tile pops, curve/foot/1 draw on, root dot pops with a ring,
 * wordmark letters rise, then everything lifts away. Tap to skip; reduced motion is a short fade.
 * Calls [onExitStart] when the lift begins (Home starts entering) and [onDone] when it is gone.
 */
@Composable
fun Splash(reduce: Boolean, onExitStart: () -> Unit, onDone: () -> Unit) {
    val clock = remember { Animatable(if (reduce) 2.7f else 0f) }
    val out = remember { Animatable(0f) }
    var exiting by remember { mutableStateOf(false) }
    LaunchedEffect(exiting) {
        if (!exiting) {
            if (reduce) delay(900) else clock.animateTo(EXIT_AT, tween((EXIT_AT * 1000).toInt(), easing = LinearEasing))
            exiting = true
            return@LaunchedEffect
        }
        onExitStart()
        out.animateTo(1f, tween(if (reduce) 200 else EXIT_MS, easing = Motion.Accelerate))
        onDone()
    }
    LaunchedEffect(exiting) { if (exiting && !reduce) clock.animateTo(2.7f, tween(((2.7f - clock.value) * 1000).toInt().coerceAtLeast(0))) }

    val bg = MaterialTheme.colorScheme.background
    val t = clock.value
    val o = out.value
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = 1f - o; if (!reduce) { scaleX = 1f + 0.06f * o; scaleY = 1f + 0.06f * o } }
            .background(bg)
            .clickable(remember { MutableInteractionSource() }, null) { exiting = true }
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.graphicsLayer { if (!reduce) { translationY = -18.dp.toPx() * o; scaleX = 1f - 0.1f * o; scaleY = 1f - 0.1f * o } },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Canvas(Modifier.size(104.dp)) { drawLogo(t) }
            Wordmark(t)
        }
        Text(
            stringResource(R.string.splash_skip), Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp)
                .graphicsLayer { alpha = win(t, 1.4f, 0.4f) },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Wordmark(t: Float) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row {
        "NumLab".forEachIndexed { i, c ->
            val p = win(t, 1.9f + 0.045f * i, 0.55f, Motion.Decelerate)
            Text(
                c.toString(), Modifier.graphicsLayer { alpha = p; translationY = (1 - p) * 20.dp.toPx() },
                fontFamily = Bricolage, fontSize = 36.sp,
                fontWeight = if (i >= 3) FontWeight.Medium else FontWeight.ExtraBold,
                color = if (i >= 3) ink.copy(alpha = 0.8f) else ink,
            )
        }
    }
}

private fun DrawScope.drawLogo(t: Float) {
    val k = size.width / 64f
    scale(k, k, Offset.Zero) {
        val tile = win(t, 0f, 0.6f, Motion.Spring)
        withTransform({ scale(0.35f + 0.65f * tile, 0.35f + 0.65f * tile, Offset(32f, 32f)) }) {
            drawRoundRect(LogoTile.copy(alpha = win(t, 0f, 0.6f)), size = Size(64f, 64f), cornerRadius = CornerRadius(18f))
        }
        trimmed(CURVE, win(t, 0.6f, 0.8f, Motion.Standard), 4.6f)
        trimmed(BASE, win(t, 1.2f, 0.35f, Motion.Standard), 3.4f)
        trimmed(ONE, win(t, 1.3f, 0.5f, Motion.Standard), 4.6f)
        val ring = win(t, 1.8f, 0.9f, Motion.Decelerate)
        if (ring in 0.001f..0.999f) {
            val a = if (ring < 0.08f) ring / 0.08f * 0.9f else 0.9f * (1 - ring) / 0.92f
            drawCircle(LogoDot.copy(alpha = a), 5.5f * (0.7f + 2.7f * ring), DOT, style = Stroke(2f))
        }
        val dot = dotPop(win(t, 1.75f, 0.5f))
        if (dot > 0f) drawCircle(LogoDot, 3.9f * dot, DOT)
    }
}

private fun DrawScope.trimmed(p: Path, f: Float, width: Float) {
    if (f <= 0f) return
    val seg = if (f >= 1f) p else Path().also { val m = PathMeasure(); m.setPath(p, false); m.getSegment(0f, m.length * f, it, true) }
    drawPath(seg, Color.White, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
