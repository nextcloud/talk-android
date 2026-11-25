/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.network

import android.app.Application
import com.nextcloud.talk.data.network.ServerConnectionInterceptor.ConnectionCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Provider

@Suppress("TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ServerConnectionInterceptorTest {

    private val server = MockWebServer()
    private val deviceOnline = MutableStateFlow(true)
    private val networkMonitor = mock<NetworkMonitor> { on { isOnline } doReturn deviceOnline }
    private lateinit var client: OkHttpClient
    private val monitor = ServerConnectionMonitor(networkMonitor, Provider { client })
    private lateinit var baseUrl: String
    private lateinit var interceptor: ServerConnectionInterceptor

    @Before
    fun setUp() {
        server.start()
        baseUrl = server.url("/").toString().removeSuffix("/")
        interceptor = ServerConnectionInterceptor(monitor)
        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addNetworkInterceptor(interceptor.connectionMarker)
            .readTimeout(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a server that refuses connections is unreachable`() =
        runTest {
            server.close()

            executeIgnoringFailure()

            assertEquals(ServerConnectionStatus.SERVER_UNREACHABLE, monitor.statusFlow(baseUrl).first())
        }

    @Test
    fun `the device counting as offline shows no connection, even if the server answers`() =
        runTest {
            deviceOnline.value = false
            server.enqueue(MockResponse())

            executeIgnoringFailure()

            assertEquals(ServerConnectionStatus.SERVER_UNREACHABLE, monitor.statusFlow(baseUrl).first())
        }

    // The check is a real request, so this test runs in real time: in virtual time, the next recheck would pass before
    // the answer.
    @Test
    fun `the server is checked right away when the device comes back online`() =
        runBlocking {
            deviceOnline.value = false
            monitor.report(baseUrl.toHttpUrl(), ServerConnectionStatus.SERVER_UNREACHABLE)
            server.enqueue(MockResponse.Builder().code(HTTP_NO_CONTENT).build())
            val connected = async(Dispatchers.Default) {
                monitor.statusFlow(baseUrl).first { it == ServerConnectionStatus.CONNECTED }
            }
            delay(COLLECTION_START_MILLIS)

            deviceOnline.value = true

            withTimeout(MAX_CHECK_MILLIS) { connected.await() }
            assertEquals("/index.php/204", takeRequestOrNull()?.url?.encodedPath)
        }

    @Test
    fun `a connection check answered by something else than Nextcloud is unreachable`() =
        runTest {
            server.enqueue(MockResponse.Builder().body("<html>Log in to use the Wi-Fi</html>").build())

            executeIgnoringFailure(
                Request.Builder()
                    .url("$baseUrl/index.php/204")
                    .tag(ConnectionCheck::class.java, ConnectionCheck)
                    .build()
            )

            assertEquals(ServerConnectionStatus.SERVER_UNREACHABLE, monitor.statusFlow(baseUrl).first())
        }

    @Test
    fun `a timeout after the server was reached doesn't make it unreachable`() =
        runTest {
            server.enqueue(MockResponse.Builder().headersDelay(READ_TIMEOUT_MILLIS * 4, TimeUnit.MILLISECONDS).build())

            executeIgnoringFailure()

            assertEquals(ServerConnectionStatus.CONNECTED, monitor.statusFlow(baseUrl).first())
        }

    @Test
    fun `websocket requests are not reported`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_BAD_GATEWAY).build())

            executeIgnoringFailure(Request.Builder().url(baseUrl).header("Upgrade", "websocket").build())

            assertEquals(ServerConnectionStatus.CONNECTED, monitor.statusFlow(baseUrl).first())
        }

    @Test
    fun `calls are not kept after they finished`() {
        server.enqueue(MockResponse())
        server.enqueue(MockResponse())

        executeIgnoringFailure()
        executeIgnoringFailure(Request.Builder().url(baseUrl).header("Upgrade", "websocket").build())

        assertEquals(0, interceptor.connectedCalls.size)
    }

    @Test
    fun `a gateway error is reported as unreachable server`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_BAD_GATEWAY).build())

            executeIgnoringFailure()

            assertEquals(ServerConnectionStatus.SERVER_UNREACHABLE, monitor.statusFlow(baseUrl).first())
        }

    @Test
    fun `an unreachable server is checked right away`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().code(HTTP_BAD_GATEWAY).build())
            server.enqueue(MockResponse.Builder().code(HTTP_NO_CONTENT).build())
            val statuses = mutableListOf<ServerConnectionStatus>()
            val collector = launch(Dispatchers.Default) { monitor.statusFlow(baseUrl).collect { statuses.add(it) } }
            delay(COLLECTION_START_MILLIS)

            executeIgnoringFailure()
            takeRequestOrNull()
            assertEquals("/index.php/204", takeRequestOrNull()?.url?.encodedPath)
            delay(COLLECTION_START_MILLIS)
            collector.cancel()

            assertEquals(
                listOf(
                    ServerConnectionStatus.CONNECTED,
                    ServerConnectionStatus.SERVER_UNREACHABLE,
                    ServerConnectionStatus.CONNECTED
                ),
                statuses
            )
        }

    @Test
    fun `an old status is checked right away when its collection starts`() =
        runBlocking {
            monitor.report(baseUrl.toHttpUrl(), ServerConnectionStatus.SERVER_UNREACHABLE)
            server.enqueue(MockResponse.Builder().code(HTTP_NO_CONTENT).build())

            withTimeout(MAX_CHECK_MILLIS) {
                monitor.statusFlow(baseUrl).first { it == ServerConnectionStatus.CONNECTED }
            }
            Unit
        }

    @Test
    fun `the maintenance mode header is reported as maintenance mode`() =
        runTest {
            server.enqueue(
                MockResponse.Builder()
                    .code(HTTP_SERVICE_UNAVAILABLE)
                    .addHeader("X-Nextcloud-Maintenance-Mode", "1")
                    .build()
            )

            executeIgnoringFailure()

            assertEquals(ServerConnectionStatus.MAINTENANCE_MODE, monitor.statusFlow(baseUrl).first())
        }

    @Test
    fun `any answer of the server after a failure is reported as connected`() =
        runTest {
            monitor.report(baseUrl.toHttpUrl(), ServerConnectionStatus.SERVER_UNREACHABLE)
            server.enqueue(MockResponse.Builder().code(HTTP_SERVICE_UNAVAILABLE).build())

            executeIgnoringFailure()

            assertEquals(ServerConnectionStatus.CONNECTED, monitor.statusFlow(baseUrl).first())
        }

    @Test
    fun `the status of another server doesn't affect this one`() =
        runTest {
            monitor.report("https://other.example.com/".toHttpUrl(), ServerConnectionStatus.SERVER_UNREACHABLE)

            assertEquals(ServerConnectionStatus.CONNECTED, monitor.statusFlow(baseUrl).first())
        }

    /** The next request the server got, or null if it got none in time, instead of waiting forever. */
    private fun takeRequestOrNull() = server.takeRequest(MAX_CHECK_MILLIS, TimeUnit.MILLISECONDS)

    private fun executeIgnoringFailure(
        request: Request = Request.Builder().url("$baseUrl/ocs/v2.php/cloud/user").build()
    ) {
        try {
            client.newCall(request).execute().close()
        } catch (_: IOException) {
            // The status is reported before the failure is passed on.
        }
    }

    companion object {
        private const val HTTP_NO_CONTENT = 204
        private const val HTTP_BAD_GATEWAY = 502
        private const val HTTP_SERVICE_UNAVAILABLE = 503
        private const val READ_TIMEOUT_MILLIS = 200L
        private const val COLLECTION_START_MILLIS = 200L
        private const val MAX_CHECK_MILLIS = 5000L
    }
}
