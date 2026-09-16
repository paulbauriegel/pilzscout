package de.pilzscout.app.ui.identify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.pilzscout.core.model.ViewType

/**
 * Optional view choice for a photo. Tapping the selected chip again clears it, so "no view" is always
 * reachable. [onDark] styles the chips for the camera preview.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ViewTypeChips(
    selected: ViewType?,
    onSelect: (ViewType?) -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    val colors = if (onDark) {
        FilterChipDefaults.filterChipColors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            labelColor = Color.White,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        FilterChipDefaults.filterChipColors()
    }
    FlowRow(
        modifier,
        horizontalArrangement = if (onDark) Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(8.dp),
    ) {
        ViewType.suggestedViews.forEach { view ->
            val isSelected = view == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else view) },
                label = { Text(view.badgeLabel()) },
                colors = colors,
                border = if (onDark) {
                    FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected, borderColor = Color.White.copy(alpha = 0.6f))
                } else {
                    FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected)
                },
            )
        }
    }
}
