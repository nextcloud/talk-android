/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.network

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Provider

/**
 * [ServerConnectionMonitor.reconnectionFlow] tells when what failed while the server couldn't be used can be sent again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ServerConnectionMonitorTest {

    private val deviceOnline = MutableStateFlow(true)
    private val networkMonitor = mock<NetworkMonitor> { on { isOnline } doReturn deviceOnline }

    // the connection checks are never answered, the status only changes by the reports of the test
    private val call = mock<Call>()
    private val client = mock<OkHttpClient> { on { newCall(any()) } doReturn call }
    private val monitor = ServerConnectionMonitor(networkMonitor, Provider { client })

    @Test
    fun `the server answering again while the network stayed up counts as reconnect`() =
        runTest {
            val reconnects = collectReconnects()

            monitor.report(BASE_URL.toHttpUrl(), ServerConnectionStatus.SERVER_UNREACHABLE)
            runCurrent()
            assertEquals(0, reconnects.size)

            monitor.report(BASE_URL.toHttpUrl(), ServerConnectionStatus.CONNECTED)
            runCurrent()
            assertEquals(1, reconnects.size)
        }

    @Test
    fun `the device getting its network back counts as reconnect`() =
        runTest {
            val reconnects = collectReconnects()

            deviceOnline.value = false
            runCurrent()
            deviceOnline.value = true
            runCurrent()

            assertEquals(1, reconnects.size)
        }

    @Test
    fun `a server that could be used from the start is no reconnect`() =
        runTest {
            val reconnects = collectReconnects()

            monitor.report(BASE_URL.toHttpUrl(), ServerConnectionStatus.CONNECTED)
            runCurrent()

            assertEquals(0, reconnects.size)
        }

    private fun TestScope.collectReconnects(): List<Unit> {
        val reconnects = mutableListOf<Unit>()
        backgroundScope.launch { monitor.reconnectionFlow(BASE_URL).collect { reconnects.add(it) } }
        runCurrent()
        return reconnects
    }

    companion object {
        private const val BASE_URL = "https://cloud.example.com"
    }
}
