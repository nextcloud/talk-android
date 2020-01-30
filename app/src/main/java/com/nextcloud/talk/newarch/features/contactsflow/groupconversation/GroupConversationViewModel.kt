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

package com.nextcloud.talk.newarch.features.contactsflow.groupconversation

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.models.json.conversations.ConversationOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.domain.usecases.CreateConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.SetConversationPasswordUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.features.contactsflow.ContactsViewOperationState
import com.nextcloud.talk.newarch.features.contactsflow.ContactsViewOperationStateWrapper
import com.nextcloud.talk.newarch.features.conversationslist.ConversationsListView
import com.nextcloud.talk.newarch.services.GlobalService
import org.koin.core.parameter.parametersOf

class GroupConversationViewModel constructor(
        application: Application,
        private val createConversationUseCase: CreateConversationUseCase,
        private val setPasswordUseCase: SetConversationPasswordUseCase,
        val globalService: GlobalService
) : BaseViewModel<ConversationsListView>(application) {
    private val _operationState = MutableLiveData(ContactsViewOperationStateWrapper(ContactsViewOperationState.WAITING, null, null))
    val operationState: LiveData<ContactsViewOperationStateWrapper> = _operationState.distinctUntilChanged()

    fun createConversation(conversationType: Int, conversationName: String, conversationPassword: String? = null) {
        _operationState.postValue(ContactsViewOperationStateWrapper(ContactsViewOperationState.PROCESSING, null, null))
        createConversationUseCase.invoke(viewModelScope, parametersOf(globalService.currentUserLiveData.value, conversationType, null, null, conversationName), object : UseCaseResponse<ConversationOverall> {
            override suspend fun onSuccess(result: ConversationOverall) {
                result.ocs.data.token?.let { token ->
                    if (!conversationPassword.isNullOrEmpty()) {
                        setPasswordForConversation(token, conversationPassword)
                    } else {
                        _operationState.postValue(ContactsViewOperationStateWrapper(ContactsViewOperationState.OK, null, token))
                    }
                } ?: run {
                    _operationState.postValue(ContactsViewOperationStateWrapper(ContactsViewOperationState.CONVERSATION_CREATED_WITH_MISSING_TOKEN, null, null))
                }
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                _operationState.postValue(ContactsViewOperationStateWrapper(ContactsViewOperationState.CONVERSATION_CREATION_FAILED, errorModel?.getErrorMessage(), null))
            }
        })
    }

    private fun setPasswordForConversation(conversationToken: String, conversationPassword: String) {
        setPasswordUseCase.invoke(viewModelScope, parametersOf(globalService.currentUserLiveData.value, conversationToken, conversationPassword), object : UseCaseResponse<GenericOverall> {
            override suspend fun onSuccess(result: GenericOverall) {
                _operationState.postValue(ContactsViewOperationStateWrapper(ContactsViewOperationState.OK, null, conversationToken))
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                _operationState.postValue(ContactsViewOperationStateWrapper(ContactsViewOperationState.CONVERSATION_PASSWORD_NOT_SET, errorModel?.getErrorMessage(), conversationToken))
            }
        })
    }
}