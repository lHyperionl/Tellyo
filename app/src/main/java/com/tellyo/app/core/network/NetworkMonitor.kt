package com.tellyo.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _status.value = NetworkStatus.Available
        }

        override fun onLost(network: Network) {
            _status.value = currentStatus()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (network == connectivityManager.activeNetwork) {
                _status.value = statusFor(capabilities)
            }
        }
    }

    init {
        runCatching {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
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
