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

package com.nextcloud.talk.newarch.features.conversationsList

import android.app.Application
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.R
import com.nextcloud.talk.R.drawable
import com.nextcloud.talk.R.string
import com.nextcloud.talk.controllers.bottomsheet.items.BasicListItemWithImage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.DeleteConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetConversationsUseCase
import com.nextcloud.talk.newarch.domain.usecases.LeaveConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.SetConversationFavoriteValueUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.ShareUtils
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf

class ConversationsListViewModel constructor(
        application: Application,
        private val getConversationsUseCase: GetConversationsUseCase,
        private val setConversationFavoriteValueUseCase: SetConversationFavoriteValueUseCase,
        private val leaveConversationUseCase: LeaveConversationUseCase,
        private val deleteConversationUseCase: DeleteConversationUseCase,
        private val conversationsRepository: ConversationsRepository,
        usersRepository: UsersRepository
) : BaseViewModel<ConversationsListView>(application) {

    var messageData: String? = null
    val searchQuery = MutableLiveData<String>()
    val currentUserLiveData: LiveData<UserNgEntity> = usersRepository.getActiveUserLiveData()
    val conversationsLiveData = Transformations.switchMap(currentUserLiveData) {
        loadConversations()
        conversationsRepository.getConversationsForUser(it.id!!)
    }

    fun leaveConversation(conversation: Conversation) {
        viewModelScope.launch {
            setConversationUpdateStatus(conversation, true)
        }

        leaveConversationUseCase.invoke(viewModelScope, parametersOf(
                currentUserLiveData.value,
                conversation
        ),
                object : UseCaseResponse<GenericOverall> {
                    override suspend fun onSuccess(result: GenericOverall) {
                        conversationsRepository.deleteConversation(
                                currentUserLiveData.value!!.id!!, conversation
                                .conversationId!!
                        )
                    }

                    override suspend fun onError(errorModel: ErrorModel?) {
                        messageData = errorModel?.getErrorMessage()
                        if (errorModel?.code == 400) {
                            // couldn't leave because we're last moderator
                        }
                        viewModelScope.launch {
                            setConversationUpdateStatus(conversation, false)
                        }
                    }
                })
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            setConversationUpdateStatus(conversation, true)
        }

        deleteConversationUseCase.invoke(viewModelScope, parametersOf(
                currentUserLiveData.value,
                conversation
        ),
                object : UseCaseResponse<GenericOverall> {
                    override suspend fun onSuccess(result: GenericOverall) {
                        conversationsRepository.deleteConversation(
                                currentUserLiveData.value!!.id!!, conversation
                                .conversationId!!
                        )
                    }

                    override suspend fun onError(errorModel: ErrorModel?) {
                        messageData = errorModel?.getErrorMessage()
                        viewModelScope.launch {
                            setConversationUpdateStatus(conversation, false)
                        }
                    }
                })

    }

    fun changeFavoriteValueForConversation(
            conversation: Conversation,
            favorite: Boolean
    ) {
        viewModelScope.launch {
            setConversationUpdateStatus(conversation, true)
        }

        setConversationFavoriteValueUseCase.invoke(viewModelScope, parametersOf(
                currentUserLiveData.value,
                conversation,
                favorite
        ),
                object : UseCaseResponse<GenericOverall> {
                    override suspend fun onSuccess(result: GenericOverall) {
                        conversationsRepository.setFavoriteValueForConversation(
                                currentUserLiveData.value!!.id!!,
                                conversation.conversationId!!, favorite
                        )
                    }

                    override suspend fun onError(errorModel: ErrorModel?) {
                        messageData = errorModel?.getErrorMessage()
                        viewModelScope.launch {
                            setConversationUpdateStatus(conversation, false)
                        }
                    }
                })
    }

    fun loadConversations() {
        getConversationsUseCase.invoke(viewModelScope, parametersOf(currentUserLiveData.value), object :
                UseCaseResponse<List<Conversation>> {
            override suspend fun onSuccess(result: List<Conversation>) {
                val mutableList = result.toMutableList()
                val internalUserId = currentUserLiveData.value!!.id
                mutableList.forEach {
                    it.internalUserId = internalUserId
                }

                conversationsRepository.saveConversationsForUser(
                        internalUserId!!,
                        mutableList)
                messageData = ""
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                messageData = errorModel?.getErrorMessage()
            }
        })
    }


    private suspend fun setConversationUpdateStatus(
            conversation: Conversation,
            value: Boolean
    ) {
        conversationsRepository.setChangingValueForConversation(
                currentUserLiveData.value!!.id!!, conversation
                .conversationId!!, value
        )
    }
}
