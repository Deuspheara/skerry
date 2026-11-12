package com.twitchplayer.tv.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the connection costs the viewer, provided by the platform (Android: `ConnectivityManager.isActiveNetworkMetered`). Only what the
 * Discover feed's autoplay needs: whether the network is metered (mobile data, a hotspot, a data-saver Wi-Fi).
 */
interface NetworkStatus {
    /** True while the active network is metered or there is none; read live, it changes when the viewer joins or leaves a Wi-Fi. */
    val metered: StateFlow<Boolean>

    companion object {
        /** A connection that is never metered (tests, platforms without the information). */
        val Unmetered: NetworkStatus = object : NetworkStatus {
            override val metered: StateFlow<Boolean> = MutableStateFlow(false)
        }
    }
}
