package com.twitchplayer.tv.navigation

import androidx.navigation3.runtime.NavKey

/** Back-stack rules of the app, independent of Compose so they can be unit tested. */
class AppNavigator(private val stack: MutableList<NavKey>) {
    val top: NavKey? get() = stack.lastOrNull()

    /** Rail selection replaces the whole stack. */
    fun openTopLevel(key: NavKey) {
        stack.clear()
        stack.add(key)
    }

    fun push(key: NavKey) {
        stack.add(key)
    }

    /** "Watch": returning to the player we came from pops instead of stacking another one. */
    fun watch(login: String) {
        val previous = stack.getOrNull(stack.lastIndex - 1)
        if (top is ChannelKey && previous == PlayerKey(login)) pop() else push(PlayerKey(login))
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /**
     * Back from a top-level screen: deeper stacks pop, other tabs return Home, and Home asks to quit.
     * Returns true when the quit confirmation must be shown.
     */
    fun back(): Boolean = when {
        stack.size > 1 -> {
            pop()
            false
        }

        top != HomeKey -> {
            openTopLevel(HomeKey)
            false
        }

        else -> true
    }
}
