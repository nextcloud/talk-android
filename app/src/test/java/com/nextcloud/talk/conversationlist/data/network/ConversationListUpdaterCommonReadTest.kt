/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data.network

import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.models.json.conversations.ConversationDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking

/**
 * The last message read by all participants comes with the answer to a read marker. A room list
 * response whose request started before that answer must not lower it again; a later one is
 * current and applies as the server sent it.
 */
class ConversationListUpdaterCommonReadTest {

    private val conversationsDao: ConversationsDao = mock()
    private val updater = ConversationListUpdater(mock<ChatMessagesDao>(), mock<ChatBlocksDao>(), conversationsDao)

    private val stored: ConversationEntity = ConversationDto(token = ROOM_TOKEN).asEntity(ACCOUNT_ID)

    @Test
    fun `last common read is written to the conversation`() =
        runTest {
            updater.updateLastCommonRead(stored.internalId, NEWER)

            verifyBlocking(conversationsDao) { updateLastCommonRead(stored.internalId, NEWER) }
        }

    @Test
    fun `a room list response requested before the read marker answer does not lower it`() =
        runTest {
            val requestedAt = System.nanoTime()
            updater.updateLastCommonRead(stored.internalId, NEWER)

            assertEquals(NEWER, preserve(serverLastCommonRead = OLDER, requestedAt).lastCommonReadMessage)
            // the guard stays for every response of that age, it is not used up by the first one
            assertEquals(NEWER, preserve(serverLastCommonRead = OLDER, requestedAt).lastCommonReadMessage)
        }

    @Test
    fun `a room list response requested before the read marker answer still brings a higher value`() =
        runTest {
            val requestedAt = System.nanoTime()
            updater.updateLastCommonRead(stored.internalId, NEWER)

            assertEquals(NEWEST, preserve(serverLastCommonRead = NEWEST, requestedAt).lastCommonReadMessage)
        }

    @Test
    fun `a room list response requested after the read marker answer applies unchanged and ends the guard`() =
        runTest {
            val requestedBefore = System.nanoTime()
            updater.updateLastCommonRead(stored.internalId, NEWER)
            val requestedAfter = System.nanoTime()

            // someone marked a message as unread, so the server really reports less now
            assertEquals(OLDER, preserve(serverLastCommonRead = OLDER, requestedAfter).lastCommonReadMessage)
            // the guard is gone: not even an older request is held back any more
            assertEquals(OLDER, preserve(serverLastCommonRead = OLDER, requestedBefore).lastCommonReadMessage)
        }

    @Test
    fun `a room without a stored common read takes the server value`() {
        assertEquals(OLDER, preserve(serverLastCommonRead = OLDER, System.nanoTime()).lastCommonReadMessage)
    }

    private fun preserve(serverLastCommonRead: Int, requestedAtNanos: Long): ConversationEntity =
        updater.preservePendingLocalState(
            previousConversations = mapOf(stored.internalId to stored),
            conversationsFromServer = listOf(stored.copy(lastCommonReadMessage = serverLastCommonRead)),
            requestedAtNanos = requestedAtNanos
        ).single()

    companion object {
        private const val ACCOUNT_ID = 7L
        private const val ROOM_TOKEN = "token"
        private const val OLDER = 2620
        private const val NEWER = 2624
        private const val NEWEST = 2630
    }
}
