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

package com.nextcloud.talk.newarch.services

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.MessagesRepository
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.ExitConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.JoinConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.SendChatMessageUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.other.ChatMessageStatus
import com.nextcloud.talk.newarch.local.models.toUser
import com.nextcloud.talk.newarch.utils.NetworkComponents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.core.KoinComponent
import org.koin.core.parameter.parametersOf
import retrofit2.Response
import java.net.CookieManager
import java.util.concurrent.ConcurrentHashMap

class GlobalService constructor(usersRepository: UsersRepository,
                                cookieManager: CookieManager,
                                private val okHttpClient: OkHttpClient,
                                private val apiErrorHandler: ApiErrorHandler,
                                private val conversationsRepository: ConversationsRepository,
                                private val messagesRepository: MessagesRepository,
                                private val networkComponents: NetworkComponents,
                                private val joinConversationUseCase: JoinConversationUseCase,
                                private val getConversationUseCase: GetConversationUseCase) : KoinComponent {
    private val applicationScope = CoroutineScope(Dispatchers.Default)
    private val previousUser: UserNgEntity? = null
    val currentUserLiveData: LiveData<UserNgEntity?> = usersRepository.getActiveUserLiveData()
    private var currentConversation: MutableLiveData<Conversation?> = MutableLiveData<Conversation?>(null)
    private val pendingMessages: LiveData<List<ChatMessage>> = Transformations.switchMap(currentConversation) { conversation ->
        conversation?.let {
            messagesRepository.getPendingMessagesForConversation(it.databaseId!!)
        }
    }

    private var messagesOperations: ConcurrentHashMap<String, Pair<ChatMessage, Int>> = ConcurrentHashMap<String, Pair<ChatMessage, Int>>()

    init {
        pendingMessages.observeForever { chatMessages ->
            for (chatMessage in chatMessages) {
                if (!messagesOperations.contains(chatMessage.internalMessageId) || messagesOperations[chatMessage.internalMessageId]?.first != chatMessage) {
                    messagesOperations[chatMessage.internalMessageId!!] = Pair(chatMessage, 0)
                    applicationScope.launch {
                        sendMessage(chatMessage)
                    }
                }
            }
        }

        currentUserLiveData.observeForever { user ->
            user?.let {
                if (it.id != previousUser?.id) {
                    cookieManager.cookieStore.removeAll()
                    currentConversation.postValue(null)
                }
            }
        }
    }

    suspend fun sendMessage(chatMessage: ChatMessage) {
        val currentUser = currentUserLiveData.value?.toUser()
        val conversation = currentConversation.value
        val operationChatMessage = messagesOperations[chatMessage.internalMessageId]

        operationChatMessage?.let { pair ->
            conversation?.let { conversation ->
                if (pair.second == 4) {
                    messagesOperations.remove(pair.first.internalMessageId)
                    messagesRepository.updateMessageStatus(ChatMessageStatus.FAILED.ordinal, conversation.databaseId!!, pair.first.jsonMessageId!!)
                } else {
                    currentUser?.let { user ->
                        if (chatMessage.internalConversationId == conversation.databaseId && conversation.databaseUserId == currentUser.id) {
                            val sendChatMessageUseCase = SendChatMessageUseCase(networkComponents.getRepository(false, user), apiErrorHandler)
                            sendChatMessageUseCase.invoke(applicationScope, parametersOf(user, conversation.token, chatMessage.message, chatMessage.parentMessage?.jsonMessageId, chatMessage.referenceId), object : UseCaseResponse<Response<ChatOverall>> {
                                override suspend fun onSuccess(result: Response<ChatOverall>) {
                                    messagesOperations.remove(pair.first.internalMessageId!!)
                                    messagesRepository.updateMessageStatus(ChatMessageStatus.SENT.ordinal, conversation.databaseId!!, pair.first.jsonMessageId!!)
                                }

                                override suspend fun onError(errorModel: ErrorModel?) {
                                    val newValue = operationChatMessage.second + 1
                                    messagesOperations[pair.first.internalMessageId!!] = Pair(chatMessage, newValue)
                                    sendMessage(chatMessage)
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    suspend fun exitConversation(conversationToken: String, globalServiceInterface: GlobalServiceInterface) {
        val currentUser = currentUserLiveData.value!!.toUser()
        val exitConversationUseCase = ExitConversationUseCase(networkComponents.getRepository(true, currentUser), apiErrorHandler)
        exitConversationUseCase.invoke(applicationScope, parametersOf(currentUser, conversationToken), object: UseCaseResponse<GenericOverall> {
            override suspend fun onSuccess(result: GenericOverall) {
                globalServiceInterface.leftConversationForUser(currentUser, currentConversation.value, GlobalServiceInterface.OperationStatus.STATUS_OK)
                withContext(Dispatchers.Main) {
                    currentConversation.postValue(null)
                }
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                globalServiceInterface.leftConversationForUser(currentUser, currentConversation.value, GlobalServiceInterface.OperationStatus.STATUS_FAILED)
            }
        })
    }
    suspend fun getConversation(conversationToken: String, globalServiceInterface: GlobalServiceInterface) {
        val currentUser = currentUserLiveData.value
        val getConversationUseCase = GetConversationUseCase(networkComponents.getRepository(true, currentUser!!.toUser()), apiErrorHandler)
        getConversationUseCase.invoke(applicationScope, parametersOf(
                currentUser,
                conversationToken
        ),
                object : UseCaseResponse<ConversationOverall> {
                    override suspend fun onSuccess(result: ConversationOverall) {
                        currentUser?.let {
                            conversationsRepository.saveConversationsForUser(it.id, listOf(result.ocs.data), false)
                            globalServiceInterface.gotConversationInfoForUser(it, result.ocs.data, GlobalServiceInterface.OperationStatus.STATUS_OK)

                        }
                    }

                    override suspend fun onError(errorModel: ErrorModel?) {
                        currentUser?.let {
                            globalServiceInterface.gotConversationInfoForUser(it, null, GlobalServiceInterface.OperationStatus.STATUS_FAILED)
                        }
                    }

                })
    }

    suspend fun joinConversation(conversationToken: String, conversationPassword: String?, globalServiceInterface: GlobalServiceInterface) {
        val currentUser = currentUserLiveData.value
        val joinConversationUseCase = JoinConversationUseCase(networkComponents.getRepository(true, currentUser!!.toUser()), apiErrorHandler)
        joinConversationUseCase.invoke(applicationScope, parametersOf(
                currentUser,
                conversationToken,
                conversationPassword
        ),
                object : UseCaseResponse<ConversationOverall> {
                    override suspend fun onSuccess(result: ConversationOverall) {
                        currentUser?.let {
                            conversationsRepository.saveConversationsForUser(it.id, listOf(result.ocs.data), false)
                            withContext(Dispatchers.Main) {
                                currentConversation.postValue(conversationsRepository.getConversationForUserWithToken(it.id, result.ocs!!.data!!.token!!))
                            }
                            globalServiceInterface.joinedConversationForUser(it, currentConversation.value, GlobalServiceInterface.OperationStatus.STATUS_OK)
                        }
                    }

                    override suspend fun onError(errorModel: ErrorModel?) {
                        currentUser?.let {
                            globalServiceInterface.joinedConversationForUser(it, currentConversation.value, GlobalServiceInterface.OperationStatus.STATUS_FAILED)
                        }
                    }
                })
    }
}