/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.SendStatus
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("Detekt.TooManyFunctions")
interface ChatMessagesDao {

    /*
     * Do not filter with "isTemporary = 0" to make sure temporary messages are also shown
     */
    @Query(
        """
    SELECT *
    FROM ChatMessages
    WHERE internalConversationId = :internalConversationId
      AND (:threadId IS NULL OR threadId = :threadId)
      AND (id >= :oldestMessageId OR isTemporary = 1)
    ORDER BY timestamp ASC, id ASC
    """
    )
    fun getMessagesEqualOrNewerThan(
        internalConversationId: String,
        threadId: Long?,
        oldestMessageId: Long
    ): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND isTemporary = 0
        AND (:threadId IS NULL OR threadId = :threadId)
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun getMessagesForConversation(internalConversationId: String, threadId: Long?): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND isTemporary = 1
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun getTempMessagesForConversation(internalConversationId: String): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND isTemporary = 1 
        AND sendStatus != 'SENT_PENDING_ACK'
        AND (:threadId IS NULL OR threadId = :threadId)
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun getTempUnsentMessagesForConversation(
        internalConversationId: String,
        threadId: Long?
    ): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND referenceId = :referenceId
        AND isTemporary = 1
        AND (:threadId IS NULL OR threadId = :threadId)
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun getTempMessageForConversation(
        internalConversationId: String,
        referenceId: String,
        threadId: Long?
    ): Flow<ChatMessageEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatMessages(chatMessages: List<ChatMessageEntity>)

    /**
     * Stores [chatMessages], drops the temporary messages their server copies replace, and carries the
     * reply count of every thread in [chatMessages] over to its cached root message. The server sends the
     * thread's current count with each message of the thread. Stored thread data is kept, see
     * [keepingThreadDataOf].
     *
     * The server relays a new thread's root message before it creates the thread, so the root is stored without
     * thread data. Every thread message in [chatMessages] (e.g. the THREAD_CREATED system message) carries the
     * thread's title and reply count, so the stored root of its thread is marked with them.
     */
    @Transaction
    suspend fun upsertChatMessagesAndDeleteTemp(internalConversationId: String, chatMessages: List<ChatMessageEntity>) {
        val storedThreadMessages = getThreadMessageEntities(internalConversationId, chatMessages.map { it.id })
            .associateBy { it.id }
        upsertChatMessages(chatMessages.map { it.keepingThreadDataOf(storedThreadMessages[it.id]) })

        chatMessages
            .filter { it.isThread && it.threadId != null }
            .groupBy { it.threadId!! }
            .forEach { (threadId, threadMessages) ->
                val newest = threadMessages.maxBy { it.id }
                markAsThreadRoot(internalConversationId, threadId, newest.threadTitle, newest.threadReplies ?: 0)
            }

        updateThreadRepliesOfThreads(
            internalConversationId,
            chatMessages
                .filter { it.isThread && it.threadId != null && it.threadReplies != null }
                .groupBy { it.threadId!! }
                .mapValues { (_, messages) -> messages.maxOf { it.threadReplies!! } }
        )

        val referenceIds = chatMessages
            .mapNotNull { it.referenceId }
            .ifEmpty { return }

        deleteTempChatMessages(internalConversationId, referenceIds)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatMessage(chatMessage: ChatMessageEntity)

    /**
     * Stores [chatMessage] like [upsertChatMessage], but keeps the stored thread data, see [keepingThreadDataOf].
     */
    @Transaction
    suspend fun upsertChatMessageKeepingThreadData(chatMessage: ChatMessageEntity) {
        val storedThreadMessage = getThreadMessageEntities(chatMessage.internalConversationId, listOf(chatMessage.id))
            .firstOrNull()
        upsertChatMessage(chatMessage.keepingThreadDataOf(storedThreadMessage))
    }

    /**
     * Returns the stored messages of [messageIds] that belong to a thread. Only their thread data is needed to keep
     * it, see [keepingThreadDataOf].
     */
    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND id IN (:messageIds)
        AND isThread = 1
        """
    )
    suspend fun getThreadMessageEntities(
        internalConversationId: String,
        messageIds: List<Long>
    ): List<ChatMessageEntity>

    /**
     * Marks the stored message [threadId] as root of its thread. A root that is already marked is left alone, so
     * its stored thread data is never replaced by older data.
     */
    @Query(
        """
        UPDATE ChatMessages
        SET isThread = 1, threadId = id, threadTitle = :threadTitle, threadReplies = :threadReplies
        WHERE internalConversationId = :internalConversationId
        AND id = :threadId
        AND isThread = 0
        """
    )
    suspend fun markAsThreadRoot(
        internalConversationId: String,
        threadId: Long,
        threadTitle: String?,
        threadReplies: Int
    )

    /**
     * Raises the reply count of the cached root message of [threadId] to [threadReplies]. A thread's reply count
     * only grows on the server, so a lower count is a stale one, e.g. from a response that took longer than a
     * relayed reply, and is ignored.
     */
    @Query(
        """
        UPDATE ChatMessages
        SET threadReplies = MAX(COALESCE(threadReplies, 0), :threadReplies)
        WHERE internalConversationId = :internalConversationId
        AND id = :threadId
        """
    )
    suspend fun updateThreadReplies(internalConversationId: String, threadId: Long, threadReplies: Int)

    /**
     * Emits the highest reply count stored on the cached messages of [threadId], or null while none is cached.
     * Every message of a thread carries the thread's count, so this follows the thread even when its root
     * message is not cached.
     */
    @Query(
        """
        SELECT MAX(threadReplies)
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND threadId = :threadId
        AND isThread = 1
        """
    )
    fun observeThreadReplies(internalConversationId: String, threadId: Long): Flow<Int?>

    /**
     * Raises reply counts, keyed by thread id, on the cached root messages of those threads, all in one
     * transaction, see [updateThreadReplies].
     */
    @Transaction
    suspend fun updateThreadRepliesOfThreads(internalConversationId: String, threadReplies: Map<Long, Int>) {
        threadReplies.forEach { (threadId, replies) ->
            updateThreadReplies(internalConversationId, threadId, replies)
        }
    }

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM ChatMessages
            WHERE internalConversationId = :internalConversationId
            AND referenceId = :referenceId
            AND isTemporary = 0
        )
        """
    )
    suspend fun hasServerCopyOfReferenceId(internalConversationId: String, referenceId: String): Boolean

