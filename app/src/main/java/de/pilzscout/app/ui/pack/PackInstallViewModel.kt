package de.pilzscout.app.ui.pack

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.pilzscout.app.pack.PackInstallWorker
import de.pilzscout.app.pack.PackRepository
import de.pilzscout.app.pack.PackState
import de.pilzscout.app.settings.SettingsRepository
import de.pilzscout.core.model.PackComponent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PackInstallViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packRepository: PackRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<PackState> = packRepository.state

    val wifiOnly: StateFlow<Boolean> = settings.packWifiOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Installs every missing or outdated component. The repository starts right away (so errors such as
     * "offline" show immediately); the worker keeps the process alive and, for remote packs, retries once
     * a suitable network is back.
     */
    fun installAll() {
        viewModelScope.launch {
            PackInstallWorker.enqueue(context, packRepository.state.value.remote, settings.packWifiOnly.first())
            packRepository.installAll()
        }
    }

    fun install(component: PackComponent) = packRepository.install(listOf(component))

    fun uninstall(component: PackComponent) = packRepository.uninstall(component)

    fun checkForUpdates() = packRepository.checkForUpdates()

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { settings.setPackWifiOnly(enabled) }
    }
}
