/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data

import com.nextcloud.talk.chat.data.model.ChatMessageModel
import com.nextcloud.talk.data.sync.Syncable
import kotlinx.coroutines.flow.Flow

interface ChatMessageRepository : Syncable {

    /**
     * Gets available messages as a stream
     */
    fun getMessages(conversationId: Long): Flow<List<ChatMessageModel>>

    /**
     * Gets a individual message
     */
    fun getMessage(conversationId: Long, messageId: Long): Flow<ChatMessageModel>
}
