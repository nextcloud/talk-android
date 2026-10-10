/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.IOException
import java.util.concurrent.TimeUnit

class RejectedCredentialsInterceptorTest {

    private var now = 0L
    private val interceptor = RejectedCredentialsInterceptor { now }

    private val credentialsA = Credentials.basic("userA", "tokenA")
    private val credentialsB = Credentials.basic("userB", "tokenB")

    @Test
    fun `rejected credentials are not sent again`() {
        val server = FakeServer(HTTP_UNAUTHORIZED)

        interceptor.intercept(server.chainFor(request(credentialsA)))
        val response = interceptor.intercept(server.chainFor(request(credentialsA)))

        assertEquals(1, server.requestCount)
        assertEquals(HTTP_UNAUTHORIZED, response.code)
    }

    @Test
    fun `other credentials are still sent`() {
        val server = FakeServer(HTTP_UNAUTHORIZED)
        interceptor.intercept(server.chainFor(request(credentialsA)))

        server.code = HTTP_OK
        val response = interceptor.intercept(server.chainFor(request(credentialsB)))

        assertEquals(2, server.requestCount)
        assertEquals(HTTP_OK, response.code)
    }

    @Test
    fun `rejected credentials are tried again once per interval`() {
        val server = FakeServer(HTTP_UNAUTHORIZED)
        interceptor.intercept(server.chainFor(request(credentialsA)))

        now += TimeUnit.MINUTES.toMillis(5)
        interceptor.intercept(server.chainFor(request(credentialsA)))
        interceptor.intercept(server.chainFor(request(credentialsA)))

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `credentials accepted on a retry are sent as usual again`() {
        val server = FakeServer(HTTP_UNAUTHORIZED)
        interceptor.intercept(server.chainFor(request(credentialsA)))

        now += TimeUnit.MINUTES.toMillis(5)
        server.code = HTTP_OK
        interceptor.intercept(server.chainFor(request(credentialsA)))
        interceptor.intercept(server.chainFor(request(credentialsA)))

        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a 401 after a redirect that dropped the credentials does not reject them`() {
        val server = FakeServer(HTTP_UNAUTHORIZED, answeredRequest = Request.Builder().url(OTHER_HOST_URL).build())

        interceptor.intercept(server.chainFor(request(credentialsA)))
        interceptor.intercept(server.chainFor(request(credentialsA)))

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `an answer to a request sent before the rejection does not accept the credentials again`() {
        val rejectingServer = FakeServer(HTTP_UNAUTHORIZED)
        // While the long polling request waits for its answer, another request with the credentials is rejected.
        val longPollingServer = FakeServer(HTTP_OK) {
            now += 1
            interceptor.intercept(rejectingServer.chainFor(request(credentialsA)))
        }
        interceptor.intercept(longPollingServer.chainFor(request(credentialsA)))

        interceptor.intercept(rejectingServer.chainFor(request(credentialsA)))

        assertEquals(1, rejectingServer.requestCount)
    }

    @Test
    fun `a server error on a retry does not accept the credentials again`() {
        val server = FakeServer(HTTP_UNAUTHORIZED)
        interceptor.intercept(server.chainFor(request(credentialsA)))

        now += TimeUnit.MINUTES.toMillis(5)
        server.code = HTTP_SERVICE_UNAVAILABLE
        interceptor.intercept(server.chainFor(request(credentialsA)))
        interceptor.intercept(server.chainFor(request(credentialsA)))

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a retry that got no answer lets the next request try again`() {
        interceptor.intercept(FakeServer(HTTP_UNAUTHORIZED).chainFor(request(credentialsA)))

        now += TimeUnit.MINUTES.toMillis(5)
        val offlineServer = FakeServer(HTTP_OK) { throw IOException("offline") }
        assertThrows(IOException::class.java) {
            interceptor.intercept(offlineServer.chainFor(request(credentialsA)))
        }
        val server = FakeServer(HTTP_OK)
        interceptor.intercept(server.chainFor(request(credentialsA)))

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `requests without credentials are always sent`() {
        val server = FakeServer(HTTP_UNAUTHORIZED)
        val request = Request.Builder().url(URL).build()

        interceptor.intercept(server.chainFor(request))
        interceptor.intercept(server.chainFor(request))

        assertEquals(2, server.requestCount)
    }

    private fun request(credentials: String): Request =
        Request.Builder().url(URL).header("Authorization", credentials).build()

    /**
     * Answers every request with [code]. [answeredRequest] stands in for the request OkHttp sent last, e.g. after a
     * redirect. [whileAnswering] runs before the answer, e.g. for what happens while a request waits for it.
     */
    private class FakeServer(
        var code: Int,
        private val answeredRequest: Request? = null,
        private val whileAnswering: () -> Unit = {}
    ) {
        var requestCount = 0

        fun chainFor(request: Request): Interceptor.Chain =
            mock {
                on { request() } doReturn request
                on { proceed(any()) } doAnswer { invocation ->
                    requestCount++
                    whileAnswering()
                    Response.Builder()
                        .request(answeredRequest ?: invocation.getArgument(0))
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("")
                        .body("".toResponseBody())
                        .build()
                }
            }
    }

    companion object {
        private const val URL = "https://cloud.example.com/ocs/v2.php/apps/spreed/api/v4/room"
        private const val OTHER_HOST_URL = "https://other.example.org/room"
        private const val HTTP_OK = 200
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_SERVICE_UNAVAILABLE = 503
    }
}
