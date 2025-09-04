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
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.data.database.model.ChatBlockEntity
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.toIntOrZero
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.converters.EnumActorTypeConverter
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.message.SendMessageUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@Suppress("LargeClass", "TooManyFunctions")
class OfflineFirstChatRepository @Inject constructor(
    private val chatDao: ChatMessagesDao,
    private val chatBlocksDao: ChatBlocksDao,
    private val network: ChatNetworkDataSource,
    private val networkMonitor: NetworkMonitor,
    userProvider: CurrentUserProviderNew
) : ChatMessageRepository {

    val currentUser: User = userProvider.currentUser.blockingGet()

    override val messageFlow:
        Flow<
            Triple<
                Boolean,
                Boolean,
                List<ChatMessage>
                >
            >
        get() = _messageFlow

    private val _messageFlow:
        MutableSharedFlow<
            Triple<
                Boolean,
                Boolean,
                List<ChatMessage>
                >
            > = MutableSharedFlow()

    override val updateMessageFlow: Flow<ChatMessage>
        get() = _updateMessageFlow

    private val _updateMessageFlow:
        MutableSharedFlow<ChatMessage> = MutableSharedFlow()

    override val lastCommonReadFlow:
        Flow<Int>
        get() = _lastCommonReadFlow

    private val _lastCommonReadFlow:
        MutableSharedFlow<Int> = MutableSharedFlow()

    override val lastReadMessageFlow: Flow<Int>
        get() = _lastReadMessageFlow

    private val _lastReadMessageFlow:
        MutableSharedFlow<Int> = MutableSharedFlow()

    override val generalUIFlow: Flow<String>
        get() = _generalUIFlow

    private val _generalUIFlow: MutableSharedFlow<String> = MutableSharedFlow()

    override val removeMessageFlow: Flow<ChatMessage>
        get() = _removeMessageFlow

    private val _removeMessageFlow:
        MutableSharedFlow<ChatMessage> = MutableSharedFlow()

    private var newXChatLastCommonRead: Int? = null
    private var itIsPaused = false
    private lateinit var scope: CoroutineScope

    lateinit var internalConversationId: String
    private lateinit var conversationModel: ConversationModel
    private lateinit var credentials: String
    private lateinit var urlForChatting: String
    private var threadId: Long? = null

    override fun initData(credentials: String, urlForChatting: String, roomToken: String, threadId: Long?) {
        internalConversationId = currentUser.id.toString() + "@" + roomToken
        this.credentials = credentials
        this.urlForChatting = urlForChatting
        this.threadId = threadId
    }

    override fun updateConversation(conversationModel: ConversationModel) {
        this.conversationModel = conversationModel
    }

    override fun initScopeAndLoadInitialMessages(withNetworkParams: Bundle) {
        scope = CoroutineScope(Dispatchers.IO)
        loadInitialMessages(withNetworkParams)
    }

    private fun loadInitialMessages(withNetworkParams: Bundle): Job =
        scope.launch {
            Log.d(TAG, "---- loadInitialMessages ------------")
            newXChatLastCommonRead = conversationModel.lastCommonReadMessage

            Log.d(TAG, "conversationModel.internalId: " + conversationModel.internalId)
            Log.d(TAG, "conversationModel.lastReadMessage:" + conversationModel.lastReadMessage)

            var newestMessageIdFromDb = chatBlocksDao.getNewestMessageIdFromChatBlocks(internalConversationId, threadId)
            Log.d(TAG, "newestMessageIdFromDb: $newestMessageIdFromDb")

            val weAlreadyHaveSomeOfflineMessages = newestMessageIdFromDb > 0
            val weHaveAtLeastTheLastReadMessage = newestMessageIdFromDb >= conversationModel.lastReadMessage.toLong()
            Log.d(TAG, "weAlreadyHaveSomeOfflineMessages:$weAlreadyHaveSomeOfflineMessages")
            Log.d(TAG, "weHaveAtLeastTheLastReadMessage:$weHaveAtLeastTheLastReadMessage")

            if (weAlreadyHaveSomeOfflineMessages && weHaveAtLeastTheLastReadMessage) {
                Log.d(
                    TAG,
                    "Initial online request is skipped because offline messages are up to date" +
                        " until lastReadMessage"
                )
                Log.d(TAG, "For messages newer than lastRead, lookIntoFuture will load them.")
            } else {
                if (!weAlreadyHaveSomeOfflineMessages) {
                    Log.d(TAG, "An online request for newest 100 messages is made because offline chat is empty")
                    if (networkMonitor.isOnline.value.not()) {
                        _generalUIFlow.emit(ChatActivity.NO_OFFLINE_MESSAGES_FOUND)
                    }
                } else {
                    Log.d(
                        TAG,
                        "An online request for newest 100 messages is made because we don't have the lastReadMessage " +
                            "(gaps could be closed by scrolling up to merge the chatblocks)"
                    )
                }

                // set up field map to load the newest messages
                val fieldMap = getFieldMap(
                    lookIntoFuture = false,
                    timeout = 0,
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

                newestMessageIdFromDb = chatBlocksDao.getNewestMessageIdFromChatBlocks(internalConversationId, threadId)
                Log.d(TAG, "newestMessageIdFromDb after sync: $newestMessageIdFromDb")
            }

            handleMessagesFromDb(newestMessageIdFromDb)

            initMessagePolling(newestMessageIdFromDb)
        }

    private suspend fun handleMessagesFromDb(newestMessageIdFromDb: Long) {
        if (newestMessageIdFromDb.toInt() != 0) {
            val limit = getCappedMessagesAmountOfChatBlock(newestMessageIdFromDb)

            val list = getMessagesBeforeAndEqual(
                messageId = newestMessageIdFromDb,
                internalConversationId = internalConversationId,
                messageLimit = limit
            )
            if (list.isNotEmpty()) {
                handleNewAndTempMessages(
                    receivedChatMessages = list,
                    lookIntoFuture = false,
                    showUnreadMessagesMarker = false
                )
            }

            // this call could be deleted when we have a worker to send messages..
            sendUnsentChatMessages(credentials, urlForChatting)

            // delay is a dirty workaround to make sure messages are added to adapter on initial load before dealing
            // with them (otherwise there is a race condition).
            delay(DELAY_TO_ENSURE_MESSAGES_ARE_ADDED)

            updateUiForLastCommonRead()
            updateUiForLastReadMessage(newestMessageIdFromDb)
        }
    }

    private suspend fun getCappedMessagesAmountOfChatBlock(messageId: Long): Int {
        val chatBlock = getBlockOfMessage(messageId.toInt())

        if (chatBlock != null) {
            val amountBetween = chatDao.getCountBetweenMessageIds(
                internalConversationId,
                messageId,
                chatBlock.oldestMessageId,
                threadId
            )

            Log.d(TAG, "amount of messages between newestMessageId and oldest message of same ChatBlock:$amountBetween")
            val limit = if (amountBetween > DEFAULT_MESSAGES_LIMIT) {
                DEFAULT_MESSAGES_LIMIT
            } else {
                amountBetween
            }
            Log.d(TAG, "limit of messages to load for UI (max 100 to ensure performance is okay):$limit")
            return limit
        } else {
            Log.e(TAG, "No chat block found. Returning 0 as limit.")
            return 0
        }
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
                timeout = 0,
                includeLastKnown = false,
                setReadMarker = true,
                lastKnown = beforeMessageId.toInt()
            )
            withNetworkParams.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)

            val loadFromServer = hasToLoadPreviousMessagesFromServer(beforeMessageId, DEFAULT_MESSAGES_LIMIT)

            if (loadFromServer) {
                Log.d(TAG, "Starting online request for loadMoreMessages")
                sync(withNetworkParams)
            }

            showMessagesBefore(internalConversationId, beforeMessageId, DEFAULT_MESSAGES_LIMIT)
            updateUiForLastCommonRead()
        }

    override fun initMessagePolling(initialMessageId: Long): Job =
        scope.launch {
            Log.d(TAG, "---- initMessagePolling ------------")

            Log.d(TAG, "newestMessage: $initialMessageId")

            var fieldMap = getFieldMap(
                lookIntoFuture = true,
                // timeout for first longpoll is 0, so "unread message" info is not shown if there were
                // initially no messages but someone writes us in the first 30 seconds.
                timeout = 0,
                includeLastKnown = false,
                setReadMarker = true,
                lastKnown = initialMessageId.toInt()
            )

            val networkParams = Bundle()

            var showUnreadMessagesMarker = true

            while (isActive) {
                if (!networkMonitor.isOnline.value || itIsPaused) {
                    Thread.sleep(HALF_SECOND)
                } else {
                    // sync database with server
                    // (This is a long blocking call because long polling (lookIntoFuture) is set)
                    networkParams.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)

                    Log.d(TAG, "Starting online request for long polling")
                    val resultsFromSync = sync(networkParams)
                    if (!resultsFromSync.isNullOrEmpty()) {
                        val chatMessages = resultsFromSync.map(ChatMessageEntity::asModel)

                        val weHaveMessagesFromOurself = chatMessages.any { it.actorId == currentUser.userId }
                        showUnreadMessagesMarker = showUnreadMessagesMarker && !weHaveMessagesFromOurself

                        if (isActive) {
                            handleNewAndTempMessages(
                                receivedChatMessages = chatMessages,
                                lookIntoFuture = true,
                                showUnreadMessagesMarker = showUnreadMessagesMarker
                            )
                        } else {
                            Log.d(TAG, "scope was already canceled")
                        }
                    } else {
                        Log.d(TAG, "resultsFromSync are null or empty")
                    }

                    updateUiForLastCommonRead()

                    val newestMessage = chatBlocksDao.getNewestMessageIdFromChatBlocks(
                        internalConversationId,
                        threadId
                    ).toInt()

                    // update field map vars for next cycle
                    fieldMap = getFieldMap(
                        lookIntoFuture = true,
                        timeout = 30,
                        includeLastKnown = false,
                        setReadMarker = true,
                        lastKnown = newestMessage
                    )

                    showUnreadMessagesMarker = false
                }
            }
        }

    private suspend fun handleNewAndTempMessages(
        receivedChatMessages: List<ChatMessage>,
        lookIntoFuture: Boolean,
        showUnreadMessagesMarker: Boolean
    ) {
        receivedChatMessages.forEach {
            Log.d(TAG, "receivedChatMessage: " + it.message)
        }

        // remove all temp messages from UI
        val oldTempMessages = chatDao.getTempMessagesForConversation(internalConversationId)
            .first()
            .map(ChatMessageEntity::asModel)
        oldTempMessages.forEach {
            Log.d(TAG, "oldTempMessage to be removed from UI: " + it.message)
            _removeMessageFlow.emit(it)
        }

        // add new messages to UI
        val tripleChatMessages = Triple(lookIntoFuture, showUnreadMessagesMarker, receivedChatMessages)
        _messageFlow.emit(tripleChatMessages)

        // remove temp messages from DB that are now found in the new messages
        val chatMessagesReferenceIds = receivedChatMessages.mapTo(HashSet(receivedChatMessages.size)) { it.referenceId }
        val tempChatMessagesThatCanBeReplaced = oldTempMessages.filter { it.referenceId in chatMessagesReferenceIds }
        tempChatMessagesThatCanBeReplaced.forEach {
            Log.d(TAG, "oldTempMessage that was identified in newMessages: " + it.message)
        }
        chatDao.deleteTempChatMessages(
            internalConversationId,
            tempChatMessagesThatCanBeReplaced.map { it.referenceId!! }
        )

        // add the remaining temp messages to UI again
        val remainingTempMessages = chatDao.getTempMessagesForConversation(internalConversationId)
            .first()
            .sortedBy { it.internalId }
            .map(ChatMessageEntity::asModel)

        remainingTempMessages.forEach {
            Log.d(TAG, "remainingTempMessage: " + it.message)
        }

        val triple = Triple(true, false, remainingTempMessages)
        _messageFlow.emit(triple)
    }

    private suspend fun hasToLoadPreviousMessagesFromServer(beforeMessageId: Long, amountToCheck: Int): Boolean {
        val loadFromServer: Boolean

        val blockForMessage = getBlockOfMessage(beforeMessageId.toInt())

        if (blockForMessage == null) {
            Log.d(TAG, "No blocks for this message were found so we have to ask server")
            loadFromServer = true
        } else if (!blockForMessage.hasHistory) {
            Log.d(TAG, "The last chatBlock is reached so we won't request server for older messages")
            loadFromServer = false
        } else {
            val amountBetween = chatDao.getCountBetweenMessageIds(
                internalConversationId,
                beforeMessageId,
                blockForMessage.oldestMessageId,
                threadId
            )
            loadFromServer = amountBetween < amountToCheck

            Log.d(
                TAG,
                "Amount between messageId " + beforeMessageId + " and " + blockForMessage.oldestMessageId +
                    " is: " + amountBetween + " and $amountToCheck were needed, so 'loadFromServer' is " +
                    loadFromServer
            )
        }
        return loadFromServer
    }

    @Suppress("LongParameterList")
    private fun getFieldMap(
        lookIntoFuture: Boolean,
        timeout: Int,
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

        threadId?.let { fieldMap["threadId"] = it.toInt() }

        fieldMap["timeout"] = timeout
        fieldMap["limit"] = limit

        fieldMap["lookIntoFuture"] = if (lookIntoFuture) 1 else 0
        fieldMap["setReadMarker"] = if (setReadMarker) 1 else 0

        return fieldMap
    }

    override suspend fun getNumberOfThreadReplies(threadId: Long): Int =
        chatDao.getNumberOfThreadReplies(internalConversationId, threadId)

    override suspend fun getMessage(messageId: Long, bundle: Bundle): Flow<ChatMessage> {
        Log.d(TAG, "Get message with id $messageId")
        val loadFromServer = hasToLoadPreviousMessagesFromServer(messageId, 1)

        if (loadFromServer) {
            val fieldMap = getFieldMap(
                lookIntoFuture = false,
                timeout = 0,
                includeLastKnown = true,
                setReadMarker = false,
                lastKnown = messageId.toInt(),
                limit = 1
            )
            bundle.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)

            Log.d(TAG, "Starting online request for single message (e.g. a reply)")
            sync(bundle)
        }
        return chatDao.getChatMessageForConversation(
            internalConversationId,
            messageId
        ).map(ChatMessageEntity::asModel)
    }

    @Suppress("UNCHECKED_CAST", "MagicNumber", "Detekt.TooGenericExceptionCaught")
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
        if (!networkMonitor.isOnline.value) {
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
            chatMessagesFromSync = updateMessagesData(
                result.second,
                blockContainingQueriedMessage,
                lookIntoFuture,
                hasHistory
            )
        } else {
            Log.d(TAG, "no data is updated...")
        }

        return chatMessagesFromSync
    }

    private suspend fun OfflineFirstChatRepository.updateMessagesData(
        chatMessagesJson: List<ChatMessageJson>,
        blockContainingQueriedMessage: ChatBlockEntity?,
        lookIntoFuture: Boolean,
        hasHistory: Boolean
    ): List<ChatMessageEntity> {
        handleUpdateMessages(chatMessagesJson)

        val chatMessagesFromSyncToProcess = chatMessagesJson.map {
            it.asEntity(currentUser.id!!)
        }

        chatDao.upsertChatMessages(chatMessagesFromSyncToProcess)

        val oldestIdFromSync = chatMessagesFromSyncToProcess.minByOrNull { it.id }!!.id
        val newestIdFromSync = chatMessagesFromSyncToProcess.maxByOrNull { it.id }!!.id
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
            threadId = threadId,
            oldestMessageId = oldestMessageIdForNewChatBlock,
            newestMessageId = newestMessageIdForNewChatBlock,
            hasHistory = hasHistory
        )
        chatBlocksDao.upsertChatBlock(newChatBlock) // crash when no conversation thread exists!

        updateBlocks(newChatBlock)
        return chatMessagesFromSyncToProcess
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
                chatBlocksDao.getChatBlocksContainingMessageId(
                    internalConversationId = internalConversationId,
                    threadId = threadId,
                    messageId = queriedMessageId.toLong()
                )

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
                internalConversationId = internalConversationId,
                threadId = threadId,
                oldestMessageId = chatBlock.oldestMessageId,
                newestMessageId = chatBlock.newestMessageId
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
                threadId = threadId,
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

    suspend fun getMessagesBeforeAndEqual(
        messageId: Long,
        internalConversationId: String,
        messageLimit: Int
    ): List<ChatMessage> =
        chatDao.getMessagesForConversationBeforeAndEqual(
            internalConversationId,
            messageId,
            messageLimit,
            threadId
        ).map {
            it.map(ChatMessageEntity::asModel)
        }.first()

    private suspend fun showMessagesBefore(internalConversationId: String, messageId: Long, limit: Int) {
        suspend fun getMessagesBefore(
            messageId: Long,
            internalConversationId: String,
            messageLimit: Int
        ): List<ChatMessage> =
            chatDao.getMessagesForConversationBefore(
                internalConversationId,
                messageId,
                messageLimit,
                threadId
            ).map {
                it.map(ChatMessageEntity::asModel)
            }.first()

        val list = getMessagesBefore(
            messageId,
            internalConversationId,
            limit
        )

        if (list.isNotEmpty()) {
            val triple = Triple(false, false, list)
            _messageFlow.emit(triple)
        }
    }

    override fun handleOnPause() {
        itIsPaused = true
        if (this::scope.isInitialized) {
            scope.cancel()
        }
    }

    override fun handleOnResume() {
        itIsPaused = false
    }

    override fun handleOnStop() {
        // not used
    }

    @Suppress("LongParameterList")
    override suspend fun sendChatMessage(
        credentials: String,
        url: String,
        message: String,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String,
        threadTitle: String?
    ): Flow<Result<ChatMessage?>> {
        if (!networkMonitor.isOnline.value) {
            return flow {
                emit(Result.failure(IOException("Skipped to send message as device is offline")))
            }
        }

        return flow {
            val response = network.sendChatMessage(
                credentials,
                url,
                message,
                displayName,
                replyTo,
                sendWithoutNotification,
                referenceId,
                threadTitle
            )

            val chatMessageModel = response.ocs?.data?.asModel()

            val sentMessage = chatDao.getTempMessageForConversation(
                internalConversationId,
                referenceId,
                threadId
            ).firstOrNull()

            sentMessage?.let {
                it.sendStatus = SendStatus.SENT_PENDING_ACK
                chatDao.updateChatMessage(it)
            }

            Log.d(TAG, "sending chat message succeeded: " + message)
            emit(Result.success(chatMessageModel))
        }
            .catch { e ->
                Log.e(TAG, "Error when sending message", e)

                val failedMessage = chatDao.getTempMessageForConversation(
                    internalConversationId,
                    referenceId,
                    threadId
                ).firstOrNull()
                failedMessage?.let {
                    it.sendStatus = SendStatus.FAILED
                    chatDao.updateChatMessage(it)

                    val failedMessageModel = it.asModel()
                    _updateMessageFlow.emit(failedMessageModel)
                }
                emit(Result.failure(e))
            }
    }

    @Suppress("LongParameterList")
    override suspend fun resendChatMessage(
        credentials: String,
        url: String,
        message: String,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String
    ): Flow<Result<ChatMessage?>> {
        val messageToResend = chatDao.getTempMessageForConversation(
            internalConversationId,
            referenceId,
            threadId
        ).firstOrNull()
        return if (messageToResend != null) {
            messageToResend.sendStatus = SendStatus.PENDING
            chatDao.updateChatMessage(messageToResend)

            val messageToResendModel = messageToResend.asModel()
            _updateMessageFlow.emit(messageToResendModel)

            sendChatMessage(
                credentials = credentials,
                url = url,
                message = message,
                displayName = displayName,
                replyTo = replyTo,
                sendWithoutNotification = sendWithoutNotification,
                referenceId = referenceId,
                threadTitle = null
            )
        } else {
            flow {
                emit(Result.failure(IllegalStateException("No temporary message found to resend")))
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    override suspend fun editChatMessage(
        credentials: String,
        url: String,
        text: String
    ): Flow<Result<ChatOverallSingleMessage>> =
        flow {
            try {
                val response = network.editChatMessage(
                    credentials,
                    url,
                    text
                )
                emit(Result.success(response))
            } catch (e: Exception) {
                emit(Result.failure(e))
            }
        }

    @Suppress("Detekt.TooGenericExceptionCaught")
    override suspend fun editTempChatMessage(message: ChatMessage, editedMessageText: String): Flow<Boolean> =
        flow {
            try {
                val messageToEdit = chatDao.getChatMessageForConversation(
                    internalConversationId,
                    message.jsonMessageId.toLong()
                ).first()
                messageToEdit.message = editedMessageText
                chatDao.upsertChatMessage(messageToEdit)

                val editedMessageModel = messageToEdit.asModel()
                _updateMessageFlow.emit(editedMessageModel)
                emit(true)
            } catch (e: Exception) {
                emit(false)
            }
        }

    override suspend fun sendUnsentChatMessages(credentials: String, url: String) {
        val tempMessages = chatDao.getTempUnsentMessagesForConversation(internalConversationId, threadId).first()
        tempMessages.sortedBy { it.internalId }.onEach {
            sendChatMessage(
                credentials,
                url,
                it.message,
                it.actorDisplayName,
                it.parentMessageId?.toIntOrZero() ?: 0,
                it.silent,
                it.referenceId.orEmpty(),
                null
            ).collect { result ->
                if (result.isSuccess) {
                    Log.d(TAG, "Sent temp message")
                } else {
                    Log.e(TAG, "Failed to send temp message")
                }
            }
        }
    }

    override suspend fun deleteTempMessage(chatMessage: ChatMessage) {
        chatDao.deleteTempChatMessages(internalConversationId, listOf(chatMessage.referenceId.orEmpty()))
        _removeMessageFlow.emit(chatMessage)
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    override suspend fun addTemporaryMessage(
        message: CharSequence,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String
    ): Flow<Result<ChatMessage?>> =
        flow {
            try {
                val tempChatMessageEntity = createChatMessageEntity(
                    internalConversationId,
                    message.toString(),
                    replyTo,
                    sendWithoutNotification,
                    referenceId
                )

                chatDao.upsertChatMessage(tempChatMessageEntity)

                val tempChatMessageModel = tempChatMessageEntity.asModel()

                emit(Result.success(tempChatMessageModel))

                val triple = Triple(true, false, listOf(tempChatMessageModel))
                _messageFlow.emit(triple)
            } catch (e: Exception) {
                Log.e(TAG, "Something went wrong when adding temporary message", e)
                emit(Result.failure(e))
            }
        }

    private fun createChatMessageEntity(
        internalConversationId: String,
        message: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String
    ): ChatMessageEntity {
        val currentTimeMillies = System.currentTimeMillis()

        val currentTimeWithoutYear = SendMessageUtils().removeYearFromTimestamp(currentTimeMillies)

        val parentMessageId = if (replyTo != 0) {
            replyTo.toLong()
        } else {
            null
        }

        val entity = ChatMessageEntity(
            internalId = "$internalConversationId@_temp_$currentTimeMillies",
            internalConversationId = internalConversationId,
            id = currentTimeWithoutYear.toLong(),
            threadId = threadId,
            message = message,
            deleted = false,
            token = conversationModel.token,
            actorId = currentUser.userId!!,
            actorType = EnumActorTypeConverter().convertToString(Participant.ActorType.USERS),
            accountId = currentUser.id!!,
            messageParameters = null,
            messageType = "comment",
            parentMessageId = parentMessageId,
            systemMessageType = ChatMessage.SystemMessageType.DUMMY,
            replyable = false,
            timestamp = currentTimeMillies / MILLIES,
            expirationTimestamp = 0,
            actorDisplayName = currentUser.displayName!!,
            referenceId = referenceId,
            isTemporary = true,
            sendStatus = SendStatus.PENDING,
            silent = sendWithoutNotification
        )
        return entity
    }

    companion object {
        val TAG = OfflineFirstChatRepository::class.simpleName
        private const val HTTP_CODE_OK: Int = 200
        private const val HTTP_CODE_NOT_MODIFIED = 304
        private const val HTTP_CODE_PRECONDITION_FAILED = 412
        private const val HALF_SECOND = 500L
        private const val DELAY_TO_ENSURE_MESSAGES_ARE_ADDED: Long = 100
        private const val DEFAULT_MESSAGES_LIMIT = 100
        private const val MILLIES = 1000
    }
}
