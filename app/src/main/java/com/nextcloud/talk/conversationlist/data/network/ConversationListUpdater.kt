/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist.data.network

import android.util.Log
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.data.network.ChatMessageSyncer
import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.model.ConversationEntity
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Keeps the conversation list entries fresh between room list syncs.
 *
 * Owns the two write paths into the conversations table that don't come from the server's room
 * list: reflecting background message catch-ups (preview, activity, derived unread count) and the
 * optimistic local read state — plus the pending read markers that keep provably stale server
 * responses from reverting that read state. The room list sync remains the authority and
 * self-healing safeguard for everything else.
 */
@Suppress("TooManyFunctions")
class ConversationListUpdater @Inject constructor(
    private val chatDao: ChatMessagesDao,
    private val chatBlocksDao: ChatBlocksDao,
    private val conversationsDao: ConversationsDao
) {

    /**
     * Read markers that were written locally but whose delivery the server has not confirmed yet.
     *
     * A room list sync request can be answered before a marker sent at the same time reaches the
     * server, so the sync response carries a provably stale read state — applying it would revert
     * the conversation entry to unread until the next sync. While a marker is pending, such stale
     * responses are kept out of the merge; the entry is only released once a sync confirms the
     * marker (server read state caught up) or sending it ultimately failed, so the server's
     * authority is restored either way and marking as unread from another device stays possible.
     */
    private val pendingReadMarkers = ConcurrentHashMap<String, Int>()

    /**
     * Favorite flags applied locally but not yet confirmed by a server response; guarded in the
     * merge like the pending read markers.
     */
    private val pendingFavorites = ConcurrentHashMap<String, Boolean>()

    /**
     * Rooms marked as unread from the conversation list whose server state doesn't reflect it yet.
     */
    private val pendingUnreadFlags = ConcurrentHashMap<String, Boolean>()

    /**
     * Tag assignments applied locally but not yet confirmed by a server response.
     */
    private val pendingTagIds = ConcurrentHashMap<String, List<String>>()

    /**
     * The locally written but not yet server-confirmed read marker of the conversation, or null.
     */
    fun pendingReadMarker(internalConversationId: String): Int? = pendingReadMarkers[internalConversationId]

    /**
     * Registers a read marker sent from the conversation list (mark as read), so the merge guards
     * it exactly like a marker sent from an open chat.
     */
    fun markPendingReadMarker(internalConversationId: String, lastReadMessage: Int) {
        pendingReadMarkers[internalConversationId] = lastReadMessage
    }

    fun markPendingFavorite(internalConversationId: String, favorite: Boolean) {
        pendingFavorites[internalConversationId] = favorite
    }

    fun clearPendingFavorite(internalConversationId: String, favorite: Boolean) {
        pendingFavorites.remove(internalConversationId, favorite)
    }

    fun markPendingUnread(internalConversationId: String) {
        pendingUnreadFlags[internalConversationId] = true
    }

    fun clearPendingUnread(internalConversationId: String) {
        pendingUnreadFlags.remove(internalConversationId)
    }

    fun markPendingTags(internalConversationId: String, tagIds: List<String>) {
        pendingTagIds[internalConversationId] = tagIds
    }

    fun clearPendingTags(internalConversationId: String, tagIds: List<String>) {
        pendingTagIds.remove(internalConversationId, tagIds)
    }

    /**
     * Releases a pending read marker: called when a room list sync confirmed it or when sending it
     * ultimately failed. Only removes [lastReadMessage] itself, so a newer marker written in the
     * meantime stays pending.
     */
    fun clearPendingReadMarker(internalConversationId: String, lastReadMessage: Int) {
        pendingReadMarkers.remove(internalConversationId, lastReadMessage)
    }

    /**
     * Optimistically writes the user's read state into the conversation entry, so the
     * conversation list reflects it immediately — before (and independent of) the read marker
     * reaching the server. The unread count is recounted from the cached messages above the new
     * marker. The server stays the authority: the next room list sync re-asserts its state, with
     * [pendingReadMarkers] bridging the window until the marker's delivery is confirmed.
     */
    suspend fun updateLocalReadState(target: ChatMessageSyncer.SyncTarget, lastReadMessage: Int) {
        val conversation =
            conversationsDao.getConversationForUser(target.accountId, target.roomToken).first() ?: return
        val unreadMessages = chatDao.countMessagesNewerThan(
            internalConversationId = target.internalConversationId,
            messageId = lastReadMessage.toLong(),
            excludedActorId = target.user.userId!!
        )
        pendingReadMarkers[target.internalConversationId] = lastReadMessage
        conversationsDao.updateReadState(conversation.internalId, lastReadMessage, unreadMessages)
        Log.d(TAG, "Local read state for room ${target.roomToken}: lastRead=$lastReadMessage, unread=$unreadMessages")
    }

    /**
     * Keeps provably stale local-action state out of the merge of server responses (the room
     * list sync and the single-room refresh): a response computed before a concurrently sent
     * change — read marker, mark as unread, favorite flag, tag assignment — reached the server
     * still reports the old state, and applying it would revert the conversation entry until the
     * next sync. While a change for the room is pending and the server hasn't reflected it yet,
     * the local state is kept; once the server confirms it (or moved past it, e.g. read further
     * or unfavorited on another device after confirmation) the pending change is released and
     * the server state applies unchanged, so the server stays the authority.
     */
    fun preservePendingLocalState(
        previousConversations: Map<String, ConversationEntity>,
        conversationsFromServer: List<ConversationEntity>
    ): List<ConversationEntity> =
        conversationsFromServer.map { serverItem ->
            val previous = previousConversations[serverItem.internalId]
                ?: return@map serverItem
            var guarded = guardPendingReadMarker(serverItem, previous)
            guarded = guardPendingUnread(guarded, previous)
            guarded = guardPendingFavorite(guarded, previous)
            guarded = guardPendingTags(guarded, previous)
            guarded
        }

    private fun guardPendingReadMarker(
        serverItem: ConversationEntity,
        previous: ConversationEntity
    ): ConversationEntity {
        val pendingMarker = pendingReadMarker(serverItem.internalId) ?: return serverItem

        return if (serverItem.lastReadMessage < pendingMarker) {
            Log.d(
                TAG,
                "Keeping local read state for room ${serverItem.token}: server lastRead=" +
                    "${serverItem.lastReadMessage} is behind the pending read marker $pendingMarker"
            )
            serverItem.copy(
                lastReadMessage = previous.lastReadMessage,
                unreadMessages = previous.unreadMessages
            )
        } else {
            clearPendingReadMarker(serverItem.internalId, pendingMarker)
            serverItem
        }
    }

    private fun guardPendingUnread(serverItem: ConversationEntity, previous: ConversationEntity): ConversationEntity {
        if (!pendingUnreadFlags.containsKey(serverItem.internalId)) {
            return serverItem
        }

        return if (serverItem.unreadMessages > 0) {
            clearPendingUnread(serverItem.internalId)
            serverItem
        } else {
            serverItem.copy(
                lastReadMessage = previous.lastReadMessage,
                unreadMessages = previous.unreadMessages
            )
        }
    }

    private fun guardPendingFavorite(serverItem: ConversationEntity, previous: ConversationEntity): ConversationEntity {
        val desired = pendingFavorites[serverItem.internalId] ?: return serverItem

        return if (serverItem.favorite == desired) {
            clearPendingFavorite(serverItem.internalId, desired)
            serverItem
        } else {
            serverItem.copy(favorite = previous.favorite)
        }
    }

    private fun guardPendingTags(serverItem: ConversationEntity, previous: ConversationEntity): ConversationEntity {
        val desired = pendingTagIds[serverItem.internalId] ?: return serverItem

        return if (serverItem.tagIds == desired) {
            clearPendingTags(serverItem.internalId, desired)
            serverItem
        } else {
            serverItem.copy(tagIds = previous.tagIds)
        }
    }

    /**
     * Reflects a background catch-up in the conversation list: the room's cached conversation
     * entry is updated with the newest persisted message, its activity timestamp and a locally
     * derived unread count, so the list is up to date the moment it is opened — before the room
     * list sync (which stays in place as the authority and self-healing safeguard) has answered.
     *
     * Concurrency: runs inside the per-room catch-up mutex and writes via a single guarded UPDATE
     * that only applies when the derived state is newer than the stored one, so a concurrently
     * finishing room list sync can neither be overwritten with older data nor interleave with a
     * read-modify-write. Thread catch-ups don't describe the room itself and are skipped.
     */
    suspend fun updateConversationFromCatchUp(
        target: ChatMessageSyncer.SyncTarget,
        outcome: ChatMessageSyncer.SyncOutcome
    ) {
        val newestMessage = outcome.newestPersistedMessage
        if (target.threadId != null || !outcome.persistedNewMessages || newestMessage == null) {
            return
        }
        val conversation =
            conversationsDao.getConversationForUser(target.accountId, target.roomToken).first() ?: return

        val unreadMessages = deriveUnreadMessagesCount(target, conversation.lastReadMessage)
        val updatedRows = conversationsDao.updateConversationFromCatchUp(
            internalId = conversation.internalId,
            lastMessageJson = LoganSquare.serialize(newestMessage),
            lastActivity = newestMessage.timestamp,
            unreadMessages = unreadMessages
        )
        Log.d(
            TAG,
            "Conversation list update for room ${target.roomToken} from catch-up " +
                "(lastActivity=${newestMessage.timestamp}, unread=$unreadMessages): " +
                if (updatedRows > 0) "applied" else "skipped, stored state is newer"
        )
    }

    /**
     * Derives the room's unread count from the cached messages, or [UNREAD_COUNT_UNKNOWN] when it
     * cannot be derived — the count is only trustworthy when the latest chat block reaches back to
     * the last read message. Own messages don't count: sending from another device advances the
     * server-side read marker, which the locally cached [lastReadMessage] may lag behind.
     */
    private suspend fun deriveUnreadMessagesCount(target: ChatMessageSyncer.SyncTarget, lastReadMessage: Int): Int {
        val latestBlock = if (lastReadMessage > 0) {
            chatBlocksDao.getLatestChatBlock(target.internalConversationId, null).first()
        } else {
            null
        }
        val blockReachesUnreadBoundary = latestBlock != null &&
            (latestBlock.oldestMessageId <= lastReadMessage || !latestBlock.hasHistory)

        return if (blockReachesUnreadBoundary) {
            chatDao.countMessagesNewerThan(
                internalConversationId = target.internalConversationId,
                messageId = lastReadMessage.toLong(),
                excludedActorId = target.user.userId!!
            )
        } else {
            UNREAD_COUNT_UNKNOWN
        }
    }

    companion object {
        private val TAG: String = ConversationListUpdater::class.java.simpleName

        /**
         * Marks that the unread count could not be derived locally; the guarded conversation
         * update keeps the stored count in that case.
         */
        private const val UNREAD_COUNT_UNKNOWN = -1

        /**
         * System messages that never become a conversation's last message on the server, so a
         * locally derived conversation preview must skip them as well.
         */
        val LAST_MESSAGE_HIDDEN_SYSTEM_TYPES = setOf(
            ChatMessage.SystemMessageType.REACTION,
            ChatMessage.SystemMessageType.REACTION_REVOKED,
            ChatMessage.SystemMessageType.REACTION_DELETED,
            ChatMessage.SystemMessageType.MESSAGE_DELETED,
            ChatMessage.SystemMessageType.MESSAGE_EDITED,
            ChatMessage.SystemMessageType.POLL_VOTED
        )
    }
}
