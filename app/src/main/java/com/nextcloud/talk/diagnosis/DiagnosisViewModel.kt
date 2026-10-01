/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.diagnosis

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.logger.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Suppress("TooGenericExceptionCaught")
class DiagnosisViewModel @AssistedInject constructor(
    private val ncApiCoroutines: NcApiCoroutines,
    private val logger: Logger,
    @Assisted val currentUser: User
) : ViewModel() {
    val credentials = ApiUtils.getCredentials(currentUser.username, currentUser.token) ?: ""

    private val _notificationViewState = MutableStateFlow<NotificationUiState>(NotificationUiState.None)
    val notificationViewState: StateFlow<NotificationUiState> = _notificationViewState

    private val _isLoading = mutableStateOf(false)
    val isLoading = _isLoading

    private val _showDialog = mutableStateOf(false)
    val showDialog = _showDialog

    fun fetchTestPushResult() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val response = ncApiCoroutines.testPushNotifications(
                    credentials,
                    ApiUtils
                        .getUrlForTestPushNotifications(currentUser.baseUrl ?: "")
                )
                val notificationMessage = response.ocs?.data?.message
                _notificationViewState.value = NotificationUiState.Success(notificationMessage)
            } catch (e: Exception) {
                logger.e(TAG, "Failed to fetch test push notification result", e)
                _notificationViewState.value = NotificationUiState.Error(e.message ?: "")
            } finally {
                _isLoading.value = false
                _showDialog.value = true
            }
        }
    }

    fun dismissDialog() {
        _showDialog.value = false
    }

    @AssistedFactory
    interface Factory {
        fun build(user: User): DiagnosisViewModel
    }

    companion object {
        private val TAG = DiagnosisViewModel::class.java.simpleName
    }
}

sealed class NotificationUiState {
    data object None : NotificationUiState()
    data class Success(val testNotification: String?) : NotificationUiState()
    data class Error(val message: String) : NotificationUiState()
}
