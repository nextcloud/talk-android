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
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.generic.GenericMeta
import com.nextcloud.talk.repositories.conversations.ConversationsRepositoryImpl.Companion.STATUS_CODE_OK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ConversationCreationViewModel @Inject constructor(
    private val repository: ConversationCreationRepository
) : ViewModel() {
    private val _selectedParticipants = MutableStateFlow<List<AutocompleteUser>>(emptyList())
    val selectedParticipants: StateFlow<List<AutocompleteUser>> = _selectedParticipants
    private val _roomViewState = MutableStateFlow<RoomUIState>(RoomUIState.None)
    val roomViewState: StateFlow<RoomUIState> = _roomViewState

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

    private val _allowGuestsResult = MutableStateFlow<AllowGuestsUiState>(AllowGuestsUiState.None)
    val allowGuestsResult: StateFlow<AllowGuestsUiState> = _allowGuestsResult

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
                val participants: List<Conversation>? = participantsOverall.ocs?.data
                addParticipantsViewState.value = AddParticipantsUiState.Success(participants)
            } catch (exception: Exception) {
                addParticipantsViewState.value = AddParticipantsUiState.Error(exception.message ?: "")
            }
        }
    }

    fun allowGuests(token: String, allow: Boolean) {
        viewModelScope.launch {
            try {
                val response = repository.allowGuests(token, allow)
                val statusCode: Int = response.ocs?.meta?.statusCode!!
                val result = (statusCode == STATUS_CODE_OK)
                _allowGuestsResult.value = AllowGuestsUiState.Success(result)
            } catch (exception: Exception) {
                _allowGuestsResult.value = AllowGuestsUiState.Error(exception.message ?: "")
            }
        }
    }

    fun createRoomAndAddParticipants(
        roomType: ConversationEnums.ConversationType,
        conversationName: String,
        participants: Set<AutocompleteUser>,
        onRoomCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _roomViewState.value = RoomUIState.None
            try {
                val roomResult = repository.createRoom(roomType, conversationName)
                val conversation = roomResult.ocs?.data

                if (conversation != null) {
                    val token = conversation.token
                    if (token != null) {
                        try {
                            val allowGuestsResult = repository.allowGuests(token, isGuestsAllowed.value)
                            val statusCode: GenericMeta? = allowGuestsResult.ocs?.meta
                            val result = (statusCode?.statusCode == STATUS_CODE_OK)
                            if (result) {
                                _allowGuestsResult.value = AllowGuestsUiState.Success(result)
                                for (participant in participants) {
                                    if (participant.id != null) {
                                        val participantOverall = repository.addParticipants(
                                            token,
                                            participant.id!!,
                                            participant.source!!
                                        ).ocs?.data
                                        addParticipantsViewState.value =
                                            AddParticipantsUiState.Success(participantOverall)
                                    }
                                    onRoomCreated(token)
                                }
                            }
                        } catch (exception: Exception) {
                            _allowGuestsResult.value = AllowGuestsUiState.Error(exception.message ?: "")
                        }
                    }
                    _roomViewState.value = RoomUIState.Success(conversation)
                } else {
                    _roomViewState.value = RoomUIState.Error("Conversation is null")
                }
            } catch (e: Exception) {
                _roomViewState.value = RoomUIState.Error(e.message ?: "Unknown error")
                Log.e("ConversationCreationViewModel", "Error - ${e.message}")
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
                _roomViewState.value = RoomUIState.Success(conversation)
            } catch (exception: Exception) {
                _roomViewState.value = RoomUIState.Error(exception.message ?: "")
            }
        }
    }
}
sealed class AllowGuestsUiState {
    data object None : AllowGuestsUiState()
    data class Success(val result: Boolean) : AllowGuestsUiState()
    data class Error(val message: String) : AllowGuestsUiState()
}

sealed class RoomUIState {
    data object None : RoomUIState()
    data class Success(val conversation: Conversation?) : RoomUIState()
    data class Error(val message: String) : RoomUIState()
}

sealed class AddParticipantsUiState() {
    data object None : AddParticipantsUiState()
    data class Success(val participants: List<Conversation>?) : AddParticipantsUiState()
    data class Error(val message: String) : AddParticipantsUiState()
}
