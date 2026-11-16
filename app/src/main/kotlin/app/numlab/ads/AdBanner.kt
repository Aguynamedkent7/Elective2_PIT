package app.numlab.ads

import android.view.View
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.numlab.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Anchored adaptive banner. Takes no space until ads may be requested, and collapses when none can
 * load (for example offline). [active] is false while something covers it: the view is then hidden
 * and paused, because AdMob does not allow ads that are obscured.
 */
@Composable
fun AdBanner(active: Boolean, modifier: Modifier = Modifier) {
    val ready by Ads.ready.collectAsStateWithLifecycle()
    if (!ready) return
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val widthDp = maxWidth.value.toInt()
        // A new width (rotation, resize) needs a new ad size, which an AdView can't change once set.
        key(widthDp) { Banner(widthDp, active) }
    }
}

@Composable
private fun Banner(widthDp: Int, active: Boolean) {
    val context = LocalContext.current
    val size = remember { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp) }
    var failed by remember { mutableStateOf(false) }
    val adView = remember {
        AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(size)
            adListener = object : AdListener() {
                override fun onAdLoaded() { failed = false }
                override fun onAdFailedToLoad(error: LoadAdError) { failed = true }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    LifecycleResumeEffect(adView, active) {
        if (active) adView.resume() else adView.pause()
        onPauseOrDispose { adView.pause() }
    }
    AndroidView(
        factory = { adView },
        modifier = Modifier.fillMaxWidth().height(if (failed) 0.dp else size.height.dp),
        update = { it.visibility = if (active) View.VISIBLE else View.GONE },
    )
}
