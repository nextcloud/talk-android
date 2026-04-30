/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data

import android.os.Bundle
import com.nextcloud.talk.chat.data.io.LifecycleAwareManager
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.generic.GenericOverall
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface ChatMessageRepository : LifecycleAwareManager {

    enum class LoadMoreDirection {
        OLDER,
        NEWER
    }

    data class MessagesRange(val oldestMessageId: Long, val newestMessageId: Long)

    /**
     * Stream of a list of messages to be handled using the associated boolean
     * false for past messages, true for future messages.
     */
    val messageFlow: Flow<Triple<Boolean, Boolean, List<ChatMessage>>>

    val updateMessageFlow: Flow<ChatMessage>

    val lastCommonReadFlow: Flow<Int>

    val lastReadMessageFlow: Flow<Int>

    val roomRefreshFlow: Flow<Unit>

    val incomingMessageFlow: Flow<Unit>

    val isLoadingFlow: Flow<Boolean>

    // /**
    //  * Used for informing the user of the underlying processing behind offline support, [String] is the key
    //  * which is handled in a switch statement in ChatActivity.
    //  */
    // val generalUIFlow: Flow<String>

    // val removeMessageFlow: Flow<ChatMessage>

    fun initData(currentUser: User, credentials: String, urlForChatting: String, roomToken: String, threadId: Long?)

    fun updateConversation(conversationModel: ConversationModel)

    suspend fun loadInitialMessages(withNetworkParams: Bundle, isChatRelaySupported: Boolean)

    suspend fun startMessagePolling(hasHighPerformanceBackend: Boolean)

    /**
     * Performs a one-time non-blocking fetch of messages newer than the latest known message.
     * Use this to immediately refresh chat after local actions (e.g. file share) that create
     * server-side messages which would otherwise only appear after the next insurance-request cycle.
     *
     * @return `true` if at least one new message was received, `false` when the server returned
     *         304 Not Modified or an empty message list.
     */
    suspend fun fetchNewMessages(): Boolean

    /**
     * Loads messages from local storage. If the messages are not found, then it
     * synchronizes the database with the server, before retrying exactly once. Only
     * emits to [messageFlow] if the message list is not empty.
     *
     * [withNetworkParams] credentials and url
     */
    suspend fun loadMoreMessages(
        anchorMessageId: Long,
        direction: LoadMoreDirection,
        roomToken: String,
        withMessageLimit: Int,
        withNetworkParams: Bundle
    ): MessagesRange?

    /**
     * Gets a individual message.
     */
    fun getMessage(messageId: Long, bundle: Bundle): Flow<ChatMessage>

    @Deprecated("getMessage(messageId: Long, bundle: Bundle)")
    suspend fun getParentMessageById(messageId: Long): Flow<ChatMessage>

    suspend fun fetchMissingParents(conversationId: String, parentIds: List<Long>)

    fun observeParentMessages(parentIds: List<Long>): Flow<List<ChatMessage>>

    suspend fun getNumberOfThreadReplies(threadId: Long): Int

    @Suppress("LongParameterList")
    suspend fun sendChatMessage(
        credentials: String,
        url: String,
        message: String,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String,
        threadTitle: String?
    ): Flow<Result<ChatMessage?>>

    @Suppress("LongParameterList")
    suspend fun resendChatMessage(
        credentials: String,
        url: String,
        message: String,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String
    ): Flow<Result<ChatMessage?>>

    suspend fun addTemporaryMessage(
        message: CharSequence,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String
    ): Flow<Result<ChatMessage?>>

    @Suppress("LongParameterList")
    suspend fun addUploadPlaceholderMessage(
        localFileUri: String,
        caption: String,
        mimeType: String?,
        fileSize: Long,
        referenceId: String
    ): Flow<Result<ChatMessage?>>

    suspend fun deleteTempMessageByReferenceId(referenceId: String)

    suspend fun editChatMessage(credentials: String, url: String, text: String): Flow<Result<ChatOverallSingleMessage>>

    suspend fun editTempChatMessage(message: ChatMessage, editedMessageText: String): Flow<Boolean>

    suspend fun sendUnsentChatMessages(credentials: String, url: String)

    suspend fun deleteTempMessage(chatMessage: ChatMessage)

    suspend fun pinMessage(credentials: String, url: String, pinUntil: Int): Flow<ChatMessage?>

    suspend fun unPinMessage(credentials: String, url: String): Flow<ChatMessage?>

    suspend fun hidePinnedMessage(credentials: String, url: String): Flow<Boolean>

    @Suppress("LongParameterList")
    suspend fun sendScheduledChatMessage(
        credentials: String,
        url: String,
        message: String,
        replyTo: Int?,
        sendWithoutNotification: Boolean,
        threadTitle: String?,
        threadId: Long?,
        sendAt: Int?
    ): Flow<Result<ChatOverallSingleMessage>>

    @Suppress("LongParameterList")
    suspend fun updateScheduledChatMessage(
        credentials: String,
        url: String,
        message: String,
        sendAt: Int?,
        sendWithoutNotification: Boolean
    ): Flow<Result<ChatMessage>>

    suspend fun deleteScheduledChatMessage(credentials: String, url: String): Flow<Result<GenericOverall>>

    suspend fun getScheduledChatMessages(credentials: String, url: String): Flow<Result<List<ChatMessage>>>

    suspend fun onSignalingChatMessageReceived(chatMessages: List<ChatMessageJson>)

    fun observeLatestMessages(internalConversationId: String): Flow<List<ChatMessageEntity>>

    fun observeMessagesForAnchor(internalConversationId: String, anchorMessageId: Long): Flow<List<ChatMessageEntity>>

    suspend fun loadMessageContext(messageId: Long, limit: Int, threadId: Long? = null): Result<MessagesRange>
}
