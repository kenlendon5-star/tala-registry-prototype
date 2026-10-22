package ph.tala.registry.ui.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * D16 basemap availability: airplane mode or a lost connection switches the map from
 * OpenStreetMap tiles to its offline coordinate plot. Reads stay on Room either way,
 * so the basemap state can never make saved coordinates inaccessible.
 */
class ConnectivityMonitor(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val mutable = MutableStateFlow(current())
    val online: StateFlow<Boolean> = mutable.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
        override fun onUnavailable() = refresh()
    }

    fun start() {
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        refresh()
    }

    fun stop() {
        runCatching { manager.unregisterNetworkCallback(callback) }
    }

    /** Forces an immediate connectivity re-check. Useful for tests and manual triggers. */
    fun refresh() { mutable.value = current() }

    private fun current(): Boolean {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
