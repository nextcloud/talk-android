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
import android.graphics.drawable.Drawable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.api.get
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.usecases.DeleteConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetConversationsUseCase
import com.nextcloud.talk.newarch.domain.usecases.LeaveConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.SetConversationFavoriteValueUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.services.GlobalService
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import java.util.concurrent.locks.ReentrantLock

class ConversationsListViewModel constructor(
        application: Application,
        private val getConversationsUseCase: GetConversationsUseCase,
        private val setConversationFavoriteValueUseCase: SetConversationFavoriteValueUseCase,
        private val leaveConversationUseCase: LeaveConversationUseCase,
        private val deleteConversationUseCase: DeleteConversationUseCase,
        private val conversationsRepository: ConversationsRepository,
        val globalService: GlobalService
) : BaseViewModel<ConversationsListView>(application) {

    private val conversationsLoadingLock = ReentrantLock()

    var messageData: String? = null
    val networkStateLiveData: MutableLiveData<ConversationsListViewNetworkState> = MutableLiveData(ConversationsListViewNetworkState.LOADING)
    val avatar: MutableLiveData<Drawable> = MutableLiveData(DisplayUtils.getRoundedDrawable(context.getDrawable(R.drawable.ic_settings_white_24dp)))
    val conversationsLiveData = Transformations.switchMap(globalService.currentUserLiveData) {
        if (networkStateLiveData.value != ConversationsListViewNetworkState.LOADING) {
            networkStateLiveData.postValue(ConversationsListViewNetworkState.LOADING)
        }
        loadConversations()
        loadAvatar()
        conversationsRepository.getConversationsForUser(it.id!!)
    }

    fun leaveConversation(conversation: Conversation) {
        viewModelScope.launch {
            setConversationUpdateStatus(conversation, true)
        }

        leaveConversationUseCase.invoke(viewModelScope, parametersOf(
                globalService.currentUserLiveData.value,
                conversation
        ),
                object : UseCaseResponse<GenericOverall> {
                    override suspend fun onSuccess(result: GenericOverall) {
                        conversationsRepository.deleteConversation(
                                globalService.currentUserLiveData.value!!.id!!, conversation
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
                globalService.currentUserLiveData.value,
                conversation
        ),
                object : UseCaseResponse<GenericOverall> {
                    override suspend fun onSuccess(result: GenericOverall) {
                        conversationsRepository.deleteConversation(
                                globalService.currentUserLiveData.value!!.id!!, conversation
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
                globalService.currentUserLiveData.value,
                conversation,
                favorite
        ),
                object : UseCaseResponse<GenericOverall> {
                    override suspend fun onSuccess(result: GenericOverall) {
                        conversationsRepository.setFavoriteValueForConversation(
                                globalService.currentUserLiveData.value!!.id!!,
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

    fun loadAvatar() {
        val operationUser = globalService.currentUserLiveData.value

        operationUser?.let {
            viewModelScope.launch {
                val url = ApiUtils.getUrlForAvatarWithNameAndPixels(it.baseUrl, it.userId, 256)
                val drawable = Coil.get((url)) {
                    addHeader("Authorization", it.getCredentials())
                    transformations(CircleCropTransformation())
                }
                avatar.postValue(drawable)
            }
        }
    }

    fun loadConversations() {
        if (conversationsLoadingLock.tryLock()) {
            getConversationsUseCase.invoke(viewModelScope, parametersOf(globalService.currentUserLiveData.value), object :
                    UseCaseResponse<List<Conversation>> {
                override suspend fun onSuccess(
                        result: List<Conversation>) {
                    networkStateLiveData.postValue(ConversationsListViewNetworkState.LOADED)
                    val mutableList = result.toMutableList()
                    val internalUserId = globalService.currentUserLiveData.value!!.id
                    mutableList.forEach {
                        it.internalUserId = internalUserId
                    }

                    conversationsRepository.saveConversationsForUser(
                            internalUserId!!,
                            mutableList)
                    messageData = ""
                    conversationsLoadingLock.unlock()
                }

                override suspend fun onError(errorModel: ErrorModel?) {
                    messageData = errorModel?.getErrorMessage()
                    networkStateLiveData.postValue(ConversationsListViewNetworkState.FAILED)
                    conversationsLoadingLock.unlock()
                }
            })
        }
    }


    private suspend fun setConversationUpdateStatus(
            conversation: Conversation,
            value: Boolean
    ) {
        conversationsRepository.setChangingValueForConversation(
                globalService.currentUserLiveData.value!!.id!!, conversation
                .conversationId!!, value
        )
    }
}
