package app.numlab.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.numlab.domain.model.MotionPref
import app.numlab.domain.model.Settings
import app.numlab.domain.model.ThemeMode
import app.numlab.domain.roots.MAX_ITER_CAP
import app.numlab.ui.R
import app.numlab.ui.components.SectionLabel
import app.numlab.ui.components.Seg
import app.numlab.ui.components.Stepper
import app.numlab.ui.theme.Bricolage
import app.numlab.ui.workbench.StepperGrid
import app.numlab.ui.workbench.superscript
import app.numlab.ui.workbench.tolExp

/** Settings sheet (PLAN §6.6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(s: Settings, version: String, update: ((Settings) -> Settings) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismiss, containerColor = cs.surface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(stringResource(R.string.settings_title), fontFamily = Bricolage, style = MaterialTheme.typography.headlineSmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.settings_theme))
                Seg(listOf(ThemeMode.SYSTEM to stringResource(R.string.theme_system), ThemeMode.LIGHT to stringResource(R.string.theme_light),
                    ThemeMode.DARK to stringResource(R.string.theme_dark)), s.themeMode, { m -> update { it.copy(themeMode = m) } })
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.settings_motion))
                Seg(listOf(MotionPref.SYSTEM to stringResource(R.string.motion_system), MotionPref.REDUCED to stringResource(R.string.motion_reduced),
                    MotionPref.FULL to stringResource(R.string.motion_full)), s.motion, { m -> update { it.copy(motion = m) } })
            }
            Toggle(stringResource(R.string.settings_dynamic), s.dynamicColor) { v -> update { it.copy(dynamicColor = v) } }
            Toggle(stringResource(R.string.settings_haptics), s.haptics) { v -> update { it.copy(haptics = v) } }
            Toggle(stringResource(R.string.settings_restore), s.restoreSession) { v -> update { it.copy(restoreSession = v) } }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.settings_defaults))
                StepperGrid {
                    val e = tolExp(s.defaultTolerance)
                    item {
                        Stepper(stringResource(R.string.field_tolerance), "10${superscript(-e)}",
                            { update { it.copy(defaultTolerance = "1e-${minOf(12, e + 1)}") } },
                            { update { it.copy(defaultTolerance = "1e-${maxOf(1, e - 1)}") } })
                    }
                    item {
                        val n = s.defaultMaxIter.toIntOrNull() ?: 100
                        Stepper(stringResource(R.string.field_max_iter), n.toString(),
                            { update { it.copy(defaultMaxIter = (n - 10).coerceAtLeast(5).toString()) } },
                            { update { it.copy(defaultMaxIter = (n + 10).coerceAtMost(MAX_ITER_CAP).toString()) } })
                    }
                    wide {
                        Stepper(stringResource(R.string.field_decimals), s.decimals.toString(),
                            { update { it.copy(decimals = (s.decimals - 1).coerceAtLeast(3)) } },
                            { update { it.copy(decimals = (s.decimals + 1).coerceAtMost(10)) } })
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(stringResource(R.string.settings_about))
                Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Text(stringResource(R.string.about_licences), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) = Row(
    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value, role = Role.Switch, onValueChange = onChange),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyLarge)
    Switch(value, null)
}
