package de.pilzscout.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import de.pilzscout.app.R
import de.pilzscout.app.ui.components.FloatingNavBar
import de.pilzscout.app.ui.components.ForestLayers
import de.pilzscout.app.ui.components.NavItem
import de.pilzscout.app.ui.components.ParallaxForestBackdrop
import kotlinx.coroutines.launch

private class HomeTab(val id: String, val label: Int, val selected: ImageVector, val unselected: ImageVector)

private val homeTabs = listOf(
    HomeTab("identify", R.string.nav_identify, Icons.Filled.PhotoCamera, Icons.Outlined.PhotoCamera),
    HomeTab("browse", R.string.nav_browse, Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    HomeTab("history", R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
)

/**
 * The three top-level tabs as swipeable pages over one shared forest backdrop that moves with parallax,
 * plus the floating bottom bar. Tapping the bar animates the pager; back on any tab but the first returns
 * to the first, as the old per-tab back stacks did.
 *
 * [pendingPage] is a one-shot request (e.g. after "new observation" pops back to the root) that is applied
 * before the first frame and then acknowledged through [onPendingPageConsumed].
 */
@Composable
fun HomeScreen(
    pendingPage: Int?,
    onPendingPageConsumed: () -> Unit,
    layers: ForestLayers,
    identify: @Composable () -> Unit,
    browse: @Composable () -> Unit,
    history: @Composable () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { homeTabs.size })
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    if (pendingPage != null) {
        pagerState.requestScrollToPage(pendingPage)
        SideEffect(onPendingPageConsumed)
    }
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0, animationSpec = spec) }
    }

    Box(Modifier.fillMaxSize()) {
        ParallaxForestBackdrop(
            progress = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            layers = layers,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = homeTabs.size - 1,
            key = { homeTabs[it].id },
        ) { page ->
            when (page) {
                0 -> identify()
                1 -> browse()
                else -> history()
            }
        }
        FloatingNavBar(
            items = homeTabs.map { NavItem(stringResource(it.label), it.selected, it.unselected) },
            selected = pagerState.targetPage,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it, animationSpec = spec) } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
