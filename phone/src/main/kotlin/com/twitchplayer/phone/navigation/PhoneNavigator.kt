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

    /**
     * Opens [key] on the current tab. A page that is already in the stack is not opened a second time: the stack returns to it (opening a
     * channel that is already the page on top does nothing; one further down pops what was opened after it).
     */
    fun push(key: NavKey) {
        val stack = currentStack
        val at = stack.lastIndexOf(key)
        if (at < 0) {
            stack.add(key)
        } else {
            while (stack.lastIndex > at) stack.removeAt(stack.lastIndex)
        }
    }

    /**
     * The viewer signed out: Following has nothing to show without one, so its pages are dropped (a later sign-in finds it at its root, not
     * on a channel or Settings page from before) and, if it was the tab being looked at, Home takes over.
     */
    fun onSignedOut() {
        val following = stacks.getValue(PhoneTab.Following)
        while (following.size > 1) following.removeAt(following.lastIndex)
        if (selected == PhoneTab.Following) {
            selected = PhoneTab.Home
            onSelectedChanged(PhoneTab.Home)
        }
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
