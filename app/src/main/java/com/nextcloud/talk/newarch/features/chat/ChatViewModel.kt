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
import android.text.TextUtils
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.bluelinelabs.conductor.Controller
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.MessagesRepository
import com.nextcloud.talk.newarch.domain.usecases.GetChatMessagesUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.local.models.User
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.toUser
import com.nextcloud.talk.newarch.local.models.toUserEntity
import com.nextcloud.talk.newarch.services.GlobalService
import com.nextcloud.talk.newarch.services.GlobalServiceInterface
import com.nextcloud.talk.newarch.utils.NetworkComponents
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import retrofit2.Response

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

    val messagesLiveData = Transformations.switchMap(futureStartingPoint) {futureStartingPoint ->
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
            globalService.getConversation(conversationToken, this@ChatViewModel)
        }
    }

    fun sendMessage(message: CharSequence) {

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

    private suspend fun pullPastMessagesForUserAndConversation(userNgEntity: UserNgEntity, conversation: Conversation) {
        if (userNgEntity.id == user.id && conversation.token == initConversation?.token && view != null) {
            val getChatMessagesUseCase = GetChatMessagesUseCase(networkComponents.getRepository(true, userNgEntity.toUser()), apiErrorHandler)
            val lastReadMessageId = conversation.lastReadMessageId
            getChatMessagesUseCase.invoke(viewModelScope, parametersOf(user, conversation.token, 0, lastReadMessageId, 1), object : UseCaseResponse<Response<ChatOverall>> {
                override suspend fun onSuccess(result: Response<ChatOverall>) {
                    val messages = result.body()?.ocs?.data
                    messages?.let {
                        for (message in it) {
                            message.activeUser = userNgEntity
                            message.internalConversationId = conversation.databaseId
                        }

                        messagesRepository.saveMessagesForConversation(it)
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
            val getChatMessagesUseCase = GetChatMessagesUseCase(networkComponents.getRepository(true, userNgEntity.toUser()), apiErrorHandler)
            var lastKnownMessageId = lastGivenMessage
            if (lastGivenMessage == 0) {
                lastKnownMessageId = conversation.lastReadMessageId.toInt()
            }
            getChatMessagesUseCase.invoke(viewModelScope, parametersOf(user, conversation.token, 1, lastKnownMessageId, 0), object : UseCaseResponse<Response<ChatOverall>> {
                override suspend fun onSuccess(result: Response<ChatOverall>) {
                    val messages = result.body()?.ocs?.data
                    messages?.let {
                        for (message in it) {
                            message.activeUser = userNgEntity
                            message.internalConversationId = conversation.databaseId
                        }

                       messagesRepository.saveMessagesForConversation(it)
                    }

                    if (result.code() == 200) {
                        val xChatLastGivenHeader: String? = result.headers().get("X-Chat-Last-Given")
                        if (xChatLastGivenHeader != null) {
                            pullFutureMessagesForUserAndConversation(userNgEntity, conversation, xChatLastGivenHeader.toInt())
                        }
                    } else {
                        pullFutureMessagesForUserAndConversation(userNgEntity, conversation, lastKnownMessageId)
                    }
                }

                override suspend fun onError(errorModel: ErrorModel?) {
                    pullFutureMessagesForUserAndConversation(userNgEntity, conversation)
                }
            })
        }
    }

}