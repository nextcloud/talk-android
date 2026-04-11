/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfoedit.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.R
import com.nextcloud.talk.conversationinfoedit.data.ConversationInfoEditRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

class ConversationInfoEditViewModel @Inject constructor(
    private val conversationInfoEditRepository: ConversationInfoEditRepository,
    private val currentUserProvider: CurrentUserProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationInfoEditUiState())
    val uiState: StateFlow<ConversationInfoEditUiState> = _uiState.asStateFlow()

    private var roomToken: String = ""
    private var initialized = false
    private var currentUser: User? = null

    fun initialize(token: String) {
        if (initialized) return
        initialized = true
        roomToken = token
        loadRoom()
    }

    fun updateConversationName(name: String) {
        _uiState.update { it.copy(conversationName = name) }
    }

    fun updateConversationDescription(description: String) {
        _uiState.update { it.copy(conversationDescription = description) }
    }

    fun messageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun resetNavigateBack() {
        _uiState.update { it.copy(navigateBack = false) }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun loadRoom() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val user = currentUserProvider.getCurrentUser().getOrThrow()
                currentUser = user
                val roomData = conversationInfoEditRepository.getRoom(user, roomToken)
                val conversationModel = roomData.conversation
                val isEvent = conversationModel.objectType == ConversationEnums.ObjectType.EVENT
                val userCredentials = ApiUtils.getCredentials(user.username, user.token) ?: ""
                val (avatarUrlLight, avatarUrlDark) = buildAvatarUrls(user, conversationModel)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        conversationName = conversationModel.displayName,
                        conversationDescription = conversationModel.description
                            .takeIf { desc -> desc.isNotEmpty() }.orEmpty(),
                        conversation = conversationModel,
                        conversationUser = user,
                        avatarCredentials = userCredentials,
                        avatarUrl = avatarUrlLight,
                        avatarUrlDark = avatarUrlDark,
                        nameEnabled = !isEvent,
                        descriptionEnabled = roomData.descriptionEndpointAvailable && !isEvent,
                        showSaveButton = !isEvent,
                        avatarButtonsEnabled = true,
                        descriptionMaxLength = roomData.descriptionMaxLength,
                        isDescriptionEndpointAvailable = roomData.descriptionEndpointAvailable
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error when fetching room", e)
                _uiState.update { it.copy(isLoading = false, userMessage = R.string.nc_common_error_sorry) }
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun uploadAvatar(file: File) {
        viewModelScope.launch {
            try {
                val user = currentUser ?: return@launch
                val conversationModel = conversationInfoEditRepository.uploadConversationAvatar(user, roomToken, file)
                val (avatarUrlLight, avatarUrlDark) = buildAvatarUrls(user, conversationModel)
                _uiState.update {
                    it.copy(
                        conversation = conversationModel,
                        avatarUrl = avatarUrlLight,
                        avatarUrlDark = avatarUrlDark,
                        avatarRefreshKey = it.avatarRefreshKey + 1
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error when uploading avatar", e)
                _uiState.update { it.copy(userMessage = R.string.nc_common_error_sorry) }
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun deleteAvatar() {
        viewModelScope.launch {
            try {
                val user = currentUser ?: return@launch
                val conversationModel = conversationInfoEditRepository.deleteConversationAvatar(user, roomToken)
                val (avatarUrlLight, avatarUrlDark) = buildAvatarUrls(user, conversationModel)
                _uiState.update {
                    it.copy(
                        conversation = conversationModel,
                        avatarUrl = avatarUrlLight,
                        avatarUrlDark = avatarUrlDark,
                        avatarRefreshKey = it.avatarRefreshKey + 1
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error when deleting avatar", e)
                _uiState.update { it.copy(userMessage = R.string.nc_common_error_sorry) }
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun renameRoom(token: String, newName: String) {
        viewModelScope.launch {
            try {
                val user = currentUser ?: currentUserProvider.getCurrentUser().getOrThrow()
                conversationInfoEditRepository.renameConversation(user, token, newName)
                _uiState.update { it.copy(navigateBack = true) }
            } catch (exception: Exception) {
                Log.e(TAG, "Error while renaming conversation", exception)
                _uiState.update { it.copy(userMessage = R.string.default_error_msg) }
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun saveNameAndDescription() {
        val roomToken = _uiState.value.conversation?.token ?: return
        val newRoomName = _uiState.value.conversationName
        viewModelScope.launch {
            try {
                val user = currentUser ?: return@launch
                conversationInfoEditRepository.renameConversation(user, roomToken, newRoomName)
                if (_uiState.value.isDescriptionEndpointAvailable) {
                    conversationInfoEditRepository.setConversationDescription(
                        user,
                        roomToken,
                        _uiState.value.conversationDescription
                    )
                }
                _uiState.update { it.copy(navigateBack = true) }
            } catch (exception: Exception) {
                Log.e(TAG, "Error while saving conversation", exception)
                _uiState.update { it.copy(userMessage = R.string.default_error_msg) }
            }
        }
    }

    companion object {
        private val TAG = ConversationInfoEditViewModel::class.simpleName
    }

    private fun buildAvatarUrls(user: User, conversationModel: ConversationModel): Pair<String, String> {
        val avatarVersion = conversationModel.avatarVersion.takeIf { it.isNotEmpty() }
        return when (conversationModel.type) {
            ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL -> Pair(
                ApiUtils.getUrlForAvatar(user.baseUrl, conversationModel.displayName, true, false),
                ApiUtils.getUrlForAvatar(user.baseUrl, conversationModel.displayName, true, true)
            )
            ConversationEnums.ConversationType.ROOM_GROUP_CALL,
            ConversationEnums.ConversationType.ROOM_PUBLIC_CALL -> Pair(
                ApiUtils.getUrlForConversationAvatarWithVersion(
                    version = 1,
                    baseUrl = user.baseUrl,
                    token = conversationModel.token,
                    isDark = false,
                    avatarVersion = avatarVersion
                ),
                ApiUtils.getUrlForConversationAvatarWithVersion(
                    version = 1,
                    baseUrl = user.baseUrl,
                    token = conversationModel.token,
                    isDark = true,
                    avatarVersion = avatarVersion
                )
            )
            else -> Pair("", "")
        }
    }
}
