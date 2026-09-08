/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation.viewmodel

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.conversationcreation.ConversationCreator
import com.nextcloud.talk.conversationcreation.ConversationParameter
import com.nextcloud.talk.conversationcreation.ConversationPresetId
import com.nextcloud.talk.conversationcreation.ConversationPresetModel
import com.nextcloud.talk.conversationcreation.CreateConversationParams
import com.nextcloud.talk.conversationcreation.NewConversation
import com.nextcloud.talk.conversationcreation.ParticipantSource
import com.nextcloud.talk.conversationcreation.data.ConversationCreationRepository
import com.nextcloud.talk.conversationcreation.parametersFor
import com.nextcloud.talk.conversationcreation.parametersOf
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOld
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ConversationCreationViewModel @Inject constructor(
    private val repository: ConversationCreationRepository,
    private val conversationCreator: ConversationCreator,
    private val currentUserProvider: CurrentUserProviderOld
) : ViewModel() {
    private val _selectedParticipants = MutableStateFlow<List<AutocompleteUser>>(emptyList())
    val selectedParticipants: StateFlow<List<AutocompleteUser>> = _selectedParticipants
    private val roomViewState = MutableStateFlow<RoomUIState>(RoomUIState.None)
    val creationState: StateFlow<RoomUIState> = roomViewState

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri

    private val _selectedEmoji = MutableStateFlow<String?>(null)
    val selectedEmoji: StateFlow<String?> = _selectedEmoji

    private val _selectedEmojiColor = MutableStateFlow<Int?>(null)
    val selectedEmojiColor: StateFlow<Int?> = _selectedEmojiColor

    private val _isCreatingRoom = MutableStateFlow(false)
    val isCreatingRoom: StateFlow<Boolean> = _isCreatingRoom

    private val _currentUser = currentUserProvider.currentUser.blockingGet()
    val currentUser: User = _currentUser

    private val spreedCapabilities = _currentUser.capabilities?.spreedCapability

    val showPresetSelection = CapabilitiesUtil.hasSpreedFeatureCapability(
        spreedCapabilities,
        SpreedFeatures.CONVERSATION_PRESETS
    )

    fun updateSelectedParticipants(participants: List<AutocompleteUser>) {
        _selectedParticipants.value = participants
    }

    fun updateSelectedImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
        if (uri != null) {
            _selectedEmoji.value = null
            _selectedEmojiColor.value = null
        }
    }

    fun updateSelectedEmojiAvatar(emoji: String?, color: Int? = null) {
        _selectedEmoji.value = emoji
        _selectedEmojiColor.value = color.takeIf { emoji != null }
        if (emoji != null) {
            _selectedImageUri.value = null
        }
    }

    private val _roomName = MutableStateFlow("")
    val roomName: StateFlow<String> = _roomName
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password
    private val _conversationDescription = MutableStateFlow("")
    val conversationDescription: StateFlow<String> = _conversationDescription
    val conversationPreset = mutableStateOf(ConversationPresetId.DEFAULT)

    private val conversationParams = mutableStateOf(CreateConversationParams())

    private val parametersChosenByUser = mutableStateOf<Map<String, Int>>(emptyMap())

    private val _presets = mutableStateOf<PresetsUiState>(PresetsUiState.Loading)
    val presets: State<PresetsUiState> = _presets

    private var presetsJob: Job? = null

    val isLoadingPresets: Boolean
        get() = showPresetSelection && _presets.value is PresetsUiState.Loading

    val pinnedParameters: Set<String>
        get() = (_presets.value as? PresetsUiState.Success)
            ?.presets
            ?.parametersOf(ConversationPresetId.FORCED)
            ?.keys
            .orEmpty()

    init {
        if (showPresetSelection) {
            loadPresets()
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun loadPresets() {
        presetsJob?.cancel()
        _presets.value = PresetsUiState.Loading
        presetsJob = viewModelScope.launch {
            try {
                val presets = repository.getConversationPresets(
                    ApiUtils.getCredentials(_currentUser.username, _currentUser.token),
                    ApiUtils.getUrlForConversationPresets(_currentUser.baseUrl)
                ).mapNotNull { ConversationPresetModel.mapToConversationPresetModel(it) }
                _presets.value = PresetsUiState.Success(presets)
                recomputeParams()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load the conversation presets", e)
                _presets.value = PresetsUiState.Error
            }
        }
    }

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
        val loaded = (_presets.value as? PresetsUiState.Success)?.presets.orEmpty()
        parametersChosenByUser.value -= loaded.parametersOf(preset).keys
        if (isLockedDown) {
            parametersChosenByUser.value -= setOf(ConversationParameter.ROOM_TYPE, ConversationParameter.LISTABLE)
            _selectedParticipants.value = _selectedParticipants.value.filter {
                (it.source ?: ParticipantSource.USERS) in ParticipantSource.local
            }
        }
        recomputeParams()
    }

    val isLockedDown: Boolean
        get() = conversationPreset.value in ConversationPresetId.lockedDown

    val isGuestsAllowed: Boolean
        get() = conversationParams.value.roomType == CreateConversationParams.ROOM_TYPE_PUBLIC

    val isConversationAvailableForRegisteredUsers: Boolean
        get() = conversationParams.value.listable != CreateConversationParams.LISTABLE_NONE

    val isOpenForGuestAppUsers: Boolean
        get() = conversationParams.value.listable == CreateConversationParams.LISTABLE_ALL

    fun allowGuests(allow: Boolean) {
        val roomType = if (allow) {
            CreateConversationParams.ROOM_TYPE_PUBLIC
        } else {
            CreateConversationParams.ROOM_TYPE_GROUP
        }
        parametersChosenByUser.value += ConversationParameter.ROOM_TYPE to roomType
        recomputeParams()
    }

    fun openConversationToRegisteredUsers(open: Boolean) {
        val listable = if (open) {
            CreateConversationParams.LISTABLE_USERS
        } else {
            CreateConversationParams.LISTABLE_NONE
        }
        parametersChosenByUser.value += ConversationParameter.LISTABLE to listable
        recomputeParams()
    }

    fun openConversationToGuestAppUsers(open: Boolean) {
        val listable = if (open) {
            CreateConversationParams.LISTABLE_ALL
        } else {
            CreateConversationParams.LISTABLE_USERS
        }
        parametersChosenByUser.value += ConversationParameter.LISTABLE to listable
        recomputeParams()
    }

    private fun recomputeParams() {
        val loaded = (_presets.value as? PresetsUiState.Success)?.presets.orEmpty()
        val params = loaded.parametersFor(conversationPreset.value, parametersChosenByUser.value)
        conversationParams.value = params
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun createRoomAndAddParticipants() {
        if (_isCreatingRoom.value) {
            return
        }
        _isCreatingRoom.value = true

        viewModelScope.launch {
            roomViewState.value = RoomUIState.None
            try {
                val conversation = conversationCreator.create(
                    _currentUser,
                    NewConversation(
                        name = _roomName.value,
                        description = _conversationDescription.value,
                        preset = conversationPreset.value,
                        params = conversationParams.value,
                        password = _password.value,
                        participants = _selectedParticipants.value.distinctBy { it.source to it.id },
                        emoji = _selectedEmoji.value,
                        emojiColor = _selectedEmojiColor.value,
                        imageUri = _selectedImageUri.value
                    )
                )
                val token = conversation?.token
                if (!token.isNullOrEmpty()) {
                    roomViewState.value = RoomUIState.Success(conversation)
                } else {
                    roomViewState.value = RoomUIState.Error("Conversation is null")
                }
            } catch (e: Exception) {
                roomViewState.value = RoomUIState.Error(e.message ?: "Unknown error")
                Log.e(TAG, "Error - ${e.message}")
            } finally {
                _isCreatingRoom.value = false
            }
        }
    }

    fun clearCreationState() {
        roomViewState.value = RoomUIState.None
    }

    companion object {
        private val TAG = ConversationCreationViewModel::class.simpleName
    }
}

sealed interface PresetsUiState {
    data object Loading : PresetsUiState
    data class Success(val presets: List<ConversationPresetModel>) : PresetsUiState
    data object Error : PresetsUiState
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
