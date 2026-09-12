package de.pilzscout.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.pilzscout.app.ui.components.LocalSpeciesImages
import de.pilzscout.app.ui.navigation.AppNavDisplay
import de.pilzscout.app.ui.pack.PackInstallScreen
import de.pilzscout.app.ui.pack.PackInstallViewModel
import de.pilzscout.app.ui.settings.SettingsViewModel
import de.pilzscout.app.ui.theme.PilzScoutTheme

@Composable
fun AppRoot() {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val dynamic by settingsViewModel.dynamicColor.collectAsStateWithLifecycle()
    PilzScoutTheme(dynamicColor = dynamic) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CompositionLocalProvider(LocalSpeciesImages provides settingsViewModel.speciesImages) {
                val packViewModel: PackInstallViewModel = hiltViewModel()
                val packState by packViewModel.state.collectAsStateWithLifecycle()
                when {
                    !packState.loaded -> Unit
                    !packState.identificationReady -> PackInstallScreen(onReady = {}, viewModel = packViewModel)
                    else -> AppNavDisplay()
                }
            }
        }
    }
}