    /**
     * Stores a temporary message unless the server copy of it is already stored. The counterpart of
     * [upsertChatMessagesAndDeleteTemp], which drops the temporary message once the server copy arrives:
     * a temporary message stored after that would never be dropped again. Check and insert share one
     * transaction, so a sync cannot store the server copy in between.
     *
     * @return true if the message was stored, false if the server copy already exists
     */
    @Transaction
    suspend fun upsertTempChatMessageUnlessServerCopyExists(chatMessage: ChatMessageEntity): Boolean {
        val referenceId = chatMessage.referenceId
        if (referenceId != null && hasServerCopyOfReferenceId(chatMessage.internalConversationId, referenceId)) {
            return false
        }
        upsertChatMessage(chatMessage)
        return true
    }

    @Deprecated("use getChatMessageEntity")
    @Query(
        """
        SELECT * 
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId 
        AND id = :messageId
        """
    )
    fun getChatMessageForConversation(internalConversationId: String, messageId: Long): Flow<ChatMessageEntity>

    @Deprecated("use getChatMessageEntity")
    @Query(
        """
    SELECT *
    FROM ChatMessages
    WHERE internalConversationId = :internalConversationId
    AND id = :messageId
    """
    )
    suspend fun getChatMessageOnce(internalConversationId: String, messageId: Long): ChatMessageEntity?

    @Deprecated("use getChatMessageEntity")
    @Query(
        """
        SELECT * 
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId 
        AND id = :messageId
        """
    )
    fun getChatMessageForConversationNullable(internalConversationId: String, messageId: Long): Flow<ChatMessageEntity?>

    @Query(
        """
        SELECT * 
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId 
        AND id = :messageId
        LIMIT 1
        """
    )
    suspend fun getChatMessageEntity(internalConversationId: String, messageId: Long): ChatMessageEntity?

