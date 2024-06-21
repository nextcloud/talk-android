/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.network

import android.os.Bundle
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.ChatMessageRepository.InsertionStrategy
import com.nextcloud.talk.chat.data.ChatRepository
import com.nextcloud.talk.chat.data.model.ChatMessageModel
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.sync.Synchronizer
import com.nextcloud.talk.data.sync.changeListSync
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class OfflineFirstChatRepository @Inject constructor(
    private val chatDao: ChatMessagesDao,
    private val network: ChatRepository,
    private val datastore: AppPreferences
) : ChatMessageRepository, Synchronizer {

    override val messageFlow:
        Flow<
            Pair<
                InsertionStrategy,
                List<ChatMessageModel>
                >
            >
        get() = _messageFlow

    private val _messageFlow:
        MutableSharedFlow<
            Pair<
                InsertionStrategy,
                List<ChatMessageModel>
                >
            > = MutableSharedFlow()

    override fun loadMoreMessages(
        beforeMessageId: Long,
        withConversationId: Long,
        withMessageLimit: Int,
        withNetworkParams: Bundle
    ): Unit =
        runBlocking {
            launch {
                val strategy = InsertionStrategy.PREPEND

                var attempts = 0
                do {
                    attempts++
                    val maxAttemptsAreNotReached = (attempts < 2)

                    val list = getMessages(
                        beforeMessageId,
                        withConversationId,
                        withMessageLimit
                    )

                    if (list.isNotEmpty()) {
                        val pair = Pair(strategy, list)
                        _messageFlow.emit(pair)
                        break
                    } else if (maxAttemptsAreNotReached) this@OfflineFirstChatRepository.sync(withNetworkParams)
                } while (maxAttemptsAreNotReached)
            }
        }

    override fun initMessagePolling(withConversationId: Long): Unit =
        runBlocking {
            launch {
                // init field map with vars
                while (true) {
                    // retrieve last known message Id for conversation id from datastore
                    // sync database with server ( This is a long blocking call b/c long polling is set )
                    // get messages after last known message id, if not empty -> emit to flow with APPEND TODO impl func
                    // update field map vars for next cycle
                }
            }
        }

    private suspend fun getMessages(
        beforeId: Long, // TODO needed for proper filtering
        roomId: Long,
        messageLimit: Int // TODO needed for proper filtering
    ): List<ChatMessageModel> =
        chatDao.getMessagesForConversationBefore(roomId, beforeId).map {
            it.map(ChatMessageEntity::asModel)
        }.first()

    override fun getMessage(withId: Long): Flow<ChatMessageModel> {
        // TODO figure this out tmrw
        // =
        // chatDao.getChatMessageForConversation(withId).map(ChatMessageEntity::asModel)
        return flowOf()
    }

    @Suppress("UNCHECKED_CAST")
    private fun getMessagesFromServer(bundle: Bundle): List<ChatMessage> {
        val credentials = bundle.getString(BundleKeys.KEY_CREDENTIALS)
        val url = bundle.getString(BundleKeys.KEY_CHAT_URL)
        val fieldMap = bundle.getSerializable(BundleKeys.KEY_FIELD_MAP) as HashMap<String, Int>

        // TODO this needs to be lifecycle aware
        val list = network.pullChatMessages(credentials!!, url!!, fieldMap)
            .firstElement()
            .subscribeOn(Schedulers.io())
            .map { (it.body() as ChatOverall).ocs!!.data }
            .observeOn(AndroidSchedulers.mainThread())
            .blockingGet()
        return list ?: listOf()
    }

    override suspend fun syncWith(bundle: Bundle, synchronizer: Synchronizer): Boolean =
        synchronizer.changeListSync(
            modelFetcher = {
                return@changeListSync getMessagesFromServer(bundle)
            },
            versionUpdater = { newLastReadId ->
                val conversationId = bundle.getLong(BundleKeys.KEY_CONVERSATION_ID)
                datastore.saveLastReadId(conversationId, newLastReadId)
            },
            modelDeleter = chatDao::deleteChatMessages,
            modelUpdater = { model ->
                chatDao.upsertChatMessages(
                    model.filterIsInstance<ChatMessage>().map { it.asEntity() }
                )
            }
        )
}
