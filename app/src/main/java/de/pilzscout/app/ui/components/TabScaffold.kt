package de.pilzscout.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import de.pilzscout.app.R

/**
 * Top-level tab chrome: a Material 3 Expressive medium flexible top app bar (tab actions and the settings
 * gear on the top row, title and subtitle below with the spec's own margins) and the content. The bar is
 * transparent and collapses as the content scrolls. It paints no background: the tab is a page of the home
 * pager, which draws the shared forest backdrop behind it and the bottom bar over it.
 */
@Composable
fun TabScaffold(
    title: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    headerIcon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Column(modifier.fillMaxSize().statusBarsPadding().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        MediumFlexibleTopAppBar(
            // Icon, title and subtitle share the title slot so the text stays aligned beside the icon
            // in both the expanded and the collapsed state.
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (headerIcon != null) HeaderIcon(headerIcon)
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            expandedHeight = TopAppBarDefaults.MediumFlexibleAppBarWithSubtitleExpandedHeight,
            actions = {
                actions()
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.action_settings)) }
            },
            windowInsets = WindowInsets(0.dp),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            scrollBehavior = scrollBehavior,
        )
        Box(Modifier.fillMaxSize()) { content(PaddingValues(bottom = floatingNavBarInset())) }
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector) {
    Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/**
 * Vertical space the floating tab bar covers at the bottom of a home page: the bar itself plus its
 * margins and the system navigation inset it sits above. Pages keep their content clear of this.
 */
@Composable
fun floatingNavBarInset(): Dp = 96.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
fun BrandRow(onOpenSettings: (() -> Unit)?, leading: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        Image(
            painterResource(R.drawable.brand_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(34.dp).clip(MaterialTheme.shapes.extraLarge).background(colorResource(R.color.ic_launcher_background)).padding(3.dp),
        )
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp).weight(1f))
        if (onOpenSettings != null) {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.action_settings)) }
        }
    }
}

/**
 * Header row laid out like a Material 3 top app bar on a compact window: 16dp screen margins, a 64dp
 * minimum row height, 16dp between the leading icon and the text, and trailing icon buttons whose 48dp
 * touch targets end 4dp from the edge so the glyphs sit on the 16dp margin. Tab-specific [actions] come
 * first, the settings gear last.
 */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onOpenSettings: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions()
            if (onOpenSettings != null) {
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.action_settings)) }
            }
        }
    }
}
