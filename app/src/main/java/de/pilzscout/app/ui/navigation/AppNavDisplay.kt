package de.pilzscout.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import de.pilzscout.app.R
import de.pilzscout.app.ui.browse.BrowseScreen
import de.pilzscout.app.ui.history.HistoryScreen
import de.pilzscout.app.ui.identify.IdentifyScreen
import de.pilzscout.app.ui.settings.SettingsScreen

private data class Tab(val key: TopLevelKey, val label: Int, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    Tab(IdentifyKey, R.string.nav_identify, Icons.Filled.PhotoCamera, Icons.Outlined.PhotoCamera),
    Tab(BrowseKey, R.string.nav_browse, Icons.Filled.Search, Icons.Outlined.Search),
    Tab(HistoryKey, R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
)

@Composable
fun AppNavDisplay() {
    val nav = remember { TopLevelBackStack(IdentifyKey) }
    val current = nav.backStack.lastOrNull()
    val showBottomBar = current is TopLevelKey

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = nav.topLevelKey == tab.key
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTo(tab.key) },
                            icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = nav.backStack,
            modifier = Modifier.padding(padding),
            onBack = { nav.pop() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<IdentifyKey> { IdentifyScreen(onOpenSettings = { nav.push(SettingsKey) }) }
                entry<BrowseKey> { BrowseScreen(onOpenSettings = { nav.push(SettingsKey) }) }
                entry<HistoryKey> { HistoryScreen(onOpenSettings = { nav.push(SettingsKey) }) }
                entry<SettingsKey> { SettingsScreen(onBack = { nav.pop() }) }
            },
        )
    }
}
