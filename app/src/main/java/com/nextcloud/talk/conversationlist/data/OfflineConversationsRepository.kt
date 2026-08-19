/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data

import com.nextcloud.talk.conversationlist.data.network.OfflineFirstConversationsRepository.ConversationResult
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

interface OfflineConversationsRepository {

    /**
     * Live stream of the observed account's conversations, for use in the conversation list.
     * Backed by the local database: it re-emits whenever conversation rows change (room list
     * sync, background catch-up, optimistic updates), with unchanged lists deduplicated.
     */
    val roomListFlow: Flow<List<ConversationModel>>

    /**
     * Stream of a single conversation, for use in each conversations settings.
     */
    @Deprecated("use observeConversation")
    val conversationFlow: Flow<ConversationModel>

    /**
     * Selects the account observed by [roomListFlow] and synchronizes its conversations with
     * the server (when online). The synced changes surface through [roomListFlow], which
     * observes the database.
     */
    @Deprecated("use observeConversation")
    fun getRooms(user: User): Job

    /**
     * Called once onStart to emit a conversation to [conversationFlow]
     * to be handled asynchronously.
     */
    @Deprecated("use observeConversation")
    fun getRoom(user: User, roomToken: String): Job

    /**
     * Updates a single conversation in the local database. [roomListFlow] observes the database
     * and re-emits the updated list on its own.
     */
    suspend fun updateConversation(conversationModel: ConversationModel)

    @Deprecated("use observeConversation")
    suspend fun getLocallyStoredConversation(user: User, roomToken: String): ConversationModel?

    fun observeConversation(accountId: Long, roomToken: String): Flow<ConversationResult>
}
