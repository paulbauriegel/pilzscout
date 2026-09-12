package de.pilzscout.app.ui.pack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.pilzscout.app.R
import de.pilzscout.app.pack.PackState
import de.pilzscout.core.model.PackComponent
import de.pilzscout.core.model.PackComponentManifest

/**
 * First-launch screen: install the bundled "Germany offline pack" component by component.
 * Identification becomes available as soon as model + core species data are installed; the image
 * components keep installing afterwards and the caller may leave this screen ([onReady]).
 */
@Composable
fun PackInstallScreen(
    onReady: () -> Unit,
    viewModel: PackInstallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.pack_title), style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.pack_intro), style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.pack_offline_note), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            PackComponentsContent(state, viewModel)
            if (state.identificationReady) {
                Button(onClick = onReady, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.pack_continue), style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.pack_continue_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Component cards + install button. No scrolling of its own, so it can be embedded in Settings. */
@Composable
fun PackComponentsContent(state: PackState, viewModel: PackInstallViewModel, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val manifest = state.manifest
        if (manifest == null) {
            Text(
                if (state.loaded) stringResource(R.string.pack_missing_manifest) else stringResource(R.string.pack_loading),
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            manifest.components.filter { it.bundled }.forEach { component ->
                ComponentCard(component, state, onInstall = { viewModel.install(component.component) }, onRemove = { viewModel.uninstall(component.component) })
            }
        }
        val anyInstalling = state.installing.isNotEmpty()
        val allInstalled = manifest?.components?.filter { it.bundled }?.all { state.isInstalled(it.component) } == true
        if (!allInstalled) {
            Button(
                onClick = viewModel::installAll,
                enabled = manifest != null && !anyInstalling,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(if (anyInstalling) R.string.pack_installing else R.string.pack_install_all), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ComponentCard(component: PackComponentManifest, state: PackState, onInstall: () -> Unit, onRemove: () -> Unit) {
    val kind = component.component
    val installed = state.isInstalled(kind)
    val progress = state.installing[kind]
    val error = state.errors[kind]
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(componentTitle(kind), style = MaterialTheme.typography.titleMedium)
                    Text(componentDescription(kind), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatBytes(component.bytes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    installed && state.isOutdated(kind) -> TextButton(onClick = onInstall) { Text(stringResource(R.string.pack_update)) }
                    installed -> Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.pack_installed), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    error != null -> Icon(Icons.Outlined.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    progress == null -> TextButton(onClick = onInstall) { Text(stringResource(R.string.pack_install)) }
                }
            }
            if (progress != null) {
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            if (error != null) {
                Text(stringResource(R.string.pack_error, error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (installed && !component.required) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.pack_remove)) }
            }
        }
    }
}

@Composable
fun componentTitle(kind: PackComponent): String = stringResource(
    when (kind) {
        PackComponent.MODEL -> R.string.pack_component_model
        PackComponent.CORE -> R.string.pack_component_core
        PackComponent.WIKI -> R.string.pack_component_wiki
        PackComponent.FUNGITASTIC -> R.string.pack_component_fungitastic
        PackComponent.IMAGES_HD -> R.string.pack_component_images_hd
    },
)

@Composable
fun componentDescription(kind: PackComponent): String = stringResource(
    when (kind) {
        PackComponent.MODEL -> R.string.pack_component_model_desc
        PackComponent.CORE -> R.string.pack_component_core_desc
        PackComponent.WIKI -> R.string.pack_component_wiki_desc
        PackComponent.FUNGITASTIC -> R.string.pack_component_fungitastic_desc
        PackComponent.IMAGES_HD -> R.string.pack_component_images_hd_desc
    },
)

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1e6)
    bytes >= 1_000 -> "%d kB".format(bytes / 1000)
    else -> "$bytes B"
}
