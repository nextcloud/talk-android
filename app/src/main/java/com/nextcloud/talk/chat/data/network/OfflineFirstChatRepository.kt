/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.network

import android.os.Bundle
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.ChatRepository
import com.nextcloud.talk.chat.data.model.ChatMessageModel
import com.nextcloud.talk.data.changeListVersion.SyncableModel
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
import retrofit2.Response
import javax.inject.Inject

class OfflineFirstChatRepository @Inject constructor(
    private val chatDao: ChatMessagesDao,
    private val network: ChatRepository,
    private val datastore: AppPreferences
) : ChatMessageRepository, Synchronizer {

    override val messageFlow:
        Flow<
            Pair<
                Boolean,
                List<ChatMessageModel>
                >
            >
        get() = _messageFlow

    private val _messageFlow:
        MutableSharedFlow<
            Pair<
                Boolean,
                List<ChatMessageModel>
                >
            > = MutableSharedFlow()

    private var newXChatLastCommonRead = 0
    private var newMessageIds: List<Long> = listOf()

    override fun loadMoreMessages(
        messageId: Long,
        withConversationId: Long,
        withMessageLimit: Int,
        withNetworkParams: Bundle
    ): Unit =
        runBlocking {
            launch {
                val fieldMap = getFieldMap(
                    withConversationId,
                    lookIntoFuture = false,
                    setReadMarker = true
                )
                withNetworkParams.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)

                var attempts = 0
                do {
                    attempts++
                    val maxAttemptsAreNotReached = (attempts < 2)

                    val list = getMessagesBefore(
                        messageId,
                        withConversationId,
                        withMessageLimit
                    )

                    if (list.isNotEmpty()) {
                        val pair = Pair(false, list)
                        _messageFlow.emit(pair)
                        break
                    } else if (maxAttemptsAreNotReached) this@OfflineFirstChatRepository.sync(withNetworkParams)
                } while (maxAttemptsAreNotReached)
            }
        }

    override fun initMessagePolling(withConversationId: Long, withNetworkParams: Bundle): Unit =
        runBlocking {
            launch {
                var fieldMap = getFieldMap(withConversationId, lookIntoFuture = true, setReadMarker = true)
                while (true) {
                    // sync database with server ( This is a long blocking call b/c long polling is set )
                    withNetworkParams.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)
                    this@OfflineFirstChatRepository.sync(withNetworkParams)

                    // get new messages, if not empty -> emit to flow with APPEND
                    val list = getMessagesFrom(newMessageIds)
                    if (list.isNotEmpty()) {
                        val pair = Pair(true, list)
                        _messageFlow.emit(pair)
                    }
                    // update field map vars for next cycle
                    fieldMap = getFieldMap(withConversationId, lookIntoFuture = true, setReadMarker = true)
                }
            }
        }

    private fun getFieldMap(
        fromConversationId: Long,
        lookIntoFuture: Boolean,
        setReadMarker: Boolean
    ): HashMap<String, Int> {
        val fieldMap = HashMap<String, Int>()

        fieldMap["includeLastKnown"] = if (!lookIntoFuture) 1 else 0

        val lastKnown = datastore.getLastKnownId(fromConversationId, 0)
        fieldMap["lastKnownMessageId"] = lastKnown

        fieldMap["lastCommonReadId"] = newXChatLastCommonRead

        fieldMap["timeout"] = if (lookIntoFuture) 30 else 0
        fieldMap["limit"] = 100
        fieldMap["lookIntoFuture"] = if (lookIntoFuture) 1 else 0
        fieldMap["setReadMarker"] = if (setReadMarker) 1 else 0

        return fieldMap
    }

    private suspend fun getMessagesBefore(
        messageId: Long,
        roomId: Long,
        messageLimit: Int
    ): List<ChatMessageModel> =
        chatDao.getMessagesForConversationBefore(roomId, messageId, messageLimit).map {
            it.map(ChatMessageEntity::asModel)
        }.first()

    private suspend fun getMessagesFrom(messageIds: List<Long>): List<ChatMessageModel> =
        chatDao.getMessagesFromIds(messageIds).map {
            it.map(ChatMessageEntity::asModel)
        }.first()


    override fun getMessage(withId: Long): Flow<ChatMessageModel> {
        // =
        // chatDao.getChatMessageForConversation(withId).map(ChatMessageEntity::asModel)
        return flowOf()
    }

    private fun process(response: Response<*>, conversationId: Long) {
        when (response.code()) {
            HTTP_CODE_OK -> {
                newXChatLastCommonRead = response.headers()["X-Chat-Last-Common-Read"]?.let {
                    Integer.parseInt(it)
                } ?: 0

                val xChatLastGivenHeader: String? = response.headers()["X-Chat-Last-Given"]
                val lastKnownId = if (response.headers().size > 0 &&
                    xChatLastGivenHeader?.isNotEmpty() == true
                ) {
                    xChatLastGivenHeader.toInt()
                } else {
                    return
                }

                if (lastKnownId > 0) {
                    datastore.saveLastKnownId(conversationId, lastKnownId)
                }
            }

            HTTP_CODE_NOT_MODIFIED -> {
                // unused atm
            }

            HTTP_CODE_PRECONDITION_FAILED -> {
                // unused atm
            }

            else -> {}
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getMessagesFromServer(bundle: Bundle): List<ChatMessage> {
        val credentials = bundle.getString(BundleKeys.KEY_CREDENTIALS)
        val url = bundle.getString(BundleKeys.KEY_CHAT_URL)
        val fieldMap = bundle.getSerializable(BundleKeys.KEY_FIELD_MAP) as HashMap<String, Int>
        val withConversationId = bundle.getLong(BundleKeys.KEY_CONVERSATION_ID)

        // TODO this needs to be lifecycle aware
        val list = network.pullChatMessages(credentials!!, url!!, fieldMap)
            .firstElement()
            .subscribeOn(Schedulers.io())
            .map {
                process(it, withConversationId)
                (it.body() as ChatOverall).ocs!!.data
            }
            .observeOn(AndroidSchedulers.mainThread())
            .blockingGet()
        return list ?: listOf()
    }

    override suspend fun syncWith(bundle: Bundle, synchronizer: Synchronizer): Boolean =
        synchronizer.changeListSync(
            modelFetcher = {
                return@changeListSync getMessagesFromServer(bundle)
            },
            // not needed
            versionUpdater = {},
            // not needed
            modelDeleter = {},
            modelUpdater = { models ->
                newMessageIds = models.map(SyncableModel::changedId)
                chatDao.upsertChatMessages(
                    models.filterIsInstance<ChatMessage>().map { it.asEntity() }
                )
            }
        )

    companion object {
        private const val HTTP_CODE_OK: Int = 200
        private const val HTTP_CODE_NOT_MODIFIED = 304
        private const val HTTP_CODE_PRECONDITION_FAILED = 412
    }
}
