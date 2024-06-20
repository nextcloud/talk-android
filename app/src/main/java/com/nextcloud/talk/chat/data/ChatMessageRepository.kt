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
                List<ChatMessageModel>>>

    /**
     * Loads messages from local storage. If the messages are not found, then it
     * synchronizes the database with the server, before retrying exactly once. Only
     * emits to [messageFlow] if the message list is not empty.
     *
     * [withNetworkParams] credentials, url, and field map.
     */
    fun loadMoreMessages(
        beforeMessageId: Long,
        withConversationId: Long,
        withMessageLimit: Int,
        withNetworkParams: Bundle
    )

    /**
     * TODO should be lifecycle and network aware
     */
    fun initMessagePolling(
        withConversationId: Long,
    )

    /**
     * Gets a individual message.
     */
    fun getMessage(withId: Long): Flow<ChatMessageModel>
}
