package de.pilzscout.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dangerous
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.pilzscout.app.R
import de.pilzscout.core.model.Edibility

@Composable
fun edibilityLabel(e: Edibility?): String = stringResource(
    when (e) {
        Edibility.DEADLY -> R.string.edibility_deadly
        Edibility.POISONOUS -> R.string.edibility_poisonous
        Edibility.PSYCHOACTIVE -> R.string.edibility_psychoactive
        Edibility.CAUTION -> R.string.edibility_caution
        Edibility.INEDIBLE -> R.string.edibility_inedible
        Edibility.EDIBLE -> R.string.edibility_edible
        Edibility.CHOICE -> R.string.edibility_choice
        Edibility.UNKNOWN, null -> R.string.edibility_unknown
    },
)

@Composable
fun edibilitySourceLabel(source: String?): String = stringResource(
    when {
        source == null -> R.string.edibility_source_none
        source.startsWith("wiki:") -> R.string.edibility_source_wiki
        source.startsWith("fungitastic") -> R.string.edibility_source_fungitastic
        else -> R.string.edibility_source_none
    },
)

/** Container/content colours: danger uses the error palette; everything else stays neutral so "edible" never looks like a green light. */
@Composable
fun edibilityColors(e: Edibility?): Pair<Color, Color> = when {
    e == null || e == Edibility.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    e.dangerous -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    e == Edibility.CAUTION || e == Edibility.INEDIBLE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
}

fun edibilityIcon(e: Edibility?): ImageVector = when {
    e == null || e == Edibility.UNKNOWN -> Icons.Outlined.HelpOutline
    e == Edibility.DEADLY -> Icons.Outlined.Dangerous
    e.dangerous -> Icons.Outlined.Warning
    e == Edibility.CAUTION || e == Edibility.INEDIBLE -> Icons.Outlined.Warning
    else -> Icons.Outlined.Info
}

/** Compact tonal badge "Reference: deadly poisonous" used in lists, species pages and the comparison. */
@Composable
fun EdibilityBadge(e: Edibility?, modifier: Modifier = Modifier, showUnknown: Boolean = false) {
    if ((e == null || e == Edibility.UNKNOWN) && !showUnknown) return
    val (bg, fg) = edibilityColors(e)
    Row(
        modifier.clip(MaterialTheme.shapes.small).background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(edibilityIcon(e), contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Text(edibilityLabel(e), style = MaterialTheme.typography.labelMedium, color = fg)
    }
}
