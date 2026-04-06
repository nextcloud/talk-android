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
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
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

    private suspend fun resolveCurrentUser(): User = currentUserProvider.getCurrentUser().getOrThrow()

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

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun loadRoom() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val user = resolveCurrentUser()
                val apiVersion = ApiUtils.getConversationApiVersion(
                    user,
                    intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, ApiUtils.API_V1)
                )
                val url = ApiUtils.getUrlForRoom(apiVersion, user.baseUrl!!, roomToken)
                val userCredentials = ApiUtils.getCredentials(user.username, user.token) ?: ""
                val conversationModel = conversationInfoEditRepository.getRoom(userCredentials, url, user)
                val spreedCapabilities = user.capabilities?.spreedCapability!!
                val descriptionEndpointAvailable =
                    CapabilitiesUtil.isConversationDescriptionEndpointAvailable(spreedCapabilities)
                val descriptionMaxLength = CapabilitiesUtil.conversationDescriptionLength(spreedCapabilities)
                val isEvent = conversationModel.objectType == ConversationEnums.ObjectType.EVENT
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        conversationName = conversationModel.displayName,
                        conversationDescription = conversationModel.description
                            .takeIf { desc -> desc.isNotEmpty() }.orEmpty(),
                        conversation = conversationModel,
                        conversationUser = user,
                        nameEnabled = !isEvent,
                        descriptionEnabled = descriptionEndpointAvailable && !isEvent,
                        descriptionMaxLength = descriptionMaxLength,
                        isDescriptionEndpointAvailable = descriptionEndpointAvailable
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
                val user = resolveCurrentUser()
                val url = ApiUtils.getUrlForConversationAvatar(1, user.baseUrl!!, roomToken)
                val userCredentials = ApiUtils.getCredentials(user.username, user.token)
                val conversationModel = conversationInfoEditRepository.uploadConversationAvatar(
                    userCredentials,
                    url,
                    user,
                    file,
                    roomToken
                )
                _uiState.update {
                    it.copy(conversation = conversationModel, avatarRefreshKey = it.avatarRefreshKey + 1)
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
                val user = resolveCurrentUser()
                val url = ApiUtils.getUrlForConversationAvatar(1, user.baseUrl!!, roomToken)
                val userCredentials = ApiUtils.getCredentials(user.username, user.token)
                val conversationModel = conversationInfoEditRepository.deleteConversationAvatar(
                    userCredentials,
                    url,
                    user,
                    roomToken
                )
                _uiState.update {
                    it.copy(conversation = conversationModel, avatarRefreshKey = it.avatarRefreshKey + 1)
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
                val user = resolveCurrentUser()
                val userCredentials = ApiUtils.getCredentials(user.username, user.token)
                val apiVersion = ApiUtils.getConversationApiVersion(
                    user,
                    intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1)
                )
                val url = ApiUtils.getUrlForRoom(apiVersion, user.baseUrl!!, token)
                conversationInfoEditRepository.renameConversation(userCredentials, url, token, newName)
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
                val user = resolveCurrentUser()
                val userCredentials = ApiUtils.getCredentials(user.username, user.token)
                val apiVersion = ApiUtils.getConversationApiVersion(
                    user,
                    intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1)
                )
                val url = ApiUtils.getUrlForRoom(apiVersion, user.baseUrl!!, roomToken)
                conversationInfoEditRepository.renameConversation(userCredentials, url, roomToken, newRoomName)
                if (_uiState.value.isDescriptionEndpointAvailable) {
                    val descApiVersion = ApiUtils.getConversationApiVersion(
                        user,
                        intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1)
                    )
                    val descUrl = ApiUtils.getUrlForConversationDescription(descApiVersion, user.baseUrl!!, roomToken)
                    conversationInfoEditRepository.setConversationDescription(
                        userCredentials,
                        descUrl,
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
}
