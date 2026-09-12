package de.pilzscout.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.pilzscout.app.ui.navigation.AppNavDisplay
import de.pilzscout.app.ui.pack.PackInstallScreen
import de.pilzscout.app.ui.pack.PackInstallViewModel
import de.pilzscout.app.ui.theme.PilzScoutTheme

@Composable
fun AppRoot() {
    PilzScoutTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val packViewModel: PackInstallViewModel = hiltViewModel()
            val packState by packViewModel.state.collectAsStateWithLifecycle()
            when {
                !packState.loaded -> Unit // brief blank state while installed.json markers are read
                !packState.identificationReady -> PackInstallScreen(onReady = {}, viewModel = packViewModel)
                else -> AppNavDisplay()
            }
        }
    }
}
