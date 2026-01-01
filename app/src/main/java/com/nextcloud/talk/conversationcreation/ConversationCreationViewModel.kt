/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.RetrofitBucket
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.generic.GenericMeta
import com.nextcloud.talk.repositories.conversations.ConversationsRepositoryImpl.Companion.STATUS_CODE_OK
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ApiUtils.getRetrofitBucketForAddParticipant
import com.nextcloud.talk.utils.ApiUtils.getRetrofitBucketForAddParticipantWithSource
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

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun createRoomAndAddParticipants(
        roomType: String,
        conversationName: String,
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
                val retrofitBucket: RetrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
                    version = apiVersion,
                    baseUrl = _currentUser.baseUrl,
                    roomType = roomType,
                    conversationName = conversationName
                )
                val roomResult = repository.createRoom(
                    credentials,
                    retrofitBucket
                )
                val conversation = roomResult.ocs?.data

                if (conversation != null) {
                    val token = conversation.token
                    if (token != null) {
                        try {
                            val apiVersion = ApiUtils.getConversationApiVersion(
                                _currentUser,
                                intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1)
                            )
                            val url = ApiUtils.getUrlForConversationDescription(
                                apiVersion,
                                _currentUser.baseUrl,
                                token
                            )

                            repository.setConversationDescription(
                                credentials,
                                url,
                                token,
                                _conversationDescription.value
                            )

                            val urlForRoomPublic = ApiUtils.getUrlForRoomPublic(
                                apiVersion,
                                _currentUser.baseUrl!!,
                                token
                            )
                            val allowGuestResultOverall = repository.allowGuests(
                                credentials,
                                urlForRoomPublic,
                                token,
                                isGuestsAllowed.value
                            )
                            val statusCode: GenericMeta? = allowGuestResultOverall.ocs?.meta
                            val result = (statusCode?.statusCode == STATUS_CODE_OK)
                            if (result) {
                                allowGuestsResult.value = AllowGuestsUiState.Success(result)
                                for (participant in participants) {
                                    if (participant.id != null) {
                                        val retrofitBucket: RetrofitBucket = if (participant.source!! == "users") {
                                            getRetrofitBucketForAddParticipant(
                                                apiVersion,
                                                _currentUser.baseUrl,
                                                token,
                                                participant.id!!
                                            )
                                        } else {
                                            getRetrofitBucketForAddParticipantWithSource(
                                                apiVersion,
                                                _currentUser.baseUrl,
                                                token,
                                                participant.source!!,
                                                participant.id!!
                                            )
                                        }

                                        val participantOverall = repository.addParticipants(
                                            credentials,
                                            retrofitBucket
                                        ).ocs?.data
                                        addParticipantsViewState.value =
                                            AddParticipantsUiState.Success(participantOverall)
                                    }
                                }
                            }
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

    fun getImageUri(avatarId: String, requestBigSize: Boolean): String =
        ApiUtils.getUrlForAvatar(
            _currentUser.baseUrl,
            avatarId,
            requestBigSize
        )
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
