package app.numlab.ui.motion

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.numlab.domain.model.MotionPref

/** Easing tokens copied from `--ease-*` in template3.html (PLAN §7.1). */
object Motion {
    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Spring: Easing = CubicBezierEasing(0.3f, 1.25f, 0.5f, 1f)
    val EaseOutQuart: Easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
}

/** True when animations should be skipped (system animator scale 0 or in-app override). */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun rememberReduceMotion(pref: MotionPref): Boolean {
    val ctx = LocalContext.current
    return remember(pref) {
        when (pref) {
            MotionPref.REDUCED -> true
            MotionPref.FULL -> false
            MotionPref.SYSTEM ->
                Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    }
}
