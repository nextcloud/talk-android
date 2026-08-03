/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation.viewmodel

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.conversationcreation.data.ConversationCreationRepository
import com.nextcloud.talk.conversationinfo.CreateRoomRequest
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOld
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ConversationCreationViewModel @Inject constructor(
    private val repository: ConversationCreationRepository,
    private val currentUserProvider: CurrentUserProviderOld
) : ViewModel() {
    private val _selectedParticipants = MutableStateFlow<List<AutocompleteUser>>(emptyList())
    val selectedParticipants: StateFlow<List<AutocompleteUser>> = _selectedParticipants
    private val roomViewState = MutableStateFlow<RoomUIState>(RoomUIState.None)

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri

    private val _currentUser = currentUserProvider.currentUser.blockingGet()
    val currentUser: User = _currentUser

    private val _isPasswordEnabled = mutableStateOf(false)
    val isPasswordEnabled = _isPasswordEnabled

    fun updateSelectedParticipants(participants: List<AutocompleteUser>) {
        _selectedParticipants.value = participants
    }

    fun isPasswordEnabled(value: Boolean) {
        _isPasswordEnabled.value = value
    }

    fun updateSelectedImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    private val _roomName = MutableStateFlow("")
    val roomName: StateFlow<String> = _roomName
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password
    private val _conversationDescription = MutableStateFlow("")
    val conversationDescription: StateFlow<String> = _conversationDescription
    var isGuestsAllowed = mutableStateOf(false)
    var isConversationAvailableForRegisteredUsers = mutableStateOf(false)
    val conversationPreset = mutableStateOf("default")
    var openForGuestAppUsers = mutableStateOf(false)
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

    fun updateConversationPreset(preset: String) {
        conversationPreset.value = preset
        when (preset) {
            "channel" -> {
                isConversationAvailableForRegisteredUsers.value = true
                openForGuestAppUsers.value = false
            }
            "announcement" -> {
                isConversationAvailableForRegisteredUsers.value = false
                openForGuestAppUsers.value = false
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught", "LongMethod")
    fun createRoomAndAddParticipants(
        roomType: String,
        conversationName: String,
        preset: String = "default",
        participants: Set<AutocompleteUser>,
        onRoomCreated: (String) -> Unit
    ) {
        val credentials = ApiUtils.getCredentials(_currentUser.username, _currentUser.token)
        val scope = when {
            isConversationAvailableForRegisteredUsers.value && !openForGuestAppUsers.value -> 1
            isConversationAvailableForRegisteredUsers.value && openForGuestAppUsers.value -> 2
            else -> 0
        }
        viewModelScope.launch {
            roomViewState.value = RoomUIState.None
            try {
                val apiVersion =
                    ApiUtils.getConversationApiVersion(_currentUser, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
                val url = ApiUtils.getUrlForRooms(apiVersion, _currentUser.baseUrl)
                val body = CreateRoomRequest().apply {
                    this.roomType = roomType
                    this.roomName = conversationName
                    this.preset = preset
                    this.description = _conversationDescription.value
                    this.listable = scope
                    this.participants = convertAutocompleteUserToParticipants(participants)

                    if (preset == "channel" || preset == "announcement") {
                        this.permissions = ParticipantPermissions.DEFAULT_GROUP_PERMISSIONS and
                            ParticipantPermissions.CHAT.inv()
                    }
                }
                val roomResult = repository.createRoomWithBody(
                    credentials,
                    url,
                    body
                )
                val conversation = roomResult.ocs?.data

                if (conversation != null) {
                    val token = conversation.token
                    if (token != null) {
                        try {
                            if (_password.value.isNotEmpty()) {
                                val url = ApiUtils.getUrlForRoomPassword(
                                    apiVersion,
                                    _currentUser.baseUrl!!,
                                    token
                                )
                                repository.setPassword(
                                    credentials,
                                    url,
                                    token,
                                    _password.value
                                )
                            }

                            val urlForOpeningConversations = ApiUtils.getUrlForOpeningConversations(
                                apiVersion,
                                _currentUser.baseUrl,
                                token
                            )

                            repository.openConversation(
                                credentials,
                                urlForOpeningConversations,
                                token,
                                scope
                            )

                            val urlForConversationAvatar = ApiUtils.getUrlForConversationAvatar(
                                1,
                                _currentUser.baseUrl!!,
                                token
                            )

                            selectedImageUri.value?.let {
                                repository.uploadConversationAvatar(
                                    credentials,
                                    _currentUser,
                                    urlForConversationAvatar,
                                    it.toFile(),
                                    token
                                )
                            }
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

    private fun convertAutocompleteUserToParticipants(
        autocompleteUsers: Set<AutocompleteUser>
    ): com.nextcloud.talk.conversationinfo.Participants {
        val participants = com.nextcloud.talk.conversationinfo.Participants()
        autocompleteUsers.forEach { autocompleteUser ->
            when (autocompleteUser.source) {
                "groups" -> participants.groups.add(autocompleteUser.id!!)
                "emails" -> participants.emails.add(autocompleteUser.id!!)
                "circles" -> participants.teams.add(autocompleteUser.id!!)
                "federated" -> participants.federatedUsers.add(autocompleteUser.id!!)
                "phones" -> participants.phones.add(autocompleteUser.id!!)
                else -> participants.users.add(autocompleteUser.id!!)
            }
        }
        return participants
    }

    fun getImageUri(avatarId: String, requestBigSize: Boolean, isDarkMode: Boolean): String =
        ApiUtils.getUrlForAvatar(_currentUser.baseUrl, avatarId, requestBigSize, darkMode = isDarkMode)
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
