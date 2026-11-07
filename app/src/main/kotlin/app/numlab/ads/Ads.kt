package app.numlab.ads

import android.app.Activity
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Gathers ad consent, then starts the AdMob SDK. No ad is requested until [ready] is true. */
object Ads {
    private val initStarted = AtomicBoolean(false)
    private val _ready = MutableStateFlow(false)
    private val _privacyOptionsRequired = MutableStateFlow(false)

    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** True when the user must be able to reopen the consent form from Settings. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    fun start(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { refresh(activity, info) } },
            { refresh(activity, info) },
        )
        // Consent stored by an earlier session lets ads start without waiting for the update above.
        refresh(activity, info)
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            refresh(activity, UserMessagingPlatform.getConsentInformation(activity))
        }
    }

    private fun refresh(activity: Activity, info: ConsentInformation) {
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (info.canRequestAds() && initStarted.compareAndSet(false, true)) {
            val app = activity.applicationContext
            // Initialization does disk work, so keep it off the main thread and away from cold start.
            thread(name = "admob-init") { MobileAds.initialize(app) { _ready.value = true } }
        }
    }
}
