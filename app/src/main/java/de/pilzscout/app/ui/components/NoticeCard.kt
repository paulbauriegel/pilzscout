package de.pilzscout.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * One layout for every inline notice (edibility, safety, recommendation, correction) so they read as a family.
 *
 * M3 Expressive: filled card on a tonal container, `largeIncreased` corner token, 8dp spacing grid,
 * emphasized title. The icon sits in the header row only; all body text is flush with the card's
 * left content edge instead of being indented next to the icon.
 *
 * Colour pairs must be tonal (`xContainer` + `onXContainer`); the content colour is provided to
 * children via `LocalContentColor`, so callers don't need to pass it to every `Text`.
 */
@Composable
fun NoticeCard(
    icon: ImageVector,
    title: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            }
            content()
        }
    }
}

/** Convenience for the common "warning on tertiary container" tone. */
@Composable
fun WarningNoticeCard(icon: ImageVector, title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    NoticeCard(icon, title, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, modifier, content)

/**
 * A nested tonal block inside a [NoticeCard] for a sub-topic that carries its own tone, e.g. a
 * "risk of confusion" section on the error container inside an otherwise neutral edibility card.
 * Uses the `medium` corner token so it reads as contained by the outer card.
 */
@Composable
fun NoticeInset(
    icon: ImageVector,
    title: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(containerColor).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmallEmphasized)
            }
            content()
        }
    }
}
