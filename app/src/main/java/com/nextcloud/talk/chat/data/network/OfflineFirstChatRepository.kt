/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.network

import android.os.Bundle
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.model.ChatMessageJson
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.sync.Synchronizer
import com.nextcloud.talk.data.sync.changeListSync
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import retrofit2.Response
import javax.inject.Inject

class OfflineFirstChatRepository @Inject constructor(
    private val chatDao: ChatMessagesDao,
    private val network: ChatNetworkDataSource,
    private val datastore: AppPreferences,
    private val monitor: NetworkMonitor
) : ChatMessageRepository, Synchronizer {

    override val messageFlow:
        Flow<
            Pair<
                Boolean,
                List<ChatMessage>
                >
            >
        get() = _messageFlow

    private val _messageFlow:
        MutableSharedFlow<
            Pair<
                Boolean,
                List<ChatMessage>
                >
            > = MutableSharedFlow()

    private var newXChatLastCommonRead: Int? = null
    private var newMessageIds: List<Long> = listOf()
    private var itIsPaused = false
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun loadMoreMessages(
        messageId: Long,
        withConversationId: Long,
        withMessageLimit: Int,
        withNetworkParams: Bundle
    ): Job =
        scope.launch {
            val fieldMap = getFieldMap(
                withConversationId,
                lookIntoFuture = false,
                setReadMarker = true
            )
            withNetworkParams.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)
            withNetworkParams.putLong(BundleKeys.KEY_CONVERSATION_ID, withConversationId)

            var attempts = 0
            do {
                attempts++
                val maxAttemptsAreNotReached = (attempts < 2)

                val lastKnown = datastore.getLastKnownId(withConversationId, 0)
                val id = if (messageId > 0) messageId else (lastKnown.toLong() + 1) // so it includes lastKnown

                val list = getMessagesBefore(
                    id,
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

    override fun initMessagePolling(withConversationId: Long, withNetworkParams: Bundle): Job =
        scope.launch {
            monitor.isOnline.onEach { online ->
                var fieldMap = getFieldMap(withConversationId, lookIntoFuture = true, setReadMarker = true)
                while (!itIsPaused) {
                    if (!online) Thread.sleep(500)

                    // sync database with server ( This is a long blocking call b/c long polling is set )
                    withNetworkParams.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)
                    withNetworkParams.putLong(BundleKeys.KEY_CONVERSATION_ID, withConversationId)
                    this@OfflineFirstChatRepository.sync(withNetworkParams)

                    // get new messages, if not empty -> emit to flow with APPEND
                    var list = getMessagesFrom(newMessageIds)
                    newMessageIds = listOf() // Clear it after use to prevent duplicates

                    // Process read status if not null
                    val lastKnown = datastore.getLastKnownId(withConversationId, 0)
                    list = list.map { chatMessage ->
                        chatMessage.readStatus = if (chatMessage.jsonMessageId <= lastKnown) {
                            ReadStatus.READ
                        } else {
                            ReadStatus.SENT
                        }

                        return@map chatMessage
                    }

                    if (list.isNotEmpty()) {
                        val pair = Pair(true, list)
                        _messageFlow.emit(pair)
                    }
                    // update field map vars for next cycle
                    fieldMap = getFieldMap(withConversationId, lookIntoFuture = true, setReadMarker = true)
                }
            }
                .flowOn(Dispatchers.IO).collect()
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

        newXChatLastCommonRead?.let {
            fieldMap["lastCommonReadId"] = if (it > 0) it else lastKnown
        }

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
    ): List<ChatMessage> =
        chatDao.getMessagesForConversationBefore(roomId, messageId, messageLimit).map {
            it.map(ChatMessageEntity::asModel)
        }.first()

    private suspend fun getMessagesFrom(messageIds: List<Long>): List<ChatMessage> =
        chatDao.getMessagesFromIds(messageIds).map {
            it.map(ChatMessageEntity::asModel)
        }.first()

    override fun getMessage(withId: Long): Flow<ChatMessageJson> {
        // =
        // chatDao.getChatMessageForConversation(withId).map(ChatMessageEntity::asModel)
        return flowOf()
    }

    private fun process(response: Response<*>, conversationId: Long) {
        when (response.code()) {
            HTTP_CODE_OK -> {
                newXChatLastCommonRead = response.headers()["X-Chat-Last-Common-Read"]?.let {
                    Integer.parseInt(it)
                }

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
    private fun getMessagesFromServer(bundle: Bundle): List<ChatMessageJson> {
        val credentials = bundle.getString(BundleKeys.KEY_CREDENTIALS)
        val url = bundle.getString(BundleKeys.KEY_CHAT_URL)
        val fieldMap = bundle.getSerializable(BundleKeys.KEY_FIELD_MAP) as HashMap<String, Int>
        val withConversationId = bundle.getLong(BundleKeys.KEY_CONVERSATION_ID)

        val list = network.pullChatMessages(credentials!!, url!!, fieldMap)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .filter { it.body() != null }
            .map {
                process(it, withConversationId)
                return@map (it.body() as ChatOverall).ocs!!.data
            }
            .blockingSingle()

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
                val conversationId = bundle.getLong(BundleKeys.KEY_CONVERSATION_ID)
                newMessageIds = models.map { it.id }
                val list = models.filterIsInstance<ChatMessageJson>().map {
                    it.asEntity().apply {
                        internalConversationId = conversationId
                    }
                }
                chatDao.upsertChatMessages(list)
            }
        )

    override fun handleOnPause() {
        itIsPaused = true
    }

    override fun handleOnResume() {
        itIsPaused = false
    }

    override fun handleOnStop() {
        // unused atm
    }

    companion object {
        private const val HTTP_CODE_OK: Int = 200
        private const val HTTP_CODE_NOT_MODIFIED = 304
        private const val HTTP_CODE_PRECONDITION_FAILED = 412
    }
}
