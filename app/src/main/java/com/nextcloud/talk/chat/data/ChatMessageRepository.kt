/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data

import android.os.Bundle
import com.nextcloud.talk.chat.data.model.ChatMessageModel
import com.nextcloud.talk.data.sync.Syncable
import kotlinx.coroutines.flow.Flow

interface ChatMessageRepository : Syncable {

    enum class InsertionStrategy {
        APPEND,
        PREPEND
    }

    /**
     * Stream of a list of messages to be handled using the associated [InsertionStrategy].
     */
    val messageFlow:
        Flow<
            Pair<
                InsertionStrategy,
                List<ChatMessageModel>
                >
            >

    /**
     * Loads messages from local storage. If the messages are not found, then it
     * synchronizes the database with the server, before retrying exactly once. Only
     * emits to [messageFlow] if the message list is not empty.
     *
     * [withNetworkParams] credentials and url
     */
    fun loadMoreMessages(
        beforeMessageId: Long,
        withConversationId: Long,
        withMessageLimit: Int,
        withNetworkParams: Bundle
    )

    /**
     * TODO should be lifecycle (ends onStop) and network aware (starts on connection gained)
     * Long polls the server for any updates to the chat, if found, it synchronizes
     * the database with the server and emits the new messages to [messageFlow],
     * else it simply retries after timeout.
     *
     * [withNetworkParams] credentials and url.
     */
    fun initMessagePolling(withConversationId: Long, withNetworkParams: Bundle)

    /**
     * Gets a individual message.
     */
    fun getMessage(withId: Long): Flow<ChatMessageModel>
}
