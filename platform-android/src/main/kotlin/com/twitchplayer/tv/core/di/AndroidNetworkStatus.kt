package com.twitchplayer.tv.core.di

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.twitchplayer.tv.core.platform.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the active network is metered, live. A network that cannot be read (no permission, no network) counts as metered: the one thing
 * this decides is whether a stream may start playing by itself, and being wrong in that direction costs a tap, not the viewer's data.
 */
@SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE is declared by each app (TV and phone manifests); a library cannot see it.
class AndroidNetworkStatus(context: Context) : NetworkStatus {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val mutable = MutableStateFlow(read())
    override val metered: StateFlow<Boolean> = mutable.asStateFlow()

    private fun read(): Boolean = runCatching { manager?.isActiveNetworkMetered ?: true }.getOrDefault(true)

    init {
        runCatching {
            manager?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        mutable.value = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    }

                    override fun onLost(network: Network) {
                        mutable.value = true
                    }
                },
            )
        }
    }
}
