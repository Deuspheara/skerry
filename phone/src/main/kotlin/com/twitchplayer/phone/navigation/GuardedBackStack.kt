package com.twitchplayer.phone.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/** [androidx.navigation3.runtime.rememberNavBackStack] with a saved form that cannot crash the restore (see [NavKeyCodec]). */
@Composable
fun rememberGuardedBackStack(root: NavKey): NavBackStack<NavKey> = rememberSaveable(
    root,
    saver = Saver<NavBackStack<NavKey>, List<String>>(
        save = { stack -> stack.map(NavKeyCodec::encode) },
        restore = { saved -> NavKeyCodec.restore(saved, root).let { keys -> NavBackStack(keys.first()).apply { addAll(keys.drop(1)) } } },
    ),
) { NavBackStack(root) }
