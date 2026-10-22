/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.jobs

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.jobs.ConversationActionWorker.Change
import com.nextcloud.talk.jobs.ConversationActionWorker.ConversationAction
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.users.UserManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * What the enqueued work is named decides which change supersedes which, and the action travels to
 * the worker as strings in its work input, which outlives an app update. Both are easy to break by
 * renaming and hard to notice at runtime, because a change that is dropped looks exactly like one the
 * server refused.
 */
@Suppress("TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ConversationActionWorkerTest {

    @Test
    fun `favoriting and unfavoriting the same conversation replace each other`() {
        assertEquals(
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.Favorite(true)),
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.Favorite(false))
        )
    }

    @Test
    fun `archiving a conversation does not replace marking it unread`() {
        assertNotEquals(
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.Archive(true)),
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.MarkUnread)
        )
    }

    @Test
    fun `the same conversation token on two accounts is two changes`() {
        assertNotEquals(
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.Archive(true)),
            ConversationActionWorker.uniqueWorkName(2, "room1", ConversationAction.Archive(true))
        )
    }

    @Test
    fun `marking read and unread go through the room's read marker slot`() {
        val readMarkerSlot = ReadMarkerSyncWorker.uniqueWorkName(1, "room1")
        listOf(
            ConversationAction.MarkRead(LAST_READ_MESSAGE),
            ConversationAction.MarkRead(null),
            ConversationAction.MarkUnread
        ).forEach { action ->
            assertEquals(readMarkerSlot, ConversationActionWorker.uniqueWorkName(1, "room1", action))
        }
    }

    @Test
    fun `work enqueued before an update is still replaced by a newer intent`() {
        assertEquals(
            "conversation-action-FAVORITE-1@room1",
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.Favorite(true))
        )
        assertEquals(
            "conversation-action-ARCHIVE-1@room1",
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.Archive(false))
        )
    }

    @Test
    fun `every action survives the trip through the work input`() {
        listOf(
            ConversationAction.MarkRead(LAST_READ_MESSAGE),
            ConversationAction.MarkRead(null),
            ConversationAction.MarkUnread,
            ConversationAction.Favorite(true),
            ConversationAction.Favorite(false),
            ConversationAction.Archive(true),
            ConversationAction.Archive(false)
        ).forEach { action ->
            val change = Change(USER_ID, "room1", action)
            assertEquals(change, ConversationActionWorker.changeOf(ConversationActionWorker.inputDataOf(change)))
        }
    }

    @Test
    fun `work enqueued before an update still runs`() {
        // written the way the previous version wrote it, which always set KEY_ENABLED
        fun previous(action: String, enabled: Boolean, lastReadMessage: Int? = null) =
            Data.Builder()
                .putLong("KEY_INTERNAL_USER_ID", USER_ID)
                .putString("KEY_ROOM_TOKEN", "room1")
                .putString("KEY_ACTION", action)
                .putBoolean("KEY_ENABLED", enabled)
                .apply { lastReadMessage?.let { putInt("KEY_LAST_READ_MESSAGE", it) } }
                .build()

        assertEquals(
            Change(USER_ID, "room1", ConversationAction.MarkRead(LAST_READ_MESSAGE)),
            ConversationActionWorker.changeOf(previous("MARK_READ", enabled = true, LAST_READ_MESSAGE))
        )
        assertEquals(
            Change(USER_ID, "room1", ConversationAction.MarkRead(null)),
            ConversationActionWorker.changeOf(previous("MARK_READ", enabled = true))
        )
        assertEquals(
            Change(USER_ID, "room1", ConversationAction.MarkUnread),
            ConversationActionWorker.changeOf(previous("MARK_UNREAD", enabled = true))
        )
        assertEquals(
            Change(USER_ID, "room1", ConversationAction.Favorite(false)),
            ConversationActionWorker.changeOf(previous("FAVORITE", enabled = false))
        )
        assertEquals(
            Change(USER_ID, "room1", ConversationAction.Archive(true)),
            ConversationActionWorker.changeOf(previous("ARCHIVE", enabled = true))
        )
    }

    @Test
    fun `an action the worker does not know is dropped rather than guessed`() {
        val unknown = workDataOf(
            "KEY_INTERNAL_USER_ID" to USER_ID,
            "KEY_ROOM_TOKEN" to "room1",
            "KEY_ACTION" to "SOMETHING_ELSE"
        )
        val missing = workDataOf("KEY_INTERNAL_USER_ID" to USER_ID, "KEY_ROOM_TOKEN" to "room1")

        assertNull(ConversationActionWorker.changeOf(unknown))
        assertNull(ConversationActionWorker.changeOf(missing))
    }

    @Test
    fun `work that is replaced by a newer intent is not reported as a failure`() {
        val conversationsRepository: ConversationsRepository = mock()
        val conversationListUpdater: ConversationListUpdater = mock()
        val worker = worker(conversationsRepository, conversationListUpdater)
        conversationsRepository.stub {
            onBlocking { addConversationToFavorites(any(), any()) } doSuspendableAnswer {
                throw CancellationException("replaced by a newer favorite")
            }
        }

        assertThrows(CancellationException::class.java) {
            runBlocking { worker.send(Change(USER_ID, "room1", ConversationAction.Favorite(true))) }
        }

        // the guard belongs to the work that replaced this one, so being superseded must not release it
        verify(conversationListUpdater, never()).clearPendingFavorite(any(), any())
    }

    @Test
    fun `a connection problem is retried while attempts are left`() {
        val conversationsRepository: ConversationsRepository = mock()
        val conversationListUpdater: ConversationListUpdater = mock()
        val worker = worker(conversationsRepository, conversationListUpdater)
        conversationsRepository.stub {
            onBlocking { addConversationToFavorites(any(), any()) } doSuspendableAnswer {
                throw IOException("no connection")
            }
        }

        val result = runBlocking { worker.send(Change(USER_ID, "room1", ConversationAction.Favorite(true))) }

        assertEquals(ListenableWorker.Result.retry(), result)
        verify(conversationListUpdater, never()).clearPendingFavorite(any(), any())
    }

    @Test
    fun `a refusal the server will repeat is given up on instead of retried`() {
        val conversationsRepository: ConversationsRepository = mock()
        val conversationListUpdater: ConversationListUpdater = mock()
        val worker = worker(conversationsRepository, conversationListUpdater)
        conversationsRepository.stub {
            onBlocking { addConversationToFavorites(any(), any()) } doSuspendableAnswer { throw forbidden() }
        }

        val result = runBlocking { worker.send(Change(USER_ID, "room1", ConversationAction.Favorite(true))) }

        assertEquals(ListenableWorker.Result.failure(), result)
        // the guard goes at once, so the next sync shows what the server actually holds
        verify(conversationListUpdater).clearPendingFavorite(INTERNAL_ID, true)
    }

    @Test
    fun `a server error is retried, as it may be gone by the next attempt`() {
        val conversationsRepository: ConversationsRepository = mock()
        val conversationListUpdater: ConversationListUpdater = mock()
        val worker = worker(conversationsRepository, conversationListUpdater)
        conversationsRepository.stub {
            onBlocking { addConversationToFavorites(any(), any()) } doSuspendableAnswer {
                throw httpError(HTTP_INTERNAL_SERVER_ERROR)
            }
        }

        val result = runBlocking { worker.send(Change(USER_ID, "room1", ConversationAction.Favorite(true))) }

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `a read marker that was sent stays guarded until a sync shows it`() {
        val conversationsRepository: ConversationsRepository = mock()
        val conversationListUpdater: ConversationListUpdater = mock()
        val worker = worker(conversationsRepository, conversationListUpdater)
        conversationsRepository.stub {
            onBlocking { markConversationAsRead(any(), any(), eq(LAST_READ_MESSAGE)) } doReturn GenericOverall()
        }

        val result = runBlocking {
            worker.send(Change(USER_ID, "room1", ConversationAction.MarkRead(LAST_READ_MESSAGE)))
        }

        assertEquals(ListenableWorker.Result.success(), result)
        verify(conversationListUpdater, never()).clearPendingReadMarker(any(), any())
    }

    @Test
    fun `a read marker the worker gives up on is released`() {
        val conversationsRepository: ConversationsRepository = mock()
        val conversationListUpdater: ConversationListUpdater = mock()
        val worker = worker(conversationsRepository, conversationListUpdater, runAttemptCount = LAST_ATTEMPT)
        conversationsRepository.stub {
            onBlocking { markConversationAsRead(any(), any(), anyOrNull()) } doSuspendableAnswer {
                throw IllegalStateException("refused")
            }
        }

        val result = runBlocking {
            worker.send(Change(USER_ID, "room1", ConversationAction.MarkRead(LAST_READ_MESSAGE)))
        }

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(conversationListUpdater).clearPendingReadMarker("$USER_ID@room1", LAST_READ_MESSAGE)
    }

    @Test
    fun `a change for a user that is gone is given up at once rather than retried`() {
        val conversationsRepository: ConversationsRepository = mock()
        val conversationListUpdater: ConversationListUpdater = mock()
        val worker = worker(conversationsRepository, conversationListUpdater)

        val change = Change(REMOVED_USER_ID, "room1", ConversationAction.Favorite(true))

        val result = runBlocking { worker.send(change) }

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(conversationListUpdater).clearPendingFavorite("$REMOVED_USER_ID@room1", true)
        verifyBlocking(conversationsRepository, never()) { addConversationToFavorites(any(), any()) }
    }

    @Test
    fun `marking read on a server with only the chat api does not ask for the conversation api`() {
        val chatOnly = USER.copy(
            capabilities = CapabilitiesDto().apply {
                spreedCapability = SpreedCapabilityDto().apply { features = listOf("chat-v2") }
            }
        )
        val conversationsRepository: ConversationsRepository = mock()
        val worker = worker(conversationsRepository, mock(), user = chatOnly)

        val result = runBlocking {
            worker.send(Change(USER_ID, "room1", ConversationAction.MarkRead(LAST_READ_MESSAGE)))
        }

        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(conversationsRepository) { markConversationAsRead(any(), any(), eq(LAST_READ_MESSAGE)) }
    }

    private fun forbidden(): HttpException = httpError(HTTP_FORBIDDEN)

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType())))

    private fun worker(
        conversationsRepository: ConversationsRepository,
        conversationListUpdater: ConversationListUpdater,
        runAttemptCount: Int = 0,
        user: User = USER
    ): ConversationActionWorker {
        val userManager: UserManager = mock()
        wheneverBlocking { userManager.getUserWithId(USER_ID) }.thenReturn(user)
        return TestListenableWorkerBuilder<ConversationActionWorker>(ApplicationProvider.getApplicationContext())
            .setRunAttemptCount(runAttemptCount)
            .build()
            .apply {
                this.userManager = userManager
                this.conversationsRepository = conversationsRepository
                this.conversationListUpdater = conversationListUpdater
            }
    }

    private companion object {
        const val USER_ID = 1L
        const val REMOVED_USER_ID = 2L
        const val LAST_READ_MESSAGE = 42
        const val INTERNAL_ID = "1@room1"
        const val HTTP_FORBIDDEN = 403
        const val HTTP_INTERNAL_SERVER_ERROR = 500

        // MAX_RUN_ATTEMPTS is 3, counted from 0
        const val LAST_ATTEMPT = 2

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
