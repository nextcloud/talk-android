/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.jobs

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import io.reactivex.Observable
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/**
 * The answer to a read marker carries the last message read by all participants. It has to reach the
 * database, otherwise the check marks of the own messages stay behind until the next sync.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ReadMarkerSyncWorkerTest {

    private val userManager: UserManager = mock()
    private val chatNetworkDataSource: ChatNetworkDataSource = mock()
    private val conversationListUpdater: ConversationListUpdater = mock()

    @Test
    fun `last common read is taken from the header of the read marker response`() {
        val response = responseWithHeader("2624")

        assertEquals(2624, ReadMarkerSyncWorker.lastCommonReadOf(response))
    }

    @Test
    fun `missing or broken header gives no last common read`() {
        assertNull(ReadMarkerSyncWorker.lastCommonReadOf(responseWithHeader(null)))
        assertNull(ReadMarkerSyncWorker.lastCommonReadOf(responseWithHeader("abc")))
    }

    @Test
    fun `a delivered read marker stores the last common read of its response`() {
        givenMarkerAnswer(responseWithHeader("2624"))

        val result = runBlocking { worker().send() }

        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(conversationListUpdater) { updateLastCommonRead("$USER_ID@$ROOM_TOKEN", 2624) }
    }

    @Test
    fun `a response without the header stores nothing but still counts as delivered`() {
        givenMarkerAnswer(responseWithHeader(null))

        val result = runBlocking { worker().send() }

        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(conversationListUpdater, never()) { updateLastCommonRead(any(), any()) }
    }

    @Test
    fun `a failed answer stores nothing and asks for another attempt`() {
        givenMarkerAnswer(responseWithHeader("2624", code = 500))

        val result = runBlocking { worker().send() }

        assertEquals(ListenableWorker.Result.retry(), result)
        verifyBlocking(conversationListUpdater, never()) { updateLastCommonRead(any(), any()) }
    }

    @Test
    fun `a failing database write does not send the delivered marker again`() {
        givenMarkerAnswer(responseWithHeader("2624"))
        wheneverBlocking { conversationListUpdater.updateLastCommonRead(any(), any()) }
            .thenThrow(IllegalStateException("database closed"))

        val result = runBlocking { worker().send() }

        assertEquals(ListenableWorker.Result.success(), result)
    }

    private fun givenMarkerAnswer(response: Response<GenericOverall>) {
        wheneverBlocking { userManager.getUserWithId(USER_ID) }.thenReturn(user())
        whenever(chatNetworkDataSource.setChatReadMarker(any(), any(), any())).thenReturn(Observable.just(response))
    }

    private fun worker(): ReadMarkerSyncWorker =
        TestListenableWorkerBuilder<ReadMarkerSyncWorker>(applicationContext())
            .setInputData(
                Data.Builder()
                    .putLong(KEY_INTERNAL_USER_ID, USER_ID)
                    .putString(KEY_ROOM_TOKEN, ROOM_TOKEN)
                    .putInt("KEY_LAST_READ_MESSAGE", MARKER)
                    .build()
            )
            .build()
            .also {
                it.userManager = userManager
                it.chatNetworkDataSource = chatNetworkDataSource
                it.conversationListUpdater = conversationListUpdater
            }

    private fun applicationContext(): Context = ApplicationProvider.getApplicationContext()

    private fun user(): User =
        User(
            id = USER_ID,
            userId = "me",
            username = "me",
            baseUrl = "https://cloud.example",
            token = "app-password",
            capabilities = CapabilitiesDto().apply {
                spreedCapability = SpreedCapabilityDto().apply { features = listOf(SpreedFeatures.CHAT_V2.value) }
            }
        )

    private fun responseWithHeader(value: String?, code: Int = 200): Response<GenericOverall> {
        val headers = Headers.Builder().apply { value?.let { add("X-Chat-Last-Common-Read", it) } }.build()
        val raw = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://cloud.example/").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(code)
            .message("message")
            .headers(headers)
            .body("".toResponseBody("application/json".toMediaType()))
            .build()
        return if (code in 200..299) Response.success(null, raw) else Response.error(raw.body!!, raw)
    }

    companion object {
        private const val USER_ID = 7L
        private const val ROOM_TOKEN = "token"
        private const val MARKER = 2624
    }
}