    @Query(
        """
        SELECT * 
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND id = :messageId
        LIMIT 1
        """
    )
    fun observeMessage(internalConversationId: String, messageId: Long): Flow<ChatMessageEntity?>

    @Query(
        value = """
            DELETE FROM ChatMessages
            WHERE internalConversationId = :internalConversationId
            AND referenceId in (:referenceIds)
            AND isTemporary = 1
        """
    )
    fun deleteTempChatMessages(internalConversationId: String, referenceIds: List<String>)

    /*
     * Used when the user cancels an upload - unlike deleteTempChatMessages, this must NOT delete
     * the placeholder once sendStatus has already moved past PENDING (i.e. the upload already
     * finished and was shared), since by then the message has actually been sent and deleting the
     * local placeholder would only hide it until the next sync brings it back.
     */
    @Query(
        value = """
            DELETE FROM ChatMessages
            WHERE internalConversationId = :internalConversationId
            AND referenceId = :referenceId
            AND isTemporary = 1
            AND sendStatus = 'PENDING'
        """
    )
    fun deleteTempChatMessageIfPending(internalConversationId: String, referenceId: String): Int

    /*
     * Marks the text messages of a conversation that are still PENDING and were created before
     * [createdBefore] (epoch seconds, like ChatMessageEntity.timestamp) as FAILED. Upload placeholders
     * are left alone: they carry a "file" entry in messageParameters, which is stored as "{}" (not NULL)
     * for a row without parameters. Only call this when no send work is left for the conversation.
     */
    @Query(
        value = """
            UPDATE ChatMessages
            SET sendStatus = 'FAILED'
            WHERE internalConversationId = :internalConversationId
            AND isTemporary = 1
            AND sendStatus = 'PENDING'
            AND timestamp < :createdBefore
            AND (messageParameters IS NULL OR messageParameters NOT LIKE '%"file"%')
        """
    )
    suspend fun failPendingTempTextMessages(internalConversationId: String, createdBefore: Long): Int

    /*
     * Writes only the send status of a temporary message, so a concurrent edit of its text is kept.
     */
    @Query(
        value = """
            UPDATE ChatMessages
            SET sendStatus = :sendStatus
            WHERE internalConversationId = :internalConversationId
            AND referenceId = :referenceId
            AND isTemporary = 1
        """
    )
    fun updateTempMessageSendStatus(internalConversationId: String, referenceId: String, sendStatus: SendStatus): Int

    /*
     * Writes only the text of a message, so a concurrent change of its send status is kept.
     */
    @Query(
        value = """
            UPDATE ChatMessages
            SET message = :message
            WHERE internalConversationId = :internalConversationId
            AND id = :messageId
        """
    )
    fun updateMessageText(internalConversationId: String, messageId: Long, message: String): Int

