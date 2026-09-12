package de.pilzscout.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.pilzscout.app.R
import de.pilzscout.app.settings.AppLanguage
import de.pilzscout.app.ui.pack.PackComponentsContent
import de.pilzscout.app.ui.pack.PackInstallViewModel

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val language by viewModel.language.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Column(Modifier.selectableGroup()) {
                LanguageOption(AppLanguage.SYSTEM, R.string.settings_language_system, language, viewModel::setLanguage)
                LanguageOption(AppLanguage.GERMAN, R.string.settings_language_de, language, viewModel::setLanguage)
                LanguageOption(AppLanguage.ENGLISH, R.string.settings_language_en, language, viewModel::setLanguage)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.settings_offline_data),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                stringResource(R.string.settings_offline_data_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            val packViewModel: PackInstallViewModel = hiltViewModel()
            val packState by packViewModel.state.collectAsStateWithLifecycle()
            PackComponentsContent(packState, packViewModel, Modifier.padding(16.dp))
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.settings_model_info), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            val classifier by viewModel.classifier.collectAsStateWithLifecycle()
            val useGpu by viewModel.useGpu.collectAsStateWithLifecycle()
            Text(
                when (val c = classifier) {
                    is de.pilzscout.app.ml.ClassifierState.Ready -> stringResource(R.string.settings_model_ready, c.info.version, c.info.name, c.info.precision, c.info.inputSize, c.info.numClasses, c.info.accelerator)
                    is de.pilzscout.app.ml.ClassifierState.Failed -> stringResource(R.string.identify_model_failed, c.message)
                    de.pilzscout.app.ml.ClassifierState.Loading -> stringResource(R.string.settings_model_not_loaded)
                    de.pilzscout.app.ml.ClassifierState.NotInstalled -> stringResource(R.string.identify_model_missing)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_gpu), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.settings_gpu_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = useGpu, onCheckedChange = viewModel::setUseGpu)
            }
            val dynamic by viewModel.dynamicColor.collectAsStateWithLifecycle()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_dynamic_color), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.settings_dynamic_color_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = dynamic, onCheckedChange = viewModel::setDynamicColor)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            Text(stringResource(R.string.settings_about_body), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun LanguageOption(
    option: AppLanguage,
    label: Int,
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = option == selected, onClick = { onSelect(option) }, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = option == selected, onClick = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
