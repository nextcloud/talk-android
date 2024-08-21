/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.contacts.AddParticipantsUiState
import com.nextcloud.talk.contacts.RoomUiState
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ConversationCreationViewModel @Inject constructor(
    private val repository: ConversationCreationRepository
) : ViewModel() {
    private val _selectedParticipants = MutableStateFlow<List<AutocompleteUser>>(emptyList())
    val selectedParticipants: StateFlow<List<AutocompleteUser>> = _selectedParticipants
    private val _roomViewState = MutableStateFlow<RoomUiState>(RoomUiState.None)
    val roomViewState: StateFlow<RoomUiState> = _roomViewState

    fun updateSelectedParticipants(participants: List<AutocompleteUser>) {
        _selectedParticipants.value = participants
    }

    private val _roomName = MutableStateFlow("")
    val roomName: StateFlow<String> = _roomName
    private val _conversationDescription = MutableStateFlow("")
    val conversationDescription: StateFlow<String> = _conversationDescription
    var isGuestsAllowed = mutableStateOf(false)
    var isConversationAvailableForRegisteredUsers = mutableStateOf(false)
    var openForGuestAppUsers = mutableStateOf(false)

    private val addParticipantsViewState = MutableStateFlow<AddParticipantsUiState>(AddParticipantsUiState.None)
    val addParticipantsUiState: StateFlow<AddParticipantsUiState> = addParticipantsViewState

    fun updateRoomName(roomName: String) {
        _roomName.value = roomName
    }

    fun updateConversationDescription(conversationDescription: String) {
        _conversationDescription.value = conversationDescription
    }

    fun renameConversation(roomToken: String) {
        viewModelScope.launch {
            try {
                repository.renameConversation(roomToken, roomName.value)
            } catch (e: Exception) {
                Log.d("ConversationCreationViewModel", "${e.message}")
            }
        }
    }
    fun setConversationDescription(roomToken: String) {
        viewModelScope.launch {
            try {
                repository.setConversationDescription(roomToken, conversationDescription.value)
            } catch (e: Exception) {
                Log.d("ConversationCreationViewModel", "${e.message}")
            }
        }
    }
    fun addParticipants(conversationToken: String?, userId: String, sourceType: String) {
        viewModelScope.launch {
            try {
                val participantsOverall = repository.addParticipants(conversationToken, userId, sourceType)
                val participants = participantsOverall.ocs?.data
                addParticipantsViewState.value = AddParticipantsUiState.Success(participants)
            } catch (exception: Exception) {
                addParticipantsViewState.value = AddParticipantsUiState.Error(exception.message ?: "")
            }
        }
    }

    fun getImageUri(avatarId: String, requestBigSize: Boolean): String {
        return repository.getImageUri(avatarId, requestBigSize)
    }

    fun createRoom(roomType: ConversationEnums.ConversationType?, conversationName: String?) {
        viewModelScope.launch {
            try {
                val room = repository.createRoom(
                    roomType,
                    conversationName
                )

                val conversation: Conversation? = room.ocs?.data
                _roomViewState.value = RoomUiState.Success(conversation)
            } catch (exception: Exception) {
                _roomViewState.value = RoomUiState.Error(exception.message ?: "")
            }
        }
    }

    fun allowGuests(token: String, allow: Boolean): ConversationCreationRepositoryImpl.AllowGuestsResult {
        return repository.allowGuests(token, allow)
    }
}
