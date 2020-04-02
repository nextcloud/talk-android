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

package com.nextcloud.talk.newarch.features.contactsflow.contacts

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationOverall
import com.nextcloud.talk.models.json.participants.AddParticipantOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.domain.usecases.AddParticipantToConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.CreateConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetContactsUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.features.contactsflow.ContactsViewOperationState
import com.nextcloud.talk.newarch.features.contactsflow.ContactsViewOperationStateWrapper
import com.nextcloud.talk.newarch.features.contactsflow.ParticipantElement
import com.nextcloud.talk.newarch.features.conversationsList.ConversationsListView
import com.nextcloud.talk.newarch.local.models.canUserCreateGroupConversations
import com.nextcloud.talk.newarch.services.GlobalService
import kotlinx.coroutines.runBlocking
import org.koin.core.parameter.parametersOf

class ContactsViewModel constructor(
        application: Application,
        private val getContactsUseCase: GetContactsUseCase,
        private val createConversationUseCase: CreateConversationUseCase,
        private val addParticipantToConversationUseCase: AddParticipantToConversationUseCase,
        val globalService: GlobalService
) : BaseViewModel<ConversationsListView>(application) {
    private val selectedParticipants = mutableListOf<ParticipantElement>()
    val selectedParticipantsLiveData: MutableLiveData<List<ParticipantElement>> = MutableLiveData()
    private val _contacts: MutableLiveData<List<ParticipantElement>> = MutableLiveData()
    val contactsLiveData: LiveData<List<ParticipantElement>> = _contacts
    private val _operationState = MutableLiveData(ContactsViewOperationStateWrapper(ContactsViewOperationState.WAITING, null, null))
    val operationState: LiveData<ContactsViewOperationStateWrapper> = _operationState.distinctUntilChanged()

    private val filterMutableLiveData: MutableLiveData<CharSequence?> = MutableLiveData(null)
    val filterLiveData: LiveData<CharSequence?> = filterMutableLiveData

    private var conversationToken: String? = null
    private var groupConversation: Boolean = false
    private var initialized = false

    fun initialize(conversationToken: String?, groupConversation: Boolean) {
        if (!initialized || conversationToken != this.conversationToken || groupConversation != this.groupConversation) {
            this.conversationToken = conversationToken
            this.groupConversation = groupConversation
            initialized = true
        }
    }

    fun setSearchQuery(query: CharSequence?) {
        filterMutableLiveData.value = query
        loadContacts()
    }

    fun selectParticipant(participant: Participant) {
        selectedParticipants.add(ParticipantElement(participant, ParticipantElementType.PARTICIPANT_SELECTED.ordinal))
        selectedParticipantsLiveData.postValue(selectedParticipants)
    }

    fun unselectParticipant(participant: Participant) {
        selectedParticipants.remove(ParticipantElement(participant, ParticipantElementType.PARTICIPANT_SELECTED.ordinal))
        selectedParticipantsLiveData.postValue(selectedParticipants)
    }

    fun createConversation(conversationType: Int, invite: String?, conversationName: String? = null, participants: List<Participant> = listOf()) {
        _operationState.postValue(ContactsViewOperationStateWrapper(ContactsViewOperationState.PROCESSING, null, null))
        createConversationUseCase.invoke(viewModelScope, parametersOf(globalService.currentUserLiveData.value, conversationType, invite, null, conversationName), object : UseCaseResponse<ConversationOverall> {
            override suspend fun onSuccess(result: ConversationOverall) {
                if (result.ocs.data.type == Conversation.ConversationType.ONE_TO_ONE_CONVERSATION || participants.isEmpty()) {
                    result.ocs.data.token?.let {
                        _operationState.postValue(ContactsViewOperationStateWrapper(ContactsViewOperationState.OK, null, it))
                    } ?: run {
                        _operationState.postValue(ContactsViewOperationStateWrapper(ContactsViewOperationState.CONVERSATION_CREATION_FAILED, null, null))
                    }
                } else {
                    result.ocs.data.token?.let { token ->
                        addParticipants(token, participants)
                    }
                }
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                _operationState.postValue(ContactsViewOperationStateWrapper(ContactsViewOperationState.CONVERSATION_CREATION_FAILED, errorModel?.getErrorMessage(), null))
            }


        })
    }

    fun addParticipants(conversationToken: String, participants: List<Participant>) {
        _operationState.postValue(ContactsViewOperationStateWrapper(ContactsViewOperationState.PROCESSING, null, null))
        for (participant in participants) {
            runBlocking {
                addParticipantToConversationUseCase.invoke(viewModelScope, parametersOf(globalService.currentUserLiveData.value, conversationToken, participant.userId, participant.source), object : UseCaseResponse<AddParticipantOverall> {
                    override suspend fun onSuccess(result: AddParticipantOverall) {

                    }

                    override suspend fun onError(errorModel: ErrorModel?) {
                        // handle errors
                    }

                })
            }
        }

        _operationState.postValue(ContactsViewOperationStateWrapper(ContactsViewOperationState.OK, null, conversationToken))
    }

    private fun loadContacts() {
        val searchQuery: String = if (filterMutableLiveData.value.isNullOrBlank()) "" else filterMutableLiveData.value.toString()
        val user = globalService.currentUserLiveData.value
        user?.let { operationUser ->
            getContactsUseCase.invoke(viewModelScope, parametersOf(operationUser, groupConversation, searchQuery, conversationToken), object :
                    UseCaseResponse<List<Participant>> {
                override suspend fun onSuccess(result: List<Participant>) {
                    val sortPriority = mapOf("users" to 0, "groups" to 1, "emails" to 2, "circles" to 3)

                    val sortedList = result.sortedWith(compareBy({
                        sortPriority[it.source]
                    }, {
                        it.displayName!!.toLowerCase()
                    }))

                    val selectedUserIds = selectedParticipants.map { (it.data as Participant).userId }


                    val participantElementsList: MutableList<ParticipantElement> = sortedList.map {
                        if (it.userId in selectedUserIds) {
                            it.selected = true
                        }

                        ParticipantElement(it, ParticipantElementType.PARTICIPANT.ordinal)
                    } as MutableList<ParticipantElement>


                    if (operationUser.canUserCreateGroupConversations() && conversationToken.isNullOrEmpty() && filterLiveData.value.isNullOrEmpty()) {
                        val newGroupElement = ParticipantElement(Pair(context.getString(R.string.nc_new_group), R.drawable.ic_people_group_white_24px), ParticipantElementType.PARTICIPANT_NEW_GROUP.ordinal)
                        participantElementsList.add(0, newGroupElement)
                    }

                    _contacts.postValue(participantElementsList)
                }

                override suspend fun onError(errorModel: ErrorModel?) {
                    _operationState.postValue(ContactsViewOperationStateWrapper(ContactsViewOperationState.LOADING_FAILED, errorModel?.getErrorMessage(), conversationToken))

                }
            })
        }
    }
}
