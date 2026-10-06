package com.tellyo.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkStatus {
    Available,
    Unavailable,
}

interface NetworkMonitor {
    val status: StateFlow<NetworkStatus>
}

/** Observes validated Internet connectivity for retry and diagnostics decisions. */
@Singleton
class AndroidNetworkMonitor(
    context: Context,
) : NetworkMonitor {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val _status = MutableStateFlow(currentStatus())
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()
    private var defaultNetwork: Network? = connectivityManager.activeNetwork

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Availability alone does not mean that this network has Internet.
            // Wait for the default network's validated capabilities.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) defaultNetwork = network
        }

        override fun onLost(network: Network) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                _status.value = currentStatus()
            } else if (network == defaultNetwork) {
                defaultNetwork = null
                _status.value = NetworkStatus.Unavailable
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val active = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                defaultNetwork
            } else connectivityManager.activeNetwork
            if (network == active) {
                _status.value = statusFor(capabilities)
            }
        }
    }

    init {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } else {
                connectivityManager.registerNetworkCallback(
                    NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                    callback,
                )
            }
        }.onFailure {
            _status.value = currentStatus()
        }
    }

    private fun currentStatus(): NetworkStatus =
        connectivityManager.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)
            ?.let(::statusFor)
            ?: NetworkStatus.Unavailable

    private fun statusFor(capabilities: NetworkCapabilities): NetworkStatus =
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            NetworkStatus.Available
        } else {
            NetworkStatus.Unavailable
        }
}
