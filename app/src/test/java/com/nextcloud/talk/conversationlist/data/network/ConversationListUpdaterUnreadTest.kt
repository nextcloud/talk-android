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
import com.nextcloud.talk.models.json.conversations.ConversationDto
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking

/**
 * Covers the local read state written when a message is marked as unread from the chat, and the
 * guard that has to keep it until the server reports an unread conversation itself. The read marker
 * moves backwards here, which the server keeps contradicting until it has processed it.
 */
class ConversationListUpdaterUnreadTest {

    private val chatDao: ChatMessagesDao = mock()
    private val chatBlocksDao: ChatBlocksDao = mock()
    private val conversationsDao: ConversationsDao = mock()

    private val updater = ConversationListUpdater(chatDao, chatBlocksDao, conversationsDao)

    private lateinit var stored: ConversationEntity

    @Before
    fun setUp() {
        stored = ConversationDto(token = ROOM_TOKEN)
            .asEntity(ACCOUNT_ID)
            .copy(lastReadMessage = READ_UP_TO_NEWEST, unreadMessages = 0)

        whenever(conversationsDao.getConversationForUser(eq(ACCOUNT_ID), eq(ROOM_TOKEN)))
            .thenAnswer { flowOf(stored) }
        wheneverBlocking { conversationsDao.updateReadState(any(), any(), any()) }.thenAnswer {
            stored = stored.copy(lastReadMessage = it.getArgument(1), unreadMessages = it.getArgument(2))
            Unit
        }
    }

    @Test
    fun `marking a message as unread moves the marker back and leaves the conversation unread`() =
        runTest {
            givenCachedMessagesNewerThanMarker(3)

            updater.updateLocalUnreadState(target(), MARKER)

            assertEquals(MARKER, stored.lastReadMessage)
            assertEquals(3, stored.unreadMessages)
        }

    @Test
    fun `own messages count towards the badge, as the marker now sits below them`() =
        runTest {
            givenCachedMessagesNewerThanMarker(4)

            updater.updateLocalUnreadState(target(), MARKER)

            // the count excluding own messages is the one that must not be used here
            verifyBlocking(chatDao) {
                countMessagesNewerThanIncludingOwn(eq(INTERNAL_CONVERSATION_ID), eq(MARKER.toLong()))
            }
            verifyBlocking(chatDao, never()) { countMessagesNewerThan(any(), any(), any()) }
            assertEquals(4, stored.unreadMessages)
        }

    @Test
    fun `a marker with nothing cached above it still shows the conversation as unread`() =
        runTest {
            givenCachedMessagesNewerThanMarker(0)

            updater.updateLocalUnreadState(target(), MARKER)

            assertEquals(1, stored.unreadMessages)
        }

    @Test
    fun `a room list response that still reports the old marker does not mark the chat read again`() =
        runTest {
            givenCachedMessagesNewerThanMarker(3)
            updater.updateLocalUnreadState(target(), MARKER)

            val guarded = preserve(serverLastRead = READ_UP_TO_NEWEST, serverUnread = 0)

            assertEquals(MARKER, guarded.lastReadMessage)
            assertEquals(3, guarded.unreadMessages)
        }

    @Test
    fun `the server stays the authority once it reports the conversation as unread`() =
        runTest {
            givenCachedMessagesNewerThanMarker(3)
            updater.updateLocalUnreadState(target(), MARKER)

            assertEquals(MARKER, preserve(serverLastRead = MARKER, serverUnread = 3).lastReadMessage)
            // and with the guard released, a later response that marks it read again applies
            val readAgain = preserve(serverLastRead = READ_UP_TO_NEWEST, serverUnread = 0)
            assertEquals(READ_UP_TO_NEWEST, readAgain.lastReadMessage)
            assertEquals(0, readAgain.unreadMessages)
        }

