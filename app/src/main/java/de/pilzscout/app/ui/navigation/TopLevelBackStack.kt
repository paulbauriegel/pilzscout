package de.pilzscout.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/**
 * One back stack per bottom-navigation tab, flattened into a single list for NavDisplay.
 * Switching tabs keeps each tab's own history; system back inside a tab pops that tab,
 * and back on a tab root returns to the start tab.
 */
class TopLevelBackStack(private val startKey: TopLevelKey) {

    private val stacks = mutableStateMapOf<TopLevelKey, SnapshotStateList<NavKey>>(
        startKey to mutableStateListOf(startKey),
    )

    var topLevelKey: TopLevelKey by mutableStateOf(startKey)
        private set

    val backStack: SnapshotStateList<NavKey> = mutableStateListOf(startKey)

    private fun rebuild() {
        backStack.clear()
        if (topLevelKey != startKey) backStack.addAll(stacks.getValue(startKey))
        backStack.addAll(stacks.getValue(topLevelKey))
    }

    fun switchTo(key: TopLevelKey) {
        if (stacks[key] == null) stacks[key] = mutableStateListOf(key)
        topLevelKey = key
        rebuild()
    }

    fun push(key: NavKey) {
        stacks.getValue(topLevelKey).add(key)
        rebuild()
    }

    fun pop() {
        val current = stacks.getValue(topLevelKey)
        current.removeLastOrNull()
        if (current.isEmpty()) {
            stacks.remove(topLevelKey)
            topLevelKey = startKey
        }
        rebuild()
    }
}
