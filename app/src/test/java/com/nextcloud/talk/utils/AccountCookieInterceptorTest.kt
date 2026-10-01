/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import okhttp3.Credentials
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.CookieManager

class AccountCookieInterceptorTest {

    private val server = MockWebServer()
    private val sharedCookieManager = CookieManager()
    private val interceptor = AccountCookieInterceptor()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        client = OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(sharedCookieManager))
            .addNetworkInterceptor(interceptor)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `an account keeps its server session between requests`() {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "$SESSION_A; Path=/"))
        server.enqueue(MockResponse())

        execute(CREDENTIALS_A)
        execute(CREDENTIALS_A)

        server.takeRequest()
        assertEquals(SESSION_A, server.takeRequest().getHeader("Cookie"))
    }

    @Test
    fun `another account on the same server doesn't get the session`() {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "$SESSION_A; Path=/"))
        server.enqueue(MockResponse())

        execute(CREDENTIALS_A)
        execute(CREDENTIALS_B)

        server.takeRequest()
        assertNull(server.takeRequest().getHeader("Cookie"))
    }

    @Test
    fun `a removed account's session isn't sent anymore`() {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "$SESSION_A; Path=/"))
        server.enqueue(MockResponse())

        execute(CREDENTIALS_A)
        interceptor.removeCookiesOf(CREDENTIALS_A)
        execute(CREDENTIALS_A)

        server.takeRequest()
        assertNull(server.takeRequest().getHeader("Cookie"))
    }

    @Test
    fun `cookies of an account are kept out of the shared cookie store`() {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "$SESSION_A; Path=/"))
        server.enqueue(MockResponse())

        execute(CREDENTIALS_A)
        execute(authorization = null)

        assertTrue(sharedCookieManager.cookieStore.cookies.isEmpty())
        server.takeRequest()
        assertNull(server.takeRequest().getHeader("Cookie"))
    }

    @Test
    fun `a session cookie of the shared store isn't sent with credentials`() {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "$SESSION_SHARED; Path=/"))
        server.enqueue(MockResponse())

        execute(authorization = null)
        execute(CREDENTIALS_A)

        server.takeRequest()
        assertNull(server.takeRequest().getHeader("Cookie"))
    }

    @Test
    fun `requests without credentials keep using the shared cookie store`() {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "$SESSION_SHARED; Path=/"))
        server.enqueue(MockResponse())

        execute(authorization = null)
        execute(authorization = null)

        server.takeRequest()
        assertEquals(SESSION_SHARED, server.takeRequest().getHeader("Cookie"))
    }

    private fun execute(authorization: String?) {
        val request = Request.Builder()
            .url(server.url("/ocs/v2.php/apps/spreed/api/v4/room"))
            .apply { authorization?.let { header("Authorization", it) } }
            .build()
        client.newCall(request).execute().close()
    }

    companion object {
        private const val SESSION_A = "nc_session_id=a"
        private const val SESSION_SHARED = "nc_session_id=shared"
        private val CREDENTIALS_A = Credentials.basic("userA", "tokenA")
        private val CREDENTIALS_B = Credentials.basic("userB", "tokenB")
    }
}
