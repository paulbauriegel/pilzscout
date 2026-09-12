package de.pilzscout.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.pilzscout.app.ui.navigation.AppNavDisplay
import de.pilzscout.app.ui.theme.PilzScoutTheme

@Composable
fun AppRoot() {
    PilzScoutTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppNavDisplay()
        }
    }
}
