package de.pilzscout.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.pilzscout.app.settings.AppLanguage
import de.pilzscout.app.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    classifierProvider: de.pilzscout.app.ml.ClassifierProvider,
) : ViewModel() {

    val language: StateFlow<AppLanguage> =
        settings.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLanguage.SYSTEM)
    val useGpu: StateFlow<Boolean> = settings.useGpu.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val classifier: StateFlow<de.pilzscout.app.ml.ClassifierState> = classifierProvider.state

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settings.setLanguage(language) }
    }

    fun setUseGpu(enabled: Boolean) {
        viewModelScope.launch { settings.setUseGpu(enabled) }
    }
}
