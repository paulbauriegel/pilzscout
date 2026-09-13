package de.pilzscout.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/**
 * Single back stack rooted at [HomeKey]. The three tabs are pages inside the home entry, so switching
 * tabs never touches the stack; detail screens push on top of it.
 */
class AppBackStack(start: NavKey = HomeKey) {

    val backStack: SnapshotStateList<NavKey> = mutableStateListOf(start)

    /** One-shot request for the home pager, consumed the next time the home entry is composed. */
    var pendingHomePage: Int? by mutableStateOf(null)

    fun push(key: NavKey) {
        backStack.add(key)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Clears everything above the home entry and shows the given tab there. */
    fun popToRoot(homePage: Int = 0) {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        pendingHomePage = homePage
    }
}
