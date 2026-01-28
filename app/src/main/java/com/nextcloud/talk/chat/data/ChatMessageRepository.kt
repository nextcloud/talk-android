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
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.generic.GenericOverall
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface ChatMessageRepository : LifecycleAwareManager {

    /**
     * Stream of a list of messages to be handled using the associated boolean
     * false for past messages, true for future messages.
     */
    val messageFlow:
        Flow<
            Triple<
                Boolean,
                Boolean,
                List<ChatMessage>
                >
            >

    val updateMessageFlow: Flow<ChatMessage>

    val lastCommonReadFlow: Flow<Int>

    val lastReadMessageFlow: Flow<Int>

    /**
     * Used for informing the user of the underlying processing behind offline support, [String] is the key
     * which is handled in a switch statement in ChatActivity.
     */
    val generalUIFlow: Flow<String>

    val removeMessageFlow: Flow<ChatMessage>

    fun initData(currentUser: User, credentials: String, urlForChatting: String, roomToken: String, threadId: Long?)

    fun updateConversation(conversationModel: ConversationModel)

    fun initScopeAndLoadInitialMessages(withNetworkParams: Bundle)

    /**
     * Loads messages from local storage. If the messages are not found, then it
     * synchronizes the database with the server, before retrying exactly once. Only
     * emits to [messageFlow] if the message list is not empty.
     *
     * [withNetworkParams] credentials and url
     */
    fun loadMoreMessages(
        beforeMessageId: Long,
        roomToken: String,
        withMessageLimit: Int,
        withNetworkParams: Bundle
    ): Job

    /**
     * Long polls the server for any updates to the chat, if found, it synchronizes
     * the database with the server and emits the new messages to [messageFlow],
     * else it simply retries after timeout.
     */
    fun initMessagePolling(initialMessageId: Long): Job

    /**
     * Gets a individual message.
     */
    suspend fun getMessage(messageId: Long, bundle: Bundle): Flow<ChatMessage>

    suspend fun getParentMessageById(messageId: Long): Flow<ChatMessage>

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
        displayName: String,
        referenceId: String,
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
        replyTo: Int?,
        sendWithoutNotification: Boolean,
        threadTitle: String?,
        threadId: Long?
    ): Flow<Result<ChatMessage>>

    suspend fun deleteScheduledChatMessage(credentials: String, url: String): Flow<Result<GenericOverall>>

    suspend fun getScheduledChatMessages(credentials: String, url: String): Flow<Result<List<ChatMessage>>>
}
