package com.twitchplayer.tv.platform.apple

import com.twitchplayer.tv.core.platform.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/**
 * "Metered" is Network.framework's *expensive* (cellular, a personal hotspot) or *constrained* (Low Data Mode) path, or no
 * connection at all. Starts at true (the safe answer for the autoplay) until the monitor's first report, which arrives at once.
 */
class AppleNetworkStatus : NetworkStatus {
    private val state = MutableStateFlow(true)
    override val metered: StateFlow<Boolean> = state.asStateFlow()

    private val monitor = nw_path_monitor_create().also { monitor ->
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_set_update_handler(monitor) { path ->
            state.value = nw_path_get_status(path) != nw_path_status_satisfied || nw_path_is_expensive(path) || nw_path_is_constrained(path)
        }
        nw_path_monitor_start(monitor)
    }
}
