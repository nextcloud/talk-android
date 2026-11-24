/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.network

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * The device counts as online with a network that can reach other hosts, also if Android couldn't validate it, e.g. on
 * a local network without internet where the Nextcloud server is still reachable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class NetworkMonitorImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val network = connectivityManager.activeNetwork!!

    @Test
    fun `a network that Android has not validated counts as online from the start`() {
        shadowOf(connectivityManager).setNetworkCapabilities(network, unvalidatedCapabilities())

        val monitor = NetworkMonitorImpl(context)

        assertTrue(monitor.isOnline.value)
    }

    @Test
    fun `a network that Android has not validated counts as online once its capabilities are reported`() =
        runBlocking {
            val earlierCallbacks = shadowOf(connectivityManager).networkCallbacks.toSet()
            val monitor = NetworkMonitorImpl(context)
            val callback = withTimeout(TIMEOUT_MILLIS) { registeredCallback(earlierCallbacks) }

            callback.onCapabilitiesChanged(network, unvalidatedCapabilities())

            assertTrue(withTimeout(TIMEOUT_MILLIS) { monitor.isOnline.first { it } })
        }

    private suspend fun registeredCallback(
        earlierCallbacks: Set<ConnectivityManager.NetworkCallback>
    ): ConnectivityManager.NetworkCallback {
        while (true) {
            (shadowOf(connectivityManager).networkCallbacks - earlierCallbacks).firstOrNull()?.let { return it }
            delay(POLL_MILLIS)
        }
    }

    private fun unvalidatedCapabilities(): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also {
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

    companion object {
        private const val TIMEOUT_MILLIS = 5_000L
        private const val POLL_MILLIS = 10L
    }
}
