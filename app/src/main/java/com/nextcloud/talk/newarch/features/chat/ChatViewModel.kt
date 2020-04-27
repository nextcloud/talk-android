/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.features.chat

import android.app.Application
import android.text.Editable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.bluelinelabs.conductor.Controller
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.MessagesRepository
import com.nextcloud.talk.newarch.domain.usecases.GetChatMessagesUseCase
import com.nextcloud.talk.newarch.domain.usecases.SendChatMessageUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.local.models.*
import com.nextcloud.talk.newarch.local.models.other.ChatMessageStatus
import com.nextcloud.talk.newarch.mvvm.BaseViewModel
import com.nextcloud.talk.newarch.services.GlobalService
import com.nextcloud.talk.newarch.services.GlobalServiceInterface
import com.nextcloud.talk.newarch.utils.NetworkComponents
import com.nextcloud.talk.newarch.utils.hashWithAlgorithm
import com.nextcloud.talk.utils.text.Spans
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import retrofit2.Response
import kotlin.collections.HashMap
import kotlin.collections.hashMapOf
import kotlin.collections.indices
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mutableListOf
import kotlin.collections.set

class ChatViewModel constructor(application: Application,
                                private val networkComponents: NetworkComponents,
                                private val apiErrorHandler: ApiErrorHandler,
                                private val conversationsRepository: ConversationsRepository,
                                private val messagesRepository: MessagesRepository,
                                private val globalService: GlobalService) : BaseViewModel<ChatView>(application), GlobalServiceInterface {
    lateinit var user: User
    val conversation: MutableLiveData<Conversation?> = MutableLiveData()
    var pastStartingPoint: Long = -1
    val futureStartingPoint: MutableLiveData<Long> = MutableLiveData()
    private var initConversation: Conversation? = null

    val messagesLiveData = Transformations.switchMap(futureStartingPoint) { futureStartingPoint ->
        conversation.value?.let {
            messagesRepository.getMessagesWithUserForConversationSince(it.databaseId!!, futureStartingPoint).map { chatMessagesList ->
                chatMessagesList.map { chatMessage ->
                    chatMessage.activeUser = user.toUserEntity()
                    chatMessage.parentMessage?.activeUser = chatMessage.activeUser
                    if (chatMessage.systemMessageType != null && chatMessage.systemMessageType != ChatMessage.SystemMessageType.DUMMY) {
                        ChatElement(chatMessage, ChatElementTypes.SYSTEM_MESSAGE)
                    } else {
                        ChatElement(chatMessage, ChatElementTypes.CHAT_MESSAGE)
                    }
                }
            }

        }
    }

    var conversationPassword: String? = null
    var view: Controller? = null


    fun init(user: User, conversationToken: String, conversationPassword: String?) {
        viewModelScope.launch {
            this@ChatViewModel.user = user
            this@ChatViewModel.initConversation = conversationsRepository.getConversationForUserWithToken(user.id!!, conversationToken)
            this@ChatViewModel.conversationPassword = conversationPassword
        }
    }

    fun sendMessage(editable: Editable, replyTo: Long?) {
        val messageParameters = hashMapOf<String, HashMap<String, String>>()
        val mentionSpans = editable.getSpans(
                0, editable.length,
                Spans.MentionChipSpan::class.java
        )
        var mentionSpan: Spans.MentionChipSpan
        val ids = mutableListOf<String>()
        for (i in mentionSpans.indices) {
            mentionSpan = mentionSpans[i]
            var mentionId = mentionSpan.id
            if (mentionId.contains(" ") || mentionId.startsWith("guest/")) {
                mentionId = "\"" + mentionId + "\""
            }

            val mentionNo = if (ids.contains("mentionId")) ids.indexOf("mentionId") + 1 else ids.size + 1
            val mentionReplace = "mention-${mentionSpan.type}$mentionNo"
            if (!ids.contains(mentionId)) {
                ids.add(mentionId)
                messageParameters[mentionReplace] = hashMapOf("type" to mentionSpan.type, "id" to mentionId.toString(), "name" to mentionSpan.label.toString())
            }

            val start = editable.getSpanStart(mentionSpan)
            editable.replace(start, editable.getSpanEnd(mentionSpan), "")
            editable.insert(start, "{$mentionReplace}")
        }

        if (user.hasSpreedFeatureCapability("chat-reference-id")) {
            ioScope.launch {
                val chatMessage = ChatMessage()
                val timestamp = System.currentTimeMillis()
                val sha1 = timestamp.toString().hashWithAlgorithm("SHA-1")
                conversation.value?.databaseId?.let { conversationDatabaseId ->
                    chatMessage.internalMessageId = sha1
                    chatMessage.internalConversationId = conversationDatabaseId
                    chatMessage.timestamp = timestamp / 1000
                    chatMessage.referenceId = sha1
                    chatMessage.replyable = false
                    // can also be "guests", but not now
                    chatMessage.actorId = user.userId
                    chatMessage.actorType = "users"
                    chatMessage.actorDisplayName = user.displayName
                    chatMessage.message = editable.toString()
                    chatMessage.systemMessageType = null
                    chatMessage.chatMessageStatus = ChatMessageStatus.PENDING_MESSAGE_SEND
                    if (replyTo != null) {
                        chatMessage.parentMessage = messagesRepository.getMessageForConversation(conversationDatabaseId, replyTo)
                    } else {
                        chatMessage.parentMessage = null
                    }
                    chatMessage.messageParameters = messageParameters
                    messagesRepository.saveMessagesForConversation(user, listOf(chatMessage), true)
                }
            }
        } else {
            val sendChatMessageUseCase = SendChatMessageUseCase(networkComponents.getRepository(false, user), ApiErrorHandler())
            // No reference id needed here
            initConversation?.let {
                sendChatMessageUseCase.invoke(viewModelScope, parametersOf(user, it.token, editable, replyTo, null), object : UseCaseResponse<Response<ChatOverall>> {
                    override suspend fun onSuccess(result: Response<ChatOverall>) {
                        // also do nothing, we did it - time to celebrate1
                    }

                    override suspend fun onError(errorModel: ErrorModel?) {
                        // Do nothing, error - tough luck
                    }
                })
            }
        }
    }

    fun joinConversation() {
        initConversation?.token?.let {
            viewModelScope.launch {
                globalService.getConversation(it, this@ChatViewModel)
            }
        }
    }

    fun leaveConversation() {
        conversation.value?.let {
            viewModelScope.launch {
                globalService.exitConversation(it.token!!, this@ChatViewModel)
            }

        }
    }

    override suspend fun gotConversationInfoForUser(userNgEntity: UserNgEntity, conversation: Conversation?, operationStatus: GlobalServiceInterface.OperationStatus) {
        if (operationStatus == GlobalServiceInterface.OperationStatus.STATUS_OK) {
            if (userNgEntity.id == user.id && conversation!!.token == initConversation?.token) {
                this.conversation.postValue(conversationsRepository.getConversationForUserWithToken(user.id!!, conversation.token!!))
                conversation.token?.let { conversationToken ->
                    globalService.joinConversation(conversationToken, conversationPassword, this)
                }
            }
        }
    }

    override suspend fun joinedConversationForUser(userNgEntity: UserNgEntity, conversation: Conversation?, operationStatus: GlobalServiceInterface.OperationStatus) {
        if (operationStatus == GlobalServiceInterface.OperationStatus.STATUS_OK) {
            if (userNgEntity.id == user.id && conversation!!.token == initConversation?.token) {
                pullPastMessagesForUserAndConversation(userNgEntity, conversation)
            }
        }
    }

    override suspend fun leftConversationForUser(user: User, conversation: Conversation?, operationStatus: GlobalServiceInterface.OperationStatus) {
        // We left the conversation
    }

    private suspend fun pullPastMessagesForUserAndConversation(userNgEntity: UserNgEntity, conversation: Conversation) {
        if (userNgEntity.id == user.id && conversation.token == initConversation?.token && view != null) {
            val getChatMessagesUseCase = GetChatMessagesUseCase(networkComponents.getRepository(true, userNgEntity.toUser()), ApiErrorHandler())
            val lastReadMessageId = conversation.lastReadMessageId
            getChatMessagesUseCase.invoke(viewModelScope, parametersOf(user, conversation.token, 0, lastReadMessageId, 1), object : UseCaseResponse<Response<ChatOverall>> {
                override suspend fun onSuccess(result: Response<ChatOverall>) {
                    val messages = result.body()?.ocs?.data
                    messages?.let {
                        for (message in it) {
                            message.internalConversationId = conversation.databaseId
                        }

                        messagesRepository.saveMessagesForConversation(user, it, false)
                    }

                    val xChatLastGivenHeader: String? = result.headers().get("X-Chat-Last-Given")
                    if (xChatLastGivenHeader != null) {
                        pastStartingPoint = xChatLastGivenHeader.toLong()
                    }

                    futureStartingPoint.postValue(pastStartingPoint)
                    pullFutureMessagesForUserAndConversation(userNgEntity, conversation, pastStartingPoint.toInt())
                }

                override suspend fun onError(errorModel: ErrorModel?) {
                    // What to do here
                }
            })
        }
    }

    suspend fun pullFutureMessagesForUserAndConversation(userNgEntity: UserNgEntity, conversation: Conversation, lastGivenMessage: Int = 0) {
        if (userNgEntity.id == user.id && conversation.token == initConversation?.token && view != null) {
            val getChatMessagesUseCase = GetChatMessagesUseCase(networkComponents.getRepository(true, userNgEntity.toUser()), ApiErrorHandler())
            var lastKnownMessageId = lastGivenMessage
            if (lastGivenMessage == 0) {
                lastKnownMessageId = conversation.lastReadMessageId.toInt()
            }
            getChatMessagesUseCase.invoke(viewModelScope, parametersOf(user, conversation.token, 1, lastKnownMessageId, 0), object : UseCaseResponse<Response<ChatOverall>> {
                override suspend fun onSuccess(result: Response<ChatOverall>) {
                    val messages = result.body()?.ocs?.data
                    messages?.let {
                        for (message in it) {
                            message.internalConversationId = conversation.databaseId
                        }

                        messagesRepository.saveMessagesForConversation(user, it, false)
                    }

                        val xChatLastGivenHeader: String? = result.headers().get("X-Chat-Last-Given")
                        if (xChatLastGivenHeader != null) {
                            pullFutureMessagesForUserAndConversation(userNgEntity, conversation, xChatLastGivenHeader.toInt())
                        }
                }

                override suspend fun onError(errorModel: ErrorModel?) {
                }
            })
        }
    }

}