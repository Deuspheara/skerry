package com.twitchplayer.phone.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey

/**
 * Back-stack rules of the phone app, independent of Compose so they can be unit tested. Each tab has its own stack
 * ([stacks], one list per [PhoneTab], each starting with the tab root); switching tabs keeps what was open in the others.
 */
class PhoneNavigator(
    private val stacks: Map<PhoneTab, MutableList<NavKey>>,
    selected: PhoneTab = PhoneTab.Home,
    private val onSelectedChanged: (PhoneTab) -> Unit = {},
) {
    /** Observable: the bar and the displayed stack follow it. */
    var selected: PhoneTab by mutableStateOf(selected)
        private set

    /** The stack the NavDisplay shows. */
    val currentStack: MutableList<NavKey> get() = stacks.getValue(selected)

    val top: NavKey get() = currentStack.last()

    /** The tab bar shows on tab roots only: detail pages use the whole screen. */
    val showsNavigation: Boolean get() = currentStack.size == 1

    /** Tab bar tap: another tab opens as it was left; the selected tab pops to its root. */
    fun selectTab(tab: PhoneTab) {
        if (tab == selected) {
            val stack = currentStack
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            selected = tab
            onSelectedChanged(tab)
        }
    }

    fun push(key: NavKey) {
        if (top != key) currentStack.add(key)
    }

    /**
     * Opens a page that belongs to browsing (a channel opened from the player) on the tab the viewer is in, except Settings, which is not a
     * place to browse from: that one goes to Home. Never stacks a page that is already on top.
     */
    fun pushOnContentTab(key: NavKey) {
        if (selected == PhoneTab.Settings) {
            selected = PhoneTab.Home
            onSelectedChanged(PhoneTab.Home)
        }
        push(key)
    }

    fun pop() {
        val stack = currentStack
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** True when [back] would do something: a deeper page, or a tab other than Home. False means "leave the app". */
    val canGoBack: Boolean get() = currentStack.size > 1 || selected != PhoneTab.Home

    /** Back: deeper pages pop, other tabs return to Home, and Home (at its root) is the end. Returns false when nothing was left to do. */
    fun back(): Boolean = when {
        currentStack.size > 1 -> {
            pop()
            true
        }

        selected != PhoneTab.Home -> {
            selected = PhoneTab.Home
            onSelectedChanged(PhoneTab.Home)
            true
        }

        else -> false
    }
}