    @Test
    fun `marking read releases a pending unread, so the server's read state applies`() {
        stored = stored.copy(lastReadMessage = MARKER, unreadMessages = 3)
        updater.markPendingUnread(INTERNAL_CONVERSATION_ID)

        updater.switchToPendingMarkAsRead(INTERNAL_CONVERSATION_ID, READ_UP_TO_NEWEST)

        assertEquals(READ_UP_TO_NEWEST, updater.pendingReadMarker(INTERNAL_CONVERSATION_ID))
        val read = preserve(serverLastRead = READ_UP_TO_NEWEST, serverUnread = 0)
        assertEquals(READ_UP_TO_NEWEST, read.lastReadMessage)
        assertEquals(0, read.unreadMessages)
    }

    @Test
    fun `marking read without a marker, as in a federated conversation, still releases a pending unread`() {
        stored = stored.copy(lastReadMessage = MARKER, unreadMessages = 3)
        updater.markPendingUnread(INTERNAL_CONVERSATION_ID)

        updater.switchToPendingMarkAsRead(INTERNAL_CONVERSATION_ID, lastReadMessage = null)

        assertNull(updater.pendingReadMarker(INTERNAL_CONVERSATION_ID))
        assertEquals(0, preserve(serverLastRead = READ_UP_TO_NEWEST, serverUnread = 0).unreadMessages)
    }

    @Test
    fun `marking unread drops a pending read marker and guards the unread state instead`() {
        stored = stored.copy(lastReadMessage = MARKER, unreadMessages = 3)
        updater.markPendingReadMarker(INTERNAL_CONVERSATION_ID, READ_UP_TO_NEWEST)

        updater.switchToPendingMarkAsUnread(INTERNAL_CONVERSATION_ID)

        assertNull(updater.pendingReadMarker(INTERNAL_CONVERSATION_ID))
        // a response that still reports the conversation read does not overwrite the local unread state
        val unread = preserve(serverLastRead = READ_UP_TO_NEWEST, serverUnread = 0)
        assertEquals(MARKER, unread.lastReadMessage)
        assertEquals(3, unread.unreadMessages)
    }

    @Test
    fun `reading in the chat releases a mark as unread from the list`() =
        runTest {
            wheneverBlocking { chatDao.countMessagesNewerThan(any(), any(), any()) }.thenReturn(0)
            updater.markPendingUnread(INTERNAL_CONVERSATION_ID)

            updater.updateLocalReadState(target(), MARKER)

            // read further elsewhere: with the unread guard left behind, the local marker would stay
            val readElsewhere = preserve(serverLastRead = READ_UP_TO_NEWEST, serverUnread = 0)
            assertEquals(READ_UP_TO_NEWEST, readElsewhere.lastReadMessage)
        }

    @Test
    fun `marking a message unread in the chat drops a read marker from the list`() =
        runTest {
            givenCachedMessagesNewerThanMarker(3)
            updater.markPendingReadMarker(INTERNAL_CONVERSATION_ID, READ_UP_TO_NEWEST)

            updater.updateLocalUnreadState(target(), MARKER)

            assertNull(updater.pendingReadMarker(INTERNAL_CONVERSATION_ID))
            // a new message arrived: with the read marker left behind, the unread count would stay at 3
            assertEquals(4, preserve(serverLastRead = MARKER, serverUnread = 4).unreadMessages)
        }

    private fun preserve(serverLastRead: Int, serverUnread: Int): ConversationEntity =
        updater.preservePendingLocalState(
            previousConversations = mapOf(stored.internalId to stored),
            conversationsFromServer = listOf(
                stored.copy(lastReadMessage = serverLastRead, unreadMessages = serverUnread)
            )
        ).single()

    private fun givenCachedMessagesNewerThanMarker(count: Int) {
        wheneverBlocking {
            chatDao.countMessagesNewerThanIncludingOwn(eq(INTERNAL_CONVERSATION_ID), eq(MARKER.toLong()))
        }.thenReturn(count)
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
        private const val INTERNAL_CONVERSATION_ID = "$ACCOUNT_ID@$ROOM_TOKEN"
        private const val MARKER = 40
        private const val READ_UP_TO_NEWEST = 42
    }
}
