/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data.network

import com.nextcloud.talk.chat.data.network.ChatMessageSyncer
import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.Conversation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Covers the guard that keeps a locally applied pin or unpin from being reverted by a room list
 * response that was computed before the change reached the server.
 */
class ConversationListUpdaterPinTest {

    private val chatDao: ChatMessagesDao = mock()
    private val chatBlocksDao: ChatBlocksDao = mock()
    private val conversationsDao: ConversationsDao = mock()

    private val updater = ConversationListUpdater(chatDao, chatBlocksDao, conversationsDao)

    private lateinit var stored: ConversationEntity

    @Test
    fun `an unpin is released as soon as the server reports another pinned message`() =
        runTest {
            // the message that stays pinned must not be suppressed forever just because the server
            // never reports "nothing pinned" after the unpin
            givenCachedConversation(pinnedId = UNPINNED_ID)
            updater.updateLocalPinnedMessage(target(), UNPINNED_ID, pinned = false)

            val guarded = preserve(serverPinnedId = OTHER_PINNED_ID)

            assertEquals(OTHER_PINNED_ID, guarded.lastPinnedId)
        }

    @Test
    fun `an unpin keeps the local state while the server still reports the unpinned message`() =
        runTest {
            givenCachedConversation(pinnedId = UNPINNED_ID)
            updater.updateLocalPinnedMessage(target(), UNPINNED_ID, pinned = false)

            val guarded = preserve(serverPinnedId = UNPINNED_ID)

            assertNull(guarded.lastPinnedId)
        }

    @Test
    fun `a pin keeps the local state until the server reports it`() =
        runTest {
            givenCachedConversation(pinnedId = null)
            updater.updateLocalPinnedMessage(target(), PINNED_ID, pinned = true)

            assertEquals(PINNED_ID, preserve(serverPinnedId = null).lastPinnedId)
            assertEquals(PINNED_ID, preserve(serverPinnedId = PINNED_ID).lastPinnedId)
            // once confirmed the guard is gone and the server stays the authority
            assertNull(preserve(serverPinnedId = null).lastPinnedId)
        }

    @Test
    fun `a completed pin stops guarding, so somebody else pinning another message still shows`() =
        runTest {
            givenCachedConversation(pinnedId = null)
            updater.updateLocalPinnedMessage(target(), PINNED_ID, pinned = true)

            updater.clearPendingPinnedMessage(stored.internalId)

            assertEquals(OTHER_PINNED_ID, preserve(serverPinnedId = OTHER_PINNED_ID).lastPinnedId)
        }

    private fun preserve(serverPinnedId: Long?): ConversationEntity =
        updater.preservePendingLocalState(
            previousConversations = mapOf(stored.internalId to stored),
            conversationsFromServer = listOf(stored.copy(lastPinnedId = serverPinnedId))
        ).single()

    private fun givenCachedConversation(pinnedId: Long?) {
        stored = Conversation(token = ROOM_TOKEN).asEntity(ACCOUNT_ID).copy(lastPinnedId = pinnedId)
        whenever(conversationsDao.getConversationForUser(eq(ACCOUNT_ID), eq(ROOM_TOKEN)))
            .thenAnswer { flowOf(stored) }
        whenever(conversationsDao.updateConversation(any())).thenAnswer {
            stored = it.getArgument(0)
            Unit
        }
    }

    private fun target(): ChatMessageSyncer.SyncTarget =
        ChatMessageSyncer.SyncTarget(
            user = User(id = ACCOUNT_ID, userId = "me", username = "me", baseUrl = "https://server.example.com"),
            roomToken = ROOM_TOKEN,
            threadId = null,
            credentials = "credentials",
            urlForChatting = "https://server.example.com/ocs/v2.php/apps/spreed/api/v1/chat/$ROOM_TOKEN"
        )

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val ROOM_TOKEN = "room1"
        private const val UNPINNED_ID = 42L
        private const val OTHER_PINNED_ID = 77L
        private const val PINNED_ID = 99L
    }
}
