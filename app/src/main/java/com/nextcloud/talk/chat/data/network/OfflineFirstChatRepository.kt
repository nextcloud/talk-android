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

            newXChatLastCommonRead = conversationModel.lastCommonReadMessage

            val fieldMap = getFieldMap(
                lookIntoFuture = false,
                includeLastKnown = true,
                setReadMarker = true,
                lastKnown = null
            )
            withNetworkParams.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)
            withNetworkParams.putString(BundleKeys.KEY_ROOM_TOKEN, conversationModel.token)

            sync(withNetworkParams)

            Log.d(TAG, "newestMessageId after sync: " + chatDao.getNewestMessageId(internalConversationId))

            showLast100MessagesBeforeAndEqual(
                internalConversationId,
                chatDao.getNewestMessageId(internalConversationId)
            )
            updateUiForLastCommonRead(200)

            initMessagePolling()
        }

    private fun updateUiForLastCommonRead(delay: Long) {
        scope.launch {
            // delay is a dirty workaround to make sure messages are added to adapter on initial load before setting
            // their read status(otherwise there is a race condition between adding messages and setting their read
            // status).
            if (delay > 0) {
                delay(delay)
            }
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
                sync(withNetworkParams)
            }

            showLast100MessagesBefore(internalConversationId, beforeMessageId)
            updateUiForLastCommonRead(0)
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

            while (!itIsPaused) {
                if (!monitor.isOnline.first()) Thread.sleep(500)

                // sync database with server (This is a long blocking call because long polling (lookIntoFuture) is set)
                networkParams.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)

                val resultsFromSync = sync(networkParams)
                if (!resultsFromSync.isNullOrEmpty()) {
                    val chatMessages = resultsFromSync.map(ChatMessageEntity::asModel)
                    val pair = Pair(true, chatMessages)
                    _messageFlow.emit(pair)
                }

                updateUiForLastCommonRead(0)

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
            // As we want the last 100 entries before beforeMessageId, we calculate if these messages are 100
            // entries apart from each other

            val amountBetween = chatDao.getCountBetweenMessageIds(
                internalConversationId,
                beforeMessageId,
                blockForMessage.oldestMessageId
            )
            loadFromServer = amountBetween < 100

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
        lastKnown: Int?
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
        fieldMap["limit"] = 100
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
                lastKnown = messageId.toInt()
            )
            bundle.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)

            // Although only the single message will be returned, a server request will load 100 messages.
            // If this turns out to be confusion for debugging we could load set the limit to 1 for this request.
            sync(bundle)
        }
        return chatDao.getChatMessageForConversation(internalConversationId, messageId)
            .map(ChatMessageEntity::asModel)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getMessagesFromServer(bundle: Bundle): Pair<Int, List<ChatMessageJson>>? {
        Log.d(TAG, "An online request is made!!!!!!!!!!!!!!!!!!!!")
        val fieldMap = bundle.getSerializable(BundleKeys.KEY_FIELD_MAP) as HashMap<String, Int>

        try {
            val result = network.pullChatMessages(credentials, urlForChatting, fieldMap)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                // .timeout(3, TimeUnit.SECONDS)
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
            Log.e(TAG, "Something went wrong when pulling chat messages", e)
        }
        return null
    }

    private suspend fun sync(bundle: Bundle): List<ChatMessageEntity>? {
        if (!monitor.isOnline.first()) {
            Log.d(TAG, "Device is offline, can't load chat messages from server")
            return null
        }

        val result = getMessagesFromServer(bundle) ?: return listOf()
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
                        val parentMessageEntity = parentMessageJson.asEntity(currentUser.id!!)
                        chatDao.upsertChatMessage(parentMessageEntity)
                        _updateMessageFlow.emit(parentMessageEntity.asModel())
                    }
                }

                ChatMessage.SystemMessageType.CLEARED_CHAT -> {
                    // for lookIntoFuture just deleting everything would be fine.
                    // But lets say we did not open the chat for a while and in between it was cleared.
                    // We just load the last 100 messages but this don't contain the system message.
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
    private fun getHasHistory(statusCode: Int, lookIntoFuture: Boolean): Boolean {
        return if (statusCode == HTTP_CODE_NOT_MODIFIED) {
            lookIntoFuture
        } else {
            true
        }
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

    private suspend fun showLast100MessagesBeforeAndEqual(internalConversationId: String, messageId: Long) {
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
            100
        )

        if (list.isNotEmpty()) {
            val pair = Pair(false, list)
            _messageFlow.emit(pair)
        }
    }

    private suspend fun showLast100MessagesBefore(internalConversationId: String, messageId: Long) {
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
            100
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

    companion object {
        val TAG = OfflineFirstChatRepository::class.simpleName
        private const val HTTP_CODE_OK: Int = 200
        private const val HTTP_CODE_NOT_MODIFIED = 304
        private const val HTTP_CODE_PRECONDITION_FAILED = 412
    }
}
