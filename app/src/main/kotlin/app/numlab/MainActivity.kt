package app.numlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.numlab.domain.model.ThemeMode
import app.numlab.ui.components.LocalHapticsEnabled
import app.numlab.ui.motion.LocalReduceMotion
import app.numlab.ui.motion.rememberReduceMotion
import app.numlab.ui.settings.SettingsSheet
import app.numlab.ui.splash.Splash
import app.numlab.ui.settings.SettingsViewModel
import app.numlab.ui.theme.NumLabTheme
import app.numlab.ui.workbench.WorkbenchViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val workbench: WorkbenchViewModel by viewModels()
    private val settingsVm: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the system splash only until settings (theme) are read; Room/DataStore warm up meanwhile.
        installSplashScreen().setKeepOnScreenCondition { settingsVm.settings.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val s by settingsVm.settings.collectAsStateWithLifecycle()
            val settings = s ?: return@setContent
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            // 0 = brand splash, 1 = splash lifting away over Home, 2 = done. Cold start only.
            var splash by rememberSaveable { mutableIntStateOf(if (savedInstanceState == null) 0 else 2) }
            CompositionLocalProvider(
                LocalReduceMotion provides rememberReduceMotion(settings.motion),
                LocalHapticsEnabled provides settings.haptics,
            ) {
                NumLabTheme(dark, settings.dynamicColor) {
                    if (splash > 0) NumLabNavGraph(workbench, settings.decimals) { showSettings = true }
                    if (showSettings) {
                        SettingsSheet(settings, BuildConfig.VERSION_NAME, settingsVm::update) { showSettings = false }
                    }
                    if (splash < 2) Splash(LocalReduceMotion.current, onExitStart = { splash = 1 }, onDone = { splash = 2 })
                }
            }
        }
    }
}
