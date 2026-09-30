/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

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

class CredentialsCookieInterceptorTest {

    private val server = MockWebServer()
    private val cookieManager = CookieManager()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        client = OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .addNetworkInterceptor(CredentialsCookieInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a request with credentials doesn't send a stored session cookie`() {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "$SESSION_COOKIE; Path=/"))
        server.enqueue(MockResponse())

        // An anonymous request stores the session cookie.
        execute(authorization = null)
        assertEquals(1, cookieManager.cookieStore.cookies.size)

        execute(authorization = CREDENTIALS)

        server.takeRequest()
        assertNull(server.takeRequest().getHeader("Cookie"))
    }

    @Test
    fun `a response to a request with credentials doesn't store its session cookie`() {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "$SESSION_COOKIE; Path=/"))

        execute(authorization = CREDENTIALS)

        assertTrue(cookieManager.cookieStore.cookies.isEmpty())
    }

    @Test
    fun `a request without credentials keeps using cookies`() {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "$SESSION_COOKIE; Path=/"))
        server.enqueue(MockResponse())

        execute(authorization = null)
        execute(authorization = null)

        server.takeRequest()
        assertEquals(SESSION_COOKIE, server.takeRequest().getHeader("Cookie"))
    }

    private fun execute(authorization: String?) {
        val request = Request.Builder()
            .url(server.url("/ocs/v2.php/apps/spreed/api/v4/room"))
            .apply { authorization?.let { header("Authorization", it) } }
            .build()
        client.newCall(request).execute().close()
    }

    companion object {
        private const val SESSION_COOKIE = "nc_session_id=abc"
        private const val CREDENTIALS = "Basic bWFyY2VsMjp0b2tlbg=="
    }
}