    @Update
    fun updateChatMessage(message: ChatMessageEntity)

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE id in (:messageIds)
        ORDER BY timestamp ASC, id ASC
        """
    )
    fun getMessagesFromIds(messageIds: List<Long>): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT * 
        FROM ChatMessages 
        WHERE internalConversationId = :internalConversationId AND id >= :messageId 
        AND isTemporary = 0
        AND (:threadId IS NULL OR threadId = :threadId)
        ORDER BY timestamp ASC, id ASC
        """
    )
    fun getMessagesForConversationSince(
        internalConversationId: String,
        messageId: Long,
        threadId: Long?
    ): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND isTemporary = 0
        AND (:threadId IS NULL OR threadId = :threadId)
        AND id BETWEEN :oldestMessageId AND :newestMessageId
        ORDER BY timestamp ASC, id ASC
        """
    )
    fun getMessagesInRange(
        internalConversationId: String,
        threadId: Long?,
        oldestMessageId: Long,
        newestMessageId: Long
    ): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId 
        AND isTemporary = 0
        AND id < :messageId
        AND (:threadId IS NULL OR threadId = :threadId)
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
        """
    )
    fun getMessagesForConversationBefore(
        internalConversationId: String,
        messageId: Long,
        limit: Int,
        threadId: Long?
    ): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId 
        AND isTemporary = 0
        AND id <= :messageId
        AND (:threadId IS NULL OR threadId = :threadId)
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
        """
    )
    fun getMessagesForConversationBeforeAndEqual(
        internalConversationId: String,
        messageId: Long,
        limit: Int,
        threadId: Long?
    ): Flow<List<ChatMessageEntity>>

    /**
     * Counts the cached messages above [messageId] the way the server counts unread messages:
     * spreed's ChatManager.getUnreadCount only counts the verbs 'comment' and 'object_shared',
     * which the chat API surfaces as every messageType except 'system', 'comment_deleted',
     * 'command' and 'reaction'. The user's own messages ([excludedActorId]) are additionally
     * excluded: the server advances the author's read marker on every post, so own messages are
     * never above the marker server-side — the exclusion compensates a lagging cached marker.
     */
    @Query(
        """
        SELECT COUNT(*)
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND isTemporary = 0
        AND id > :messageId
        AND messageType NOT IN ('system', 'comment_deleted', 'command', 'reaction')
        AND NOT (actorType = 'users' AND actorId = :excludedActorId)
        """
    )
    suspend fun countMessagesNewerThan(internalConversationId: String, messageId: Long, excludedActorId: String): Int

    /**
     * Counts the cached messages above [messageId] like [countMessagesNewerThan], but including the
     * user's own messages. Excluding them only compensates a read marker that lags behind the server's;
     * a marker the user moved back on purpose really does sit below their own messages, and the server
     * counts those as unread as well.
     */
    @Query(
        """
        SELECT COUNT(*)
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND isTemporary = 0
        AND id > :messageId
        AND messageType NOT IN ('system', 'comment_deleted', 'command', 'reaction')
        """
    )
    suspend fun countMessagesNewerThanIncludingOwn(internalConversationId: String, messageId: Long): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND isTemporary = 0
        AND (:threadId IS NULL OR threadId = :threadId)
        AND id BETWEEN :newestMessageId AND :oldestMessageId
        """
    )
    fun getCountBetweenMessageIds(
        internalConversationId: String,
        oldestMessageId: Long,
        newestMessageId: Long,
        threadId: Long?
    ): Int

    @Query(
        """
        DELETE FROM chatmessages
        WHERE internalId LIKE :pattern
        """
    )
    fun clearAllMessagesForUser(pattern: String)

    @Query(
        """
        DELETE FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND expirationTimestamp > 0
        AND expirationTimestamp <= :currentTimeSecs
        """
    )
    suspend fun deleteExpiredMessages(internalConversationId: String, currentTimeSecs: Long): Int

    @Query(
        """
        SELECT MIN(id)
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND isTemporary = 0
        AND (:threadId IS NULL OR threadId = :threadId)
        AND id BETWEEN :oldestMessageId AND :newestMessageId
        """
    )
    suspend fun getOldestMessageIdInRange(
        internalConversationId: String,
        threadId: Long?,
        oldestMessageId: Long,
        newestMessageId: Long
    ): Long?

    @Query(
        """
        SELECT MAX(id)
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND isTemporary = 0
        AND (:threadId IS NULL OR threadId = :threadId)
        AND id BETWEEN :oldestMessageId AND :newestMessageId
        """
    )
    suspend fun getNewestMessageIdInRange(
        internalConversationId: String,
        threadId: Long?,
        oldestMessageId: Long,
        newestMessageId: Long
    ): Long?

    @Query(
        """
        DELETE FROM chatmessages
        WHERE internalConversationId = :internalConversationId 
        AND id < :messageId
        """
    )
    fun deleteMessagesOlderThan(internalConversationId: String, messageId: Long)
}

/**
 * Returns this message with the thread data of [stored], the stored copy of the same message, where this copy
 * has none. The server omits the thread data on some copies of a thread message, e.g. on the parent of an edit
 * or delete sent over the chat relay, and a message never stops belonging to a thread.
 */
internal fun ChatMessageEntity.keepingThreadDataOf(stored: ChatMessageEntity?): ChatMessageEntity =
    when {
        stored == null || !stored.isThread -> this
        !isThread -> copy(
            isThread = true,
            threadId = stored.threadId,
            threadTitle = stored.threadTitle,
            threadReplies = stored.threadReplies
        )
        threadTitle.isNullOrEmpty() -> copy(threadTitle = stored.threadTitle)
        else -> this
    }
