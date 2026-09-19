/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Andy Scherzinger <andy.scherzinger@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.content.Context
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ssl.SSLSocketFactoryCompat
import com.nextcloud.talk.utils.ssl.TrustManager
import io.reactivex.Single
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier

class RemoteWipeInterceptorTest {

    private val server = MockWebServer()
    private val userManager: UserManager = mock()
    private val context: Context = mock()
    private val sslSocketFactory: SSLSocketFactoryCompat = mock()
    private val trustManager: TrustManager = mock()

    private lateinit var interceptor: RemoteWipeInterceptor
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')

        whenever(trustManager.acceptedIssuers).thenReturn(emptyArray<X509Certificate>())
        whenever(trustManager.getHostnameVerifier(any())).thenReturn(HostnameVerifier { _, _ -> true })
        whenever(userManager.users).thenReturn(Single.just(listOf(user())))
        whenever(userManager.scheduleUserForDeletionWithId(any())).thenReturn(Single.just(true))

        interceptor = RemoteWipeInterceptor(userManager, context, sslSocketFactory, trustManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a 401 the server did not ask a wipe for keeps the account`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("""{"wipe":false}"""))

        val response = interceptor.intercept(chainReturning(HTTP_UNAUTHORIZED))

        assertEquals(HTTP_UNAUTHORIZED, response.code)
        verify(userManager, never()).scheduleUserForDeletionWithId(any())
    }

    @Test
    fun `a 401 while the wipe check itself is refused keeps the account`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_TOO_MANY_REQUESTS))

        val response = interceptor.intercept(chainReturning(HTTP_UNAUTHORIZED))

        assertEquals(HTTP_UNAUTHORIZED, response.code)
        verify(userManager, never()).scheduleUserForDeletionWithId(any())
    }

    @Test
    fun `a response other than 401 is passed through untouched`() {
        val response = interceptor.intercept(chainReturning(HTTP_OK))

        assertEquals(HTTP_OK, response.code)
        verify(userManager, never()).scheduleUserForDeletionWithId(any())
        assertEquals(0, server.requestCount)
    }

    private fun chainReturning(code: Int): Interceptor.Chain {
        val request = Request.Builder().url("$baseUrl/ocs/v2.php/apps/spreed/api/v4/room").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .body("".toResponseBody())
            .build()

        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(any())).thenReturn(response)
        return chain
    }

    private fun user() = User(id = ACCOUNT_ID, userId = "me", username = "me", baseUrl = baseUrl, token = "token")

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val HTTP_OK = 200
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
