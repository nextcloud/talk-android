/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ssl.SSLSocketFactoryCompat
import com.nextcloud.talk.utils.ssl.TrustManager
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking

/**
 * A 401 must only remove the account whose credentials the request carried. These cases must never remove one.
 */
class RemoteWipeInterceptorTest {

    private val userManager: UserManager = mock()
    private val interceptor = RemoteWipeInterceptor(
        userManager,
        mock<Context>(),
        mock<SSLSocketFactoryCompat>(),
        mock<TrustManager>()
    )

    private val userA = User(id = 1, username = "userA", token = "tokenA", baseUrl = BASE_URL)
    private val userB = User(id = 2, username = "userB", token = "tokenB", baseUrl = BASE_URL)

    @Test
    fun `a 401 for a request without credentials removes no account`() {
        wheneverBlocking { userManager.getUsers() } doReturn listOf(userA, userB)

        val response = interceptor.intercept(chainAnswering401(Request.Builder().url(PREVIEW_URL).build()))

        assertEquals(HTTP_UNAUTHORIZED, response.code)
        verifyBlocking(userManager, never()) { scheduleUserForDeletionWithId(any()) }
    }

    @Test
    fun `a 401 for credentials of no stored account removes no account`() {
        wheneverBlocking { userManager.getUsers() } doReturn listOf(userA, userB)
        val request = Request.Builder()
            .url(PREVIEW_URL)
            .header("Authorization", Credentials.basic("userB", "outdatedToken"))
            .build()

        interceptor.intercept(chainAnswering401(request))

        verifyBlocking(userManager, never()) { scheduleUserForDeletionWithId(any()) }
    }

    @Test
    fun `a 401 after a redirect to another host removes no account`() {
        wheneverBlocking { userManager.getUsers() } doReturn listOf(userA, userB)
        val original = Request.Builder()
            .url(PREVIEW_URL)
            .header("Authorization", Credentials.basic("userA", "tokenA"))
            .build()
        // OkHttp drops the credentials when following a redirect to another host.
        val redirected = Request.Builder().url("https://other.example.org/image").build()

        interceptor.intercept(chainAnswering401(original, answeredRequest = redirected))

        verifyBlocking(userManager, never()) { scheduleUserForDeletionWithId(any()) }
    }

    private fun chainAnswering401(request: Request, answeredRequest: Request = request): Interceptor.Chain {
        val response = Response.Builder()
            .request(answeredRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(HTTP_UNAUTHORIZED)
            .message("Unauthorized")
            .build()
        return mock {
            on { request() } doReturn request
            on { proceed(any()) } doReturn response
        }
    }

    companion object {
        private const val BASE_URL = "https://cloud.example.com"
        private const val PREVIEW_URL = "$BASE_URL/index.php/core/preview?fileId=1"
        private const val HTTP_UNAUTHORIZED = 401
    }
}
