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
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_offline_data)) },
                supportingContent = { Text(stringResource(R.string.settings_offline_data_summary)) },
            )
            ListItem(headlineContent = { Text(stringResource(R.string.settings_model_info)) })
            ListItem(headlineContent = { Text(stringResource(R.string.settings_about)) })
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
