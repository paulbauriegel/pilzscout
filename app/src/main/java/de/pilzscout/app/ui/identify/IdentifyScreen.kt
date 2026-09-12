package de.pilzscout.app.ui.identify

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.pilzscout.app.R
import de.pilzscout.app.ui.components.TabScaffold

@Composable
fun IdentifyScreen(onOpenSettings: () -> Unit) {
    TabScaffold(title = stringResource(R.string.identify_title), onOpenSettings = onOpenSettings) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.placeholder_coming_soon), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
