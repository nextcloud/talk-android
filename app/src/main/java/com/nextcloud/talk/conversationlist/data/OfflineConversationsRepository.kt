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
     * Emits when [getRooms] fails to sync with the server (e.g. a dropped/reset connection on a
     * slow network) while there are no locally cached conversations to fall back on for that
     * account, so the UI can tell the user why the list is empty instead of failing silently.
     * A failed sync while conversations are already cached does not emit here, since
     * [roomListFlow] already has data to show and the sync is a best-effort background refresh.
     */
    val syncErrorFlow: Flow<Throwable>

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
