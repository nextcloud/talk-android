/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data

import com.nextcloud.talk.models.domain.ConversationModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

interface OfflineConversationsRepository {

    /**
     * Stream of a list of rooms, for use in the conversation list.
     */
    val roomListFlow: Flow<List<ConversationModel>>

    /**
     * Loads rooms from local storage. If the rooms are not found, then it
     * synchronizes the database with the server, before retrying exactly once. Only
     * emits to [roomListFlow] if the rooms list is not empty.
     *
     */
    fun getRooms(): Job

    /**
     * Called once onStart to emit a conversation to [conversationFlow]
     * to be handled asynchronously.
     */
    fun getRoom(roomToken: String): Flow<ConversationModel?>
}
