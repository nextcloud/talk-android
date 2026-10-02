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
     * Live stream of the conversations of the account with the internal id [accountId], for use in
     * the conversation list. Backed by the local database: it re-emits whenever conversation rows
     * change (room list sync, background catch-up, optimistic updates), with unchanged lists
     * deduplicated.
     */
    fun roomListFlow(accountId: Long): Flow<List<ConversationModel>>

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
     * Synchronizes the conversations of [user] with the server (when online). The synced changes
     * surface through [roomListFlow], which observes the database.
     *
     * The sync asks the server only for the conversations changed since the last one where it
     * can. [forceFullSync] makes it fetch the whole list instead.
     */
    @Deprecated("use observeConversation")
    fun getRooms(user: User, forceFullSync: Boolean = false): Job

    /**
     * Synchronizes [user]'s conversations with the server, suspending until that sync and the
     * message catch-up it triggers have finished, and returns whether the sync succeeded.
     *
     * Unlike [getRooms] this neither returns early nor changes which account [roomListFlow]
     * observes.
     */
    suspend fun syncRooms(user: User, forceFullSync: Boolean = false): Boolean

    /**
     * Whether a sync for [user] right now would either ask only for the conversations that changed,
     * or be the full refresh that falls due every five minutes.
     *
     * False means the next sync would fetch the whole conversation list without the cadence calling
     * for it.
     */
    suspend fun isPeriodicSyncDue(user: User): Boolean

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

    /**
     * Makes the next conversation list sync of the account with the internal id [accountId] fetch
     * the whole list.
     *
     * A conversation this device left or deleted is absent from the server's answer rather than
     * marked as gone, which only a full response can be read as a removal. Without this the row
     * survives locally until the next full sync falls due.
     */
    fun requireFullSync(accountId: Long)

    fun observeConversation(accountId: Long, roomToken: String): Flow<ConversationResult>
}
