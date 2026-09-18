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
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.jobs.ConversationActionWorker.ConversationAction
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the enqueued work is named decides which change supersedes which, and the action travels to
 * the worker as a string. Both are easy to break by renaming and hard to notice at runtime, because
 * a change that is dropped looks exactly like one the server refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ConversationActionWorkerTest {

    @Test
    fun `favoriting the same conversation twice replaces the older intent`() {
        assertEquals(
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.FAVORITE),
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.FAVORITE)
        )
    }

    @Test
    fun `archiving a conversation does not replace marking it unread`() {
        assertNotEquals(
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.ARCHIVE),
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.MARK_UNREAD)
        )
    }

    @Test
    fun `the same conversation token on two accounts is two changes`() {
        assertNotEquals(
            ConversationActionWorker.uniqueWorkName(1, "room1", ConversationAction.ARCHIVE),
            ConversationActionWorker.uniqueWorkName(2, "room1", ConversationAction.ARCHIVE)
        )
    }

    @Test
    fun `every action survives the trip through the work input`() {
        ConversationAction.entries.forEach { action ->
            assertEquals(action, ConversationActionWorker.actionOf(action.name))
        }
    }

    @Test
    fun `an action the worker does not know is dropped rather than guessed`() {
        assertNull(ConversationActionWorker.actionOf("SOMETHING_ELSE"))
        assertNull(ConversationActionWorker.actionOf(null))
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
            runBlocking { worker.send(USER_ID, "room1", ConversationAction.FAVORITE, enabled = true) }
        }

        // the guard belongs to the work that replaced this one, so being superseded must not release it
        verify(conversationListUpdater, never()).clearPendingFavorite(any(), any())
    }

    @Test
    fun `a request the server refuses is a failure rather than a cancellation`() {
        val conversationsRepository: ConversationsRepository = mock()
        val conversationListUpdater: ConversationListUpdater = mock()
        val worker = worker(conversationsRepository, conversationListUpdater)
        conversationsRepository.stub {
            onBlocking { addConversationToFavorites(any(), any()) } doSuspendableAnswer {
                throw IllegalStateException("refused")
            }
        }

        val result = runBlocking { worker.send(USER_ID, "room1", ConversationAction.FAVORITE, enabled = true) }

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    private fun worker(
        conversationsRepository: ConversationsRepository,
        conversationListUpdater: ConversationListUpdater
    ): ConversationActionWorker {
        val userManager: UserManager = mock()
        wheneverBlocking { userManager.getUserWithId(USER_ID) }.thenReturn(USER)
        return TestListenableWorkerBuilder<ConversationActionWorker>(ApplicationProvider.getApplicationContext())
            .build()
            .apply {
                this.userManager = userManager
                this.conversationsRepository = conversationsRepository
                this.conversationListUpdater = conversationListUpdater
            }
    }

    private companion object {
        const val USER_ID = 1L

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
