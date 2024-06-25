/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data.network

import android.os.Bundle
import com.nextcloud.talk.conversationlist.data.ConversationsListRepository
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.data.sync.Synchronizer
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineFirstConversationsRepository @Inject constructor(
    private val dao: ConversationsDao,
    private val network: ConversationsNetworkDataSource,
    private val currentUserProviderNew: CurrentUserProviderNew
) : ConversationsListRepository {

    override fun getRooms(accountId: Long): Flow<List<ConversationModel>> {
        // TODO implement logic when to load from DB/network

        return dao.getConversationsForUser(accountId).map { conversationEntities ->
            conversationEntities.map { it.asModel() }
        }
    }

    private fun getConversationsFromServer(): List<Conversation> {
        val user = currentUserProviderNew.currentUser.blockingGet()

        val list = network.getRooms(user!!, user.baseUrl!!, false)
            .firstElement()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .blockingGet()

        return list ?: listOf()
    }

    override suspend fun syncWith(bundle: Bundle, synchronizer: Synchronizer): Boolean =
        // TODO implement synchronizer?
        false

        // synchronizer.changeListSync(
        //     modelFetcher = {
        //         // TODO return@changeListSync getConversationsFromServer()
                    // make Conversations a SyncableModel IF necessary
        //     },
        //     // not needed
        //     versionUpdater = {},
        //     // not needed
        //     modelDeleter = {},
        //     modelUpdater = {}
        // )
}
