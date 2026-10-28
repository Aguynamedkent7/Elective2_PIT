package app.numlab.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numlab.domain.model.Settings
import app.numlab.domain.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide settings: theme, motion, haptics, defaults (PLAN §5.2, §6.6). Null until loaded. */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<Settings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun update(t: (Settings) -> Settings) {
        viewModelScope.launch { repo.update(t) }
    }
}
