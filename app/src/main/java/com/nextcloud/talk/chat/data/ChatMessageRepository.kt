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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

interface ChatMessageRepository : LifecycleAwareManager {

    /**
     * Stream of a list of messages to be handled using the associated boolean
     * false for past messages, true for future messages.
     */
    val messageFlow:
        Flow<
            Pair<
                Boolean,
                List<ChatMessage>
                >
            >

    val updateMessageFlow: Flow<ChatMessage>

    val lastCommonReadFlow: Flow<Int>

    fun setData(
        conversationModel: ConversationModel,
        credentials: String,
        urlForChatting: String
    )

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
     *
     * [withNetworkParams] credentials and url.
     */
    fun initMessagePolling(): Job

    /**
     * Gets a individual message.
     */
    suspend fun getMessage(messageId: Long, bundle: Bundle): Flow<ChatMessage>

    /**
     * Destroys unused resources.
     */
    fun handleChatOnBackPress()
}
