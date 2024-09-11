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
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.generic.GenericMeta
import com.nextcloud.talk.repositories.conversations.ConversationsRepositoryImpl.Companion.STATUS_CODE_OK
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

class ConversationCreationViewModel @Inject constructor(
    private val repository: ConversationCreationRepository,
    private val userManager: UserManager
) : ViewModel() {
    private val _selectedParticipants = MutableStateFlow<List<AutocompleteUser>>(emptyList())
    val selectedParticipants: StateFlow<List<AutocompleteUser>> = _selectedParticipants
    private val roomViewState = MutableStateFlow<RoomUIState>(RoomUIState.None)

    private val _uploadState = MutableStateFlow<UploadAvatarState>(UploadAvatarState.Loading)
    val uploadState: StateFlow<UploadAvatarState> = _uploadState

    private val _deleteState = MutableStateFlow<DeleteAvatarState>(DeleteAvatarState.Loading)
    val deleteState: StateFlow<DeleteAvatarState> = _deleteState

    private val _currentUser = userManager.currentUser.blockingGet()
    val currentUser: User = _currentUser

    fun updateSelectedParticipants(participants: List<AutocompleteUser>) {
        _selectedParticipants.value = participants
    }

    private val _roomName = MutableStateFlow("")
    val roomName: StateFlow<String> = _roomName
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password
    private val _conversationDescription = MutableStateFlow("")
    val conversationDescription: StateFlow<String> = _conversationDescription
    var isGuestsAllowed = mutableStateOf(false)
    var isConversationAvailableForRegisteredUsers = mutableStateOf(false)
    var openForGuestAppUsers = mutableStateOf(false)
    private val addParticipantsViewState = MutableStateFlow<AddParticipantsUiState>(AddParticipantsUiState.None)
    private val allowGuestsResult = MutableStateFlow<AllowGuestsUiState>(AllowGuestsUiState.None)
    fun updateRoomName(roomName: String) {
        _roomName.value = roomName
    }

    fun updatePassword(password: String) {
        _password.value = password
    }

    fun updateConversationDescription(conversationDescription: String) {
        _conversationDescription.value = conversationDescription
    }

    fun createRoomAndAddParticipants(
        roomType: String,
        conversationName: String,
        participants: Set<AutocompleteUser>,
        onRoomCreated: (String) -> Unit
    ) {
        val scope = when {
            isConversationAvailableForRegisteredUsers.value && !openForGuestAppUsers.value -> 1
            isConversationAvailableForRegisteredUsers.value && openForGuestAppUsers.value -> 2
            else -> 0
        }
        viewModelScope.launch {
            roomViewState.value = RoomUIState.None
            try {
                val roomResult = repository.createRoom(roomType, conversationName)
                val conversation = roomResult.ocs?.data

                if (conversation != null) {
                    val token = conversation.token
                    if (token != null) {
                        try {
                            repository.setConversationDescription(
                                token,
                                _conversationDescription.value
                            )
                            val allowGuestResultOverall = repository.allowGuests(token, isGuestsAllowed.value)
                            val statusCode: GenericMeta? = allowGuestResultOverall.ocs?.meta
                            val result = (statusCode?.statusCode == STATUS_CODE_OK)
                            if (result) {
                                allowGuestsResult.value = AllowGuestsUiState.Success(result)
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
                                }
                            }
                            if (_password.value.isNotEmpty()) {
                                repository.setPassword(token, _password.value)
                            }
                            repository.openConversation(token, scope)
                            onRoomCreated(token)
                        } catch (exception: Exception) {
                            allowGuestsResult.value = AllowGuestsUiState.Error(exception.message ?: "")
                        }
                    }
                    roomViewState.value = RoomUIState.Success(conversation)
                } else {
                    roomViewState.value = RoomUIState.Error("Conversation is null")
                }
            } catch (e: Exception) {
                roomViewState.value = RoomUIState.Error(e.message ?: "Unknown error")
                Log.e("ConversationCreationViewModel", "Error - ${e.message}")
            }
        }
    }

    fun uploadConversationAvatar(file: File, roomToken: String) {
        viewModelScope.launch {
            try {
                val response = repository.uploadConversationAvatar(file, roomToken)
                _uploadState.value = UploadAvatarState.Success(response)
            } catch (e: Exception) {
                _uploadState.value = UploadAvatarState.Error(e)
            }
        }
    }

    fun deleteConversationAvatar(roomToken: String) {
        viewModelScope.launch {
            try {
                val result = repository.deleteConversationAvatar(roomToken)
                _deleteState.value = DeleteAvatarState.Success(result)
            } catch (e: Exception) {
                _deleteState.value = DeleteAvatarState.Error(e)
            }
        }
    }

    fun getImageUri(avatarId: String, requestBigSize: Boolean): String {
        return repository.getImageUri(avatarId, requestBigSize)
    }

    fun createRoom(roomType: String, conversationName: String?) {
        viewModelScope.launch {
            try {
                val room = repository.createRoom(
                    roomType,
                    conversationName
                )

                val conversation: Conversation? = room.ocs?.data
                roomViewState.value = RoomUIState.Success(conversation)
            } catch (exception: Exception) {
                roomViewState.value = RoomUIState.Error(exception.message ?: "")
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

sealed class AddParticipantsUiState {
    data object None : AddParticipantsUiState()
    data class Success(val participants: List<Conversation>?) : AddParticipantsUiState()
    data class Error(val message: String) : AddParticipantsUiState()
}

sealed class UploadAvatarState {
    object Loading : UploadAvatarState()
    data class Success(val roomOverall: ConversationModel) : UploadAvatarState()
    data class Error(val exception: Exception) : UploadAvatarState()
}

sealed class DeleteAvatarState {
    object Loading : DeleteAvatarState()
    data class Success(val roomOverall: ConversationModel) : DeleteAvatarState()
    data class Error(val exception: Exception) : DeleteAvatarState()
}
