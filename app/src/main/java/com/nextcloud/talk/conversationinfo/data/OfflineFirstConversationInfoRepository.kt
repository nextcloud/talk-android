/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo.data

import android.os.Bundle
import com.nextcloud.talk.chat.data.ChatRepository
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.sync.Synchronizer
import com.nextcloud.talk.data.sync.changeListSync
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineFirstConversationInfoRepository @Inject constructor(
    private val dao: ConversationsDao,
    private val network: ChatRepository,
    private val currentUserProviderNew: CurrentUserProviderNew
) : ConversationInfoRepository {

    override fun getRoomInfo(accountId: Long, token: String): Flow<ConversationModel> {
        return dao.getConversationForUser(accountId, token).map(ConversationEntity::asModel)
    }

    private fun getConversationInfoFromServer(bundle: Bundle): List<ConversationModel> {
        val token = bundle.getString(BundleKeys.KEY_TOKEN)
        val user = currentUserProviderNew.currentUser.blockingGet()

        val singleItemList = network.getRoom(user, token!!)
            .firstElement()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .blockingGet()

        return listOf(singleItemList)
    }

    override suspend fun syncWith(bundle: Bundle, synchronizer: Synchronizer): Boolean =
        synchronizer.changeListSync(
            modelFetcher = {
                return@changeListSync getConversationInfoFromServer(bundle)
            },
            versionUpdater = {}, // Not needed for conversation info
            modelDeleter = {}, // Not needed for conversation info
            modelUpdater = { models ->
                dao.upsertConversations(
                    models.filterIsInstance<ConversationModel>().map(ConversationModel::asEntity)
                )
            }
        )
}
