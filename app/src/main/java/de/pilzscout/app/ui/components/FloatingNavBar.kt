package de.pilzscout.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.pilzscout.app.ui.theme.ForestColors

data class NavItem(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector)

/** Floating pill-shaped bottom bar in warm dark brown with a sage indicator, as in the design reference. */
@Composable
fun FloatingNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Surface(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (dark) ForestColors.navBarDark else ForestColors.navBarLight,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, item ->
                val isSelected = i == selected
                Column(
                    Modifier
                        .clip(MaterialTheme.shapes.large)
                        .selectable(selected = isSelected, onClick = { onSelect(i) }, role = Role.Tab)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(if (isSelected) ForestColors.navSelected else androidx.compose.ui.graphics.Color.Transparent)
                            .width(56.dp)
                            .height(30.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = null,
                            tint = if (isSelected) ForestColors.navOnSelected else ForestColors.navOnBar,
                        )
                    }
                    Text(item.label, style = MaterialTheme.typography.labelMedium, color = ForestColors.navOnBar, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}
