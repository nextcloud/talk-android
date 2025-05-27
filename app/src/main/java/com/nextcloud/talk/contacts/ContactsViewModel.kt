/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.conversations.Conversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ContactsViewModel @Inject constructor(
    private val repository: ContactsRepository
) : ViewModel() {

    private val _contactsViewState = MutableStateFlow<ContactsUiState>(ContactsUiState.None)
    val contactsViewState: StateFlow<ContactsUiState> = _contactsViewState
    private val _roomViewState = MutableStateFlow<RoomUiState>(RoomUiState.None)
    val roomViewState: StateFlow<RoomUiState> = _roomViewState
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    private val shareTypes: MutableList<String> = mutableListOf(ShareType.User.shareType)
    val shareTypeList: List<String> = shareTypes
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive
    private val selectedParticipants = MutableStateFlow<List<AutocompleteUser>>(emptyList())
    val selectedParticipantsList: StateFlow<List<AutocompleteUser>> = selectedParticipants.asStateFlow()
    private val _isAddParticipantsView = MutableStateFlow(false)
    val isAddParticipantsView: StateFlow<Boolean> = _isAddParticipantsView

    private var hideAlreadyAddedParticipants: Boolean = false

    init {
        getContactsFromSearchParams()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectContact(contact: AutocompleteUser) {
        val updatedParticipants = selectedParticipants.value + contact
        selectedParticipants.value = updatedParticipants
    }

    fun deselectContact(contact: AutocompleteUser) {
        val updatedParticipants = selectedParticipants.value - contact
        selectedParticipants.value = updatedParticipants
    }

    fun updateSelectedParticipants(participants: List<AutocompleteUser>) {
        selectedParticipants.value = participants
    }
    fun setSearchActive(searchState: Boolean) {
        _isSearchActive.value = searchState
    }

    fun updateShareTypes(value: List<String>) {
        shareTypes.addAll(value)
    }

    fun updateIsAddParticipants(value: Boolean) {
        _isAddParticipantsView.value = value
    }

    fun hideAlreadyAddedParticipants(value: Boolean) {
        hideAlreadyAddedParticipants = value
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun getContactsFromSearchParams(query: String = "") {
        _contactsViewState.value = ContactsUiState.Loading
        viewModelScope.launch {
            try {
                val contacts = repository.getContacts(
                    if (query != "") query else searchQuery.value,
                    shareTypeList
                )
                val contactsList: MutableList<AutocompleteUser>? = contacts.ocs!!.data?.toMutableList()

                if (hideAlreadyAddedParticipants) {
                    contactsList?.removeAll(selectedParticipants.value)
                }

                _contactsViewState.value = ContactsUiState.Success(contactsList)
            } catch (exception: Exception) {
                _contactsViewState.value = ContactsUiState.Error(exception.message ?: "")
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun createRoom(roomType: String, sourceType: String?, userId: String, conversationName: String?) {
        viewModelScope.launch {
            try {
                val room = repository.createRoom(
                    roomType,
                    sourceType,
                    userId,
                    conversationName
                )

                val conversation: Conversation? = room.ocs?.data
                _roomViewState.value = RoomUiState.Success(conversation)
            } catch (exception: Exception) {
                _roomViewState.value = RoomUiState.Error(exception.message ?: "")
            }
        }
    }
    fun getImageUri(avatarId: String, requestBigSize: Boolean): String {
        return repository.getImageUri(avatarId, requestBigSize)
    }
}

sealed class ContactsUiState {
    data object None : ContactsUiState()
    data object Loading : ContactsUiState()
    data class Success(val contacts: List<AutocompleteUser>?) : ContactsUiState()
    data class Error(val message: String) : ContactsUiState()
}

sealed class RoomUiState {
    data object None : RoomUiState()
    data class Success(val conversation: Conversation?) : RoomUiState()
    data class Error(val message: String) : RoomUiState()
}
