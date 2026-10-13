/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.wipe.WipeCheckResponseDto
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking
import retrofit2.Response
import java.io.IOException

/**
 * An account must only be removed when its server confirms that it requested a wipe, so all other answers mean no wipe.
 */
class RemoteWipeHandlerTest {

    private val ncApiCoroutines: NcApiCoroutines = mock()
    private val handler = RemoteWipeHandler(ncApiCoroutines)

    private val user = User(id = USER_ID, username = "userA", token = "tokenA", baseUrl = BASE_URL)

    @Test
    fun `a wipe is requested when the server answers so`() {
        wheneverBlocking { ncApiCoroutines.checkRemoteWipe(any(), any()) } doReturn
            Response.success(WipeCheckResponseDto(wipe = true))

        assertTrue(runBlocking { handler.isWipeRequested(user) })
    }

    @Test
    fun `no wipe is requested when the server did not request one`() {
        wheneverBlocking { ncApiCoroutines.checkRemoteWipe(any(), any()) } doReturn
            Response.error(HTTP_NOT_FOUND, "".toResponseBody())

        assertFalse(runBlocking { handler.isWipeRequested(user) })
    }

    @Test
    fun `no wipe is requested when the server answers so`() {
        wheneverBlocking { ncApiCoroutines.checkRemoteWipe(any(), any()) } doReturn
            Response.success(WipeCheckResponseDto(wipe = false))

        assertFalse(runBlocking { handler.isWipeRequested(user) })
    }

    @Test
    fun `no wipe is requested when the wipe check is rate limited`() {
        wheneverBlocking { ncApiCoroutines.checkRemoteWipe(any(), any()) } doReturn
            Response.error(HTTP_TOO_MANY_REQUESTS, "".toResponseBody())

        assertFalse(runBlocking { handler.isWipeRequested(user) })
    }

    @Test
    fun `no wipe is requested when the server cannot be asked`() {
        wheneverBlocking { ncApiCoroutines.checkRemoteWipe(any(), any()) } doSuspendableAnswer {
            throw IOException("unreachable")
        }

        assertFalse(runBlocking { handler.isWipeRequested(user) })
    }

    @Test
    fun `the server is not asked for an account without a token`() {
        assertFalse(runBlocking { handler.isWipeRequested(user.copy(token = null)) })
        verifyBlocking(ncApiCoroutines, never()) { checkRemoteWipe(any(), any()) }
    }

    companion object {
        private const val USER_ID = 1L
        private const val BASE_URL = "https://cloud.example.com"
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
