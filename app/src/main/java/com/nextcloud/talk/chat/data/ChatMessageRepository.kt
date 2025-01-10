/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data

import android.os.Bundle
import com.nextcloud.talk.chat.data.io.LifecycleAwareManager
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

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

    fun setData(conversationModel: ConversationModel, credentials: String, urlForChatting: String)

    fun loadInitialMessages(withNetworkParams: Bundle): Job

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

    /**
     * Destroys unused resources.
     */
    fun handleChatOnBackPress()

    suspend fun sendChatMessage(
        credentials: String,
        url: String,
        message: CharSequence,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String
    ): Flow<Result<ChatMessage?>>

    suspend fun addTemporaryMessage(
        message: CharSequence,
        displayName: String,
        replyTo: Int,
        referenceId: String
    ): Flow<Result<ChatMessage?>>

    suspend fun editChatMessage(credentials: String, url: String, text: String): Flow<Result<ChatOverallSingleMessage>>
}
