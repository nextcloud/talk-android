/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.jobs

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.users.UserManager
import io.reactivex.Observable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Marking read or unread in the conversation list goes through the same work slot as this worker, so
 * the list replaces the chat's read marker. Releasing a guard on the way out of work that was replaced
 * would hand back the guard its replacement has just set, and the next room list sync would then undo
 * what the user did last.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ReadMarkerSyncWorkerTest {

    private val chatNetworkDataSource: ChatNetworkDataSource = mock()
    private val conversationListUpdater: ConversationListUpdater = mock()

    @Test
    fun `a read marker the list replaced does not release the guard of what replaced it`() {
        val worker = worker()
        whenever(chatNetworkDataSource.setChatReadMarker(any(), any(), any()))
            .thenThrow(CancellationException("replaced by marking unread in the list"))

        assertThrows(CancellationException::class.java) {
            runBlocking { worker.sendReadMarker(USER_ID, ROOM_TOKEN, LAST_READ_MESSAGE) }
        }

        verify(conversationListUpdater, never()).clearPendingReadMarker(any(), any())
        verify(conversationListUpdater, never()).clearPendingUnread(any())
    }

    @Test
    fun `a read marker the server refuses is retried while attempts are left`() {
        val worker = worker()
        whenever(chatNetworkDataSource.setChatReadMarker(any(), any(), any()))
            .thenThrow(IllegalStateException("refused"))

        val result = runBlocking { worker.sendReadMarker(USER_ID, ROOM_TOKEN, LAST_READ_MESSAGE) }

        assertEquals(ListenableWorker.Result.retry(), result)
        verify(conversationListUpdater, never()).clearPendingReadMarker(any(), any())
    }

    @Test
    fun `a read marker that was sent keeps its guard for the next sync to clear`() {
        val worker = worker()
        whenever(chatNetworkDataSource.setChatReadMarker(any(), any(), any()))
            .thenReturn(Observable.just(GenericOverall()))

        val result = runBlocking { worker.sendReadMarker(USER_ID, ROOM_TOKEN, LAST_READ_MESSAGE) }

        assertEquals(ListenableWorker.Result.success(), result)
        verify(conversationListUpdater, never()).clearPendingReadMarker(any(), any())
    }

    @Test
    fun `a read marker for a user that is gone releases both read state guards`() {
        val worker = worker(user = null)

        val result = runBlocking { worker.sendReadMarker(USER_ID, ROOM_TOKEN, LAST_READ_MESSAGE) }

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(conversationListUpdater).clearPendingReadMarker(eq(INTERNAL_ID), eq(LAST_READ_MESSAGE))
        verify(conversationListUpdater).clearPendingUnread(eq(INTERNAL_ID))
    }

    private fun worker(user: User? = USER): ReadMarkerSyncWorker {
        val userManager: UserManager = mock()
        wheneverBlocking { userManager.getUserWithId(USER_ID) }.thenReturn(user)
        return TestListenableWorkerBuilder<ReadMarkerSyncWorker>(ApplicationProvider.getApplicationContext())
            .build()
            .apply {
                this.userManager = userManager
                this.chatNetworkDataSource = this@ReadMarkerSyncWorkerTest.chatNetworkDataSource
                this.conversationListUpdater = this@ReadMarkerSyncWorkerTest.conversationListUpdater
            }
    }

    private companion object {
        const val USER_ID = 1L
        const val ROOM_TOKEN = "room1"
        const val INTERNAL_ID = "1@room1"
        const val LAST_READ_MESSAGE = 42

        val USER = User(
            id = USER_ID,
            userId = "me",
            username = "me",
            token = "app-password",
            baseUrl = "https://server.example.com",
            capabilities = CapabilitiesDto().apply {
                spreedCapability = SpreedCapabilityDto().apply {
                    features = listOf("conversation-v4", "chat-v2")
                }
            }
        )
    }
}
