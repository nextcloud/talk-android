/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitorImpl @Inject constructor(private val context: Context) : NetworkMonitor {

    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!

    override val isOnlineLiveData: LiveData<Boolean>
        get() = isOnline.asLiveData()

    override val isOnline: StateFlow<Boolean> get() = _isOnline

    private val _isOnline: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val connected = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
                trySend(connected)
                Log.d(TAG, "Network status changed: $connected")
            }

            override fun onUnavailable() {
                super.onUnavailable()
                trySend(false)
                Log.d(TAG, "Network status: onUnavailable")
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                trySend(false)
                Log.d(TAG, "Network status: onLost")
            }

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                trySend(true)
                Log.d(TAG, "Network status: onAvailable")
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.stateIn(
        CoroutineScope(Dispatchers.IO),
        SharingStarted.WhileSubscribed(COROUTINE_TIMEOUT),
        false
    )

    companion object {
        private val TAG = NetworkMonitorImpl::class.java.simpleName
        private const val COROUTINE_TIMEOUT = 5000L
    }
}
