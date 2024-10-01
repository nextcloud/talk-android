/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.network

import android.os.Bundle
import android.util.Log
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.data.database.model.ChatBlockEntity
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class OfflineFirstChatRepository @Inject constructor(
    private val chatDao: ChatMessagesDao,
    private val chatBlocksDao: ChatBlocksDao,
    private val network: ChatNetworkDataSource,
    private val datastore: AppPreferences,
    private val monitor: NetworkMonitor,
    private val userProvider: CurrentUserProviderNew
) : ChatMessageRepository {

    val currentUser: User = userProvider.currentUser.blockingGet()

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

    override val updateMessageFlow:
        Flow<ChatMessage>
        get() = _updateMessageFlow

    private val _updateMessageFlow:
        MutableSharedFlow<ChatMessage> = MutableSharedFlow()

    override val lastCommonReadFlow:
        Flow<Int>
        get() = _lastCommonReadFlow

    private val _lastCommonReadFlow:
        MutableSharedFlow<Int> = MutableSharedFlow()

    override val lastReadMessageFlow:
        Flow<Int>
        get() = _lastReadMessageFlow

    private val _lastReadMessageFlow:
        MutableSharedFlow<Int> = MutableSharedFlow()

    private var newXChatLastCommonRead: Int? = null
    private var itIsPaused = false
    private val scope = CoroutineScope(Dispatchers.IO)

    lateinit var internalConversationId: String
    private lateinit var conversationModel: ConversationModel
    private lateinit var credentials: String
    private lateinit var urlForChatting: String

    override fun setData(conversationModel: ConversationModel, credentials: String, urlForChatting: String) {
        this.conversationModel = conversationModel
        this.credentials = credentials
        this.urlForChatting = urlForChatting
        internalConversationId = conversationModel.internalId
    }

    override fun loadInitialMessages(withNetworkParams: Bundle): Job =
        scope.launch {
            Log.d(TAG, "---- loadInitialMessages ------------")
            Log.d(TAG, "conversationModel.internalId: " + conversationModel.internalId)

            newXChatLastCommonRead = conversationModel.lastCommonReadMessage

            Log.d(TAG, "conversationModel.lastReadMessage:" + conversationModel.lastReadMessage)

            var newestMessageId = chatDao.getNewestMessageId(internalConversationId)
            Log.d(TAG, "newestMessageId: $newestMessageId")
            if (newestMessageId.toInt() == 0) {
                Log.d(TAG, "newestMessageId from db was 0. Must only happen when chat is loaded for the first time")
            }

            if (conversationModel.lastReadMessage.toLong() != newestMessageId) {
                Log.d(TAG, "An online request is made because chat is not up to date")

                // set up field map to load the newest messages
                val fieldMap = getFieldMap(
                    lookIntoFuture = false,
                    includeLastKnown = true,
                    setReadMarker = true,
                    lastKnown = null
                )
                withNetworkParams.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)
                withNetworkParams.putString(BundleKeys.KEY_ROOM_TOKEN, conversationModel.token)

                Log.d(TAG, "Starting online request for initial loading")
                val chatMessageEntities = sync(withNetworkParams)
                if (chatMessageEntities == null) {
                    Log.e(TAG, "initial loading of messages failed")
                }

                newestMessageId = chatDao.getNewestMessageId(internalConversationId)
                Log.d(TAG, "newestMessageId after sync: $newestMessageId")
            } else {
                Log.d(TAG, "Initial online request is skipped because offline messages are up to date")
            }

            val chatBlock = getBlockOfMessage(newestMessageId.toInt())

            val amountBetween = chatDao.getCountBetweenMessageIds(
                internalConversationId,
                newestMessageId,
                chatBlock!!.oldestMessageId
            )

            Log.d(TAG, "amount of messages between newestMessageId and oldest message of same ChatBlock:$amountBetween")
            val limit = if (amountBetween > DEFAULT_MESSAGES_LIMIT) {
                DEFAULT_MESSAGES_LIMIT
            } else {
                amountBetween
            }
            Log.d(TAG, "limit of messages to load for UI (max 100 to ensure performance is okay):$limit")

            showMessagesBeforeAndEqual(
                internalConversationId,
                newestMessageId,
                limit
            )

            // delay is a dirty workaround to make sure messages are added to adapter on initial load before dealing
            // with them (otherwise there is a race condition).
            delay(DELAY_TO_ENSURE_MESSAGES_ARE_ADDED)

            updateUiForLastCommonRead()
            updateUiForLastReadMessage(newestMessageId)

            initMessagePolling()
        }

    private suspend fun updateUiForLastReadMessage(newestMessageId: Long) {
        val scrollToLastRead = conversationModel.lastReadMessage.toLong() < newestMessageId
        if (scrollToLastRead) {
            _lastReadMessageFlow.emit(conversationModel.lastReadMessage)
        }
    }

    private fun updateUiForLastCommonRead() {
        scope.launch {
            newXChatLastCommonRead?.let {
                _lastCommonReadFlow.emit(it)
            }
        }
    }

    override fun loadMoreMessages(
        beforeMessageId: Long,
        roomToken: String,
        withMessageLimit: Int,
        withNetworkParams: Bundle
    ): Job =
        scope.launch {
            Log.d(TAG, "---- loadMoreMessages for $beforeMessageId ------------")

            val fieldMap = getFieldMap(
                lookIntoFuture = false,
                includeLastKnown = false,
                setReadMarker = true,
                lastKnown = beforeMessageId.toInt()
            )
            withNetworkParams.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)

            val loadFromServer = hasToLoadPreviousMessagesFromServer(beforeMessageId)

            if (loadFromServer) {
                Log.d(TAG, "Starting online request for loadMoreMessages")
                sync(withNetworkParams)
            }

            showMessagesBefore(internalConversationId, beforeMessageId, DEFAULT_MESSAGES_LIMIT)
            updateUiForLastCommonRead()
        }

    override fun initMessagePolling(): Job =
        scope.launch {
            Log.d(TAG, "---- initMessagePolling ------------")

            val initialMessageId = chatDao.getNewestMessageId(internalConversationId).toInt()
            Log.d(TAG, "newestMessage: $initialMessageId")

            var fieldMap = getFieldMap(
                lookIntoFuture = true,
                includeLastKnown = false,
                setReadMarker = true,
                lastKnown = initialMessageId
            )

            val networkParams = Bundle()

            while (true) {
                if (!monitor.isOnline.first() || itIsPaused) {
                    Thread.sleep(HALF_SECOND)
                } else {
                    // sync database with server (This is a long blocking call because long polling (lookIntoFuture) is set)
                    networkParams.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)

                    Log.d(TAG, "Starting online request for long polling")
                    val resultsFromSync = sync(networkParams)
                    if (!resultsFromSync.isNullOrEmpty()) {
                        val chatMessages = resultsFromSync.map(ChatMessageEntity::asModel)
                        val pair = Pair(true, chatMessages)
                        _messageFlow.emit(pair)
                    }

                    updateUiForLastCommonRead()

                    val newestMessage = chatDao.getNewestMessageId(internalConversationId).toInt()

                    // update field map vars for next cycle
                    fieldMap = getFieldMap(
                        lookIntoFuture = true,
                        includeLastKnown = false,
                        setReadMarker = true,
                        lastKnown = newestMessage
                    )
                }
            }
        }

    private suspend fun hasToLoadPreviousMessagesFromServer(beforeMessageId: Long): Boolean {
        val loadFromServer: Boolean

        val blockForMessage = getBlockOfMessage(beforeMessageId.toInt())

        if (blockForMessage == null) {
            Log.d(TAG, "No blocks for this message were found so we have to ask server")
            loadFromServer = true
        } else if (!blockForMessage.hasHistory) {
            Log.d(TAG, "The last chatBlock is reached so we won't request server for older messages")
            loadFromServer = false
        } else {
            // we know that beforeMessageId and blockForMessage.oldestMessageId are in the same block.
            // As we want the last DEFAULT_MESSAGES_LIMIT entries before beforeMessageId, we calculate if these
            // messages are DEFAULT_MESSAGES_LIMIT entries apart from each other

            val amountBetween = chatDao.getCountBetweenMessageIds(
                internalConversationId,
                beforeMessageId,
                blockForMessage.oldestMessageId
            )
            loadFromServer = amountBetween < DEFAULT_MESSAGES_LIMIT

            Log.d(
                TAG,
                "Amount between messageId " + beforeMessageId + " and " + blockForMessage.oldestMessageId +
                    " is: " + amountBetween + " so 'loadFromServer' is " + loadFromServer
            )
        }
        return loadFromServer
    }

    private fun getFieldMap(
        lookIntoFuture: Boolean,
        includeLastKnown: Boolean,
        setReadMarker: Boolean,
        lastKnown: Int?,
        limit: Int = DEFAULT_MESSAGES_LIMIT
    ): HashMap<String, Int> {
        val fieldMap = HashMap<String, Int>()

        fieldMap["includeLastKnown"] = if (includeLastKnown) 1 else 0

        if (lastKnown != null) {
            fieldMap["lastKnownMessageId"] = lastKnown
        }

        newXChatLastCommonRead?.let {
            fieldMap["lastCommonReadId"] = it
        }

        fieldMap["timeout"] = if (lookIntoFuture) 30 else 0
        fieldMap["limit"] = limit
        fieldMap["lookIntoFuture"] = if (lookIntoFuture) 1 else 0
        fieldMap["setReadMarker"] = if (setReadMarker) 1 else 0

        return fieldMap
    }

    override suspend fun getMessage(messageId: Long, bundle: Bundle): Flow<ChatMessage> {
        Log.d(TAG, "Get message with id $messageId")
        val loadFromServer = hasToLoadPreviousMessagesFromServer(messageId)

        if (loadFromServer) {
            val fieldMap = getFieldMap(
                lookIntoFuture = false,
                includeLastKnown = true,
                setReadMarker = false,
                lastKnown = messageId.toInt(),
                limit = 1
            )
            bundle.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)

            Log.d(TAG, "Starting online request for single message (e.g. a reply)")
            sync(bundle)
        }
        return chatDao.getChatMessageForConversation(internalConversationId, messageId)
            .map(ChatMessageEntity::asModel)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getMessagesFromServer(bundle: Bundle): Pair<Int, List<ChatMessageJson>>? {
        val fieldMap = bundle.getSerializable(BundleKeys.KEY_FIELD_MAP) as HashMap<String, Int>

        var attempts = 1
        while (attempts < 5) {
            Log.d(TAG, "message limit: " + fieldMap["limit"])
            try {
                val result = network.pullChatMessages(credentials, urlForChatting, fieldMap)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .map { it ->
                        when (it.code()) {
                            HTTP_CODE_OK -> {
                                Log.d(TAG, "getMessagesFromServer HTTP_CODE_OK")
                                newXChatLastCommonRead = it.headers()["X-Chat-Last-Common-Read"]?.let {
                                    Integer.parseInt(it)
                                }

                                return@map Pair(
                                    HTTP_CODE_OK,
                                    (it.body() as ChatOverall).ocs!!.data!!
                                )
                            }

                            HTTP_CODE_NOT_MODIFIED -> {
                                Log.d(TAG, "getMessagesFromServer HTTP_CODE_NOT_MODIFIED")

                                return@map Pair(
                                    HTTP_CODE_NOT_MODIFIED,
                                    listOf<ChatMessageJson>()
                                )
                            }

                            HTTP_CODE_PRECONDITION_FAILED -> {
                                Log.d(TAG, "getMessagesFromServer HTTP_CODE_PRECONDITION_FAILED")

                                return@map Pair(
                                    HTTP_CODE_PRECONDITION_FAILED,
                                    listOf<ChatMessageJson>()
                                )
                            }

                            else -> {
                                return@map Pair(
                                    HTTP_CODE_PRECONDITION_FAILED,
                                    listOf<ChatMessageJson>()
                                )
                            }
                        }
                    }
                    .blockingSingle()
                return result
            } catch (e: Exception) {
                Log.e(TAG, "Something went wrong when pulling chat messages (attempt: $attempts)", e)
                attempts++

                val newMessageLimit = when (attempts) {
                    2 -> 50
                    3 -> 10
                    else -> 5
                }
                fieldMap["limit"] = newMessageLimit
            }
        }
        Log.e(TAG, "All attempts to get messages from server failed")
        return null
    }

    private suspend fun sync(bundle: Bundle): List<ChatMessageEntity>? {
        if (!monitor.isOnline.first()) {
            Log.d(TAG, "Device is offline, can't load chat messages from server")
            return null
        }

        val result = getMessagesFromServer(bundle)
        if (result == null) {
            Log.d(TAG, "No result from server")
            return null
        }

        var chatMessagesFromSync: List<ChatMessageEntity>? = null

        val fieldMap = bundle.getSerializable(BundleKeys.KEY_FIELD_MAP) as HashMap<String, Int>
        val queriedMessageId = fieldMap["lastKnownMessageId"]
        val lookIntoFuture = fieldMap["lookIntoFuture"] == 1

        val statusCode = result.first

        val hasHistory = getHasHistory(statusCode, lookIntoFuture)

        Log.d(
            TAG,
            "internalConv=$internalConversationId statusCode=$statusCode lookIntoFuture=$lookIntoFuture " +
                "hasHistory=$hasHistory " +
                "queriedMessageId=$queriedMessageId"
        )

        val blockContainingQueriedMessage: ChatBlockEntity? = getBlockOfMessage(queriedMessageId)

        if (blockContainingQueriedMessage != null && !hasHistory) {
            blockContainingQueriedMessage.hasHistory = false
            chatBlocksDao.upsertChatBlock(blockContainingQueriedMessage)
            Log.d(TAG, "End of chat was reached so hasHistory=false is set")
        }

        if (result.second.isNotEmpty()) {
            val chatMessagesJson = result.second

            handleUpdateMessages(chatMessagesJson)

            chatMessagesFromSync = chatMessagesJson.map {
                it.asEntity(currentUser.id!!)
            }

            chatDao.upsertChatMessages(chatMessagesFromSync)

            val oldestIdFromSync = chatMessagesFromSync.minByOrNull { it.id }!!.id
            val newestIdFromSync = chatMessagesFromSync.maxByOrNull { it.id }!!.id
            Log.d(TAG, "oldestIdFromSync: $oldestIdFromSync")
            Log.d(TAG, "newestIdFromSync: $newestIdFromSync")

            var oldestMessageIdForNewChatBlock = oldestIdFromSync
            var newestMessageIdForNewChatBlock = newestIdFromSync

            if (blockContainingQueriedMessage != null) {
                if (lookIntoFuture) {
                    val oldestMessageIdFromBlockOfQueriedMessage = blockContainingQueriedMessage.oldestMessageId
                    Log.d(TAG, "oldestMessageIdFromBlockOfQueriedMessage: $oldestMessageIdFromBlockOfQueriedMessage")
                    oldestMessageIdForNewChatBlock = oldestMessageIdFromBlockOfQueriedMessage
                } else {
                    val newestMessageIdFromBlockOfQueriedMessage = blockContainingQueriedMessage.newestMessageId
                    Log.d(TAG, "newestMessageIdFromBlockOfQueriedMessage: $newestMessageIdFromBlockOfQueriedMessage")
                    newestMessageIdForNewChatBlock = newestMessageIdFromBlockOfQueriedMessage
                }
            }

            Log.d(TAG, "oldestMessageIdForNewChatBlock: $oldestMessageIdForNewChatBlock")
            Log.d(TAG, "newestMessageIdForNewChatBlock: $newestMessageIdForNewChatBlock")

            val newChatBlock = ChatBlockEntity(
                internalConversationId = internalConversationId,
                accountId = conversationModel.accountId,
                token = conversationModel.token,
                oldestMessageId = oldestMessageIdForNewChatBlock,
                newestMessageId = newestMessageIdForNewChatBlock,
                hasHistory = hasHistory
            )
            chatBlocksDao.upsertChatBlock(newChatBlock)

            updateBlocks(newChatBlock)
        } else {
            Log.d(TAG, "no data is updated...")
        }

        return chatMessagesFromSync
    }

    private suspend fun handleUpdateMessages(messagesJson: List<ChatMessageJson>) {
        messagesJson.forEach { messageJson ->
            when (messageJson.systemMessageType) {
                ChatMessage.SystemMessageType.REACTION,
                ChatMessage.SystemMessageType.REACTION_REVOKED,
                ChatMessage.SystemMessageType.REACTION_DELETED,
                ChatMessage.SystemMessageType.MESSAGE_DELETED,
                ChatMessage.SystemMessageType.POLL_VOTED,
                ChatMessage.SystemMessageType.MESSAGE_EDITED -> {
                    // the parent message is always the newest state, no matter how old the system message is.
                    // that's why we can just take the parent, update it in DB and update the UI
                    messageJson.parentMessage?.let { parentMessageJson ->
                        parentMessageJson.message?.let {
                            val parentMessageEntity = parentMessageJson.asEntity(currentUser.id!!)
                            chatDao.upsertChatMessage(parentMessageEntity)
                            _updateMessageFlow.emit(parentMessageEntity.asModel())
                        }
                    }
                }

                ChatMessage.SystemMessageType.CLEARED_CHAT -> {
                    // for lookIntoFuture just deleting everything would be fine.
                    // But lets say we did not open the chat for a while and in between it was cleared.
                    // We just load the last messages but this don't contain the system message.
                    // We scroll up and load the system message. Deleting everything is not an option as we
                    // would loose the messages that we want to keep. We only want to
                    // delete the messages and chatBlocks older than the system message.
                    chatDao.deleteMessagesOlderThan(internalConversationId, messageJson.id)
                    chatBlocksDao.deleteChatBlocksOlderThan(internalConversationId, messageJson.id)
                }

                else -> {}
            }
        }
    }

    /**
     *  304 is returned when oldest message of chat was queried or when long polling request returned with no
     *  modification. hasHistory is only set to false, when 304 was returned for the the oldest message
     */
    private fun getHasHistory(statusCode: Int, lookIntoFuture: Boolean): Boolean =
        if (statusCode == HTTP_CODE_NOT_MODIFIED) {
            lookIntoFuture
        } else {
            true
        }

    private suspend fun getBlockOfMessage(queriedMessageId: Int?): ChatBlockEntity? {
        var blockContainingQueriedMessage: ChatBlockEntity? = null
        if (queriedMessageId != null) {
            val blocksContainingQueriedMessage =
                chatBlocksDao.getChatBlocksContainingMessageId(internalConversationId, queriedMessageId.toLong())

            val chatBlocks = blocksContainingQueriedMessage.first()
            if (chatBlocks.size > 1) {
                Log.w(TAG, "multiple chat blocks with messageId $queriedMessageId were found")
            }

            blockContainingQueriedMessage = if (chatBlocks.isNotEmpty()) {
                chatBlocks.first()
            } else {
                null
            }
        }
        return blockContainingQueriedMessage
    }

    private suspend fun updateBlocks(chatBlock: ChatBlockEntity): ChatBlockEntity? {
        val connectedChatBlocks =
            chatBlocksDao.getConnectedChatBlocks(
                internalConversationId,
                chatBlock.oldestMessageId,
                chatBlock.newestMessageId
            ).first()

        return if (connectedChatBlocks.size == 1) {
            Log.d(TAG, "This chatBlock is not connected to others")
            val chatBlockFromDb = connectedChatBlocks[0]
            Log.d(TAG, "chatBlockFromDb.oldestMessageId: " + chatBlockFromDb.oldestMessageId)
            Log.d(TAG, "chatBlockFromDb.newestMessageId: " + chatBlockFromDb.newestMessageId)
            chatBlockFromDb
        } else if (connectedChatBlocks.size > 1) {
            Log.d(TAG, "Found " + connectedChatBlocks.size + " chat blocks that are connected")
            val oldestIdFromDbChatBlocks =
                connectedChatBlocks.minByOrNull { it.oldestMessageId }!!.oldestMessageId
            val newestIdFromDbChatBlocks =
                connectedChatBlocks.maxByOrNull { it.newestMessageId }!!.newestMessageId

            val hasNoHistory = connectedChatBlocks.any { !it.hasHistory }
            val hasHistory = !hasNoHistory
            Log.d(TAG, "hasHistory = $hasHistory")

            chatBlocksDao.deleteChatBlocks(connectedChatBlocks)
            Log.d(TAG, "These chat blocks were deleted")

            val newChatBlock = ChatBlockEntity(
                internalConversationId = internalConversationId,
                accountId = conversationModel.accountId,
                token = conversationModel.token,
                oldestMessageId = oldestIdFromDbChatBlocks,
                newestMessageId = newestIdFromDbChatBlocks,
                hasHistory = hasHistory
            )
            chatBlocksDao.upsertChatBlock(newChatBlock)
            Log.d(TAG, "A new chat block was created that covers all the range of the found chatblocks")
            Log.d(TAG, "new chatBlock - oldest MessageId: $oldestIdFromDbChatBlocks")
            Log.d(TAG, "new chatBlock - newest MessageId: $newestIdFromDbChatBlocks")
            newChatBlock
        } else {
            Log.d(TAG, "No chat block found ....")
            null
        }
    }

    private suspend fun showMessagesBeforeAndEqual(internalConversationId: String, messageId: Long, limit: Int) {
        suspend fun getMessagesBeforeAndEqual(
            messageId: Long,
            internalConversationId: String,
            messageLimit: Int
        ): List<ChatMessage> =
            chatDao.getMessagesForConversationBeforeAndEqual(
                internalConversationId,
                messageId,
                messageLimit
            ).map {
                it.map(ChatMessageEntity::asModel)
            }.first()

        val list = getMessagesBeforeAndEqual(
            messageId,
            internalConversationId,
            limit
        )

        if (list.isNotEmpty()) {
            val pair = Pair(false, list)
            _messageFlow.emit(pair)
        }
    }

    private suspend fun showMessagesBefore(internalConversationId: String, messageId: Long, limit: Int) {
        suspend fun getMessagesBefore(
            messageId: Long,
            internalConversationId: String,
            messageLimit: Int
        ): List<ChatMessage> =
            chatDao.getMessagesForConversationBefore(
                internalConversationId,
                messageId,
                messageLimit
            ).map {
                it.map(ChatMessageEntity::asModel)
            }.first()

        val list = getMessagesBefore(
            messageId,
            internalConversationId,
            limit
        )

        if (list.isNotEmpty()) {
            val pair = Pair(false, list)
            _messageFlow.emit(pair)
        }
    }

    override fun handleOnPause() {
        itIsPaused = true
    }

    override fun handleOnResume() {
        itIsPaused = false
    }

    override fun handleOnStop() {
        // unused atm
    }

    override fun handleChatOnBackPress() {
        scope.cancel()
    }

    companion object {
        val TAG = OfflineFirstChatRepository::class.simpleName
        private const val HTTP_CODE_OK: Int = 200
        private const val HTTP_CODE_NOT_MODIFIED = 304
        private const val HTTP_CODE_PRECONDITION_FAILED = 412
        private const val HALF_SECOND = 500L
        private const val DELAY_TO_ENSURE_MESSAGES_ARE_ADDED: Long = 100
        private const val DEFAULT_MESSAGES_LIMIT = 100
    }
}
