/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.newarch.utils

import androidx.lifecycle.LiveData
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.GetConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.JoinConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.koin.core.KoinComponent
import org.koin.core.parameter.parametersOf
import java.net.CookieManager

class ConversationsManager constructor(usersRepository: UsersRepository,
                                       cookieManager: CookieManager,
                                       okHttpClient: OkHttpClient,
                                       private val conversationsRepository: ConversationsRepository,
                                       private val joinConversationUseCase: JoinConversationUseCase,
                                       private val getConversationUseCase: GetConversationUseCase) : KoinComponent {
    private val applicationScope = CoroutineScope(Dispatchers.Default)
    private val previousUser: UserNgEntity? = null
    private val currentUserLiveData: LiveData<UserNgEntity> = usersRepository.getActiveUserLiveData()
    private var currentConversation: Conversation? = null

    init {
        currentUserLiveData.observeForever { user ->
            if (user.id != previousUser?.id) {
                cookieManager.cookieStore.removeAll()
                //okHttpClient.dispatcher().cancelAll()
                currentConversation = null
            }
        }
    }

    suspend fun getConversation(conversationToken: String, conversationsManagerInterface: ConversationsManagerInterface) {
        val currentUser = currentUserLiveData.value
        getConversationUseCase.invoke(applicationScope, parametersOf(
                currentUser,
                conversationToken
        ),
                object : UseCaseResponse<RoomOverall> {
                    override suspend fun onSuccess(result: RoomOverall) {
                        currentUser?.let {
                            conversationsRepository.saveConversationsForUser(it.id!!, listOf(result.ocs.data))
                            conversationsManagerInterface.gotConversationInfoForUser(it, result.ocs.data, ConversationsManagerInterface.OperationStatus.STATUS_OK)
                        }
                    }

                    override suspend fun onError(errorModel: ErrorModel?) {
                        currentUser?.let {
                            conversationsManagerInterface.gotConversationInfoForUser(it, null, ConversationsManagerInterface.OperationStatus.STATUS_FAILED)
                        }
                    }
                })
    }

    suspend fun joinConversation(conversationToken: String, conversationPassword: String?, conversationsManagerInterface: ConversationsManagerInterface) {
        val currentUser = currentUserLiveData.value
        joinConversationUseCase.invoke(applicationScope, parametersOf(
                currentUser,
                conversationToken,
                conversationPassword
        ),
                object : UseCaseResponse<RoomOverall> {
                    override suspend fun onSuccess(result: RoomOverall) {
                        currentUser?.let {
                            conversationsRepository.saveConversationsForUser(it.id!!, listOf(result.ocs.data))
                            currentConversation = conversationsRepository.getConversationForUserWithToken(it.id!!, result.ocs!!.data!!.token!!)
                            conversationsManagerInterface.joinedConversationForUser(it, currentConversation, ConversationsManagerInterface.OperationStatus.STATUS_OK)
                        }
                    }

                    override suspend fun onError(errorModel: ErrorModel?) {
                        currentUser?.let {
                            conversationsManagerInterface.joinedConversationForUser(it, currentConversation, ConversationsManagerInterface.OperationStatus.STATUS_FAILED)
                        }
                    }
                })
    }
}