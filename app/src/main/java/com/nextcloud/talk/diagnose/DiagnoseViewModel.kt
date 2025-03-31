/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.diagnose

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import kotlinx.coroutines.launch
import javax.inject.Inject

class DiagnoseViewModel @Inject constructor(
    private val ncApiCoroutines: NcApiCoroutines,
    private val currentUserProvider: CurrentUserProviderNew
) : ViewModel() {
    private val _currentUser = currentUserProvider.currentUser.blockingGet()
    val currentUser: User = _currentUser
    val credentials = ApiUtils.getCredentials(_currentUser.username, _currentUser.token) ?: ""

    private val _notificationMessage = mutableStateOf("")
    val notificationMessage = _notificationMessage

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
                        .getUrlForTestPushNotifications(_currentUser.baseUrl ?: "")
                )
                _notificationMessage.value = response.ocs?.data?.message ?: "Error while fetching test push message"
            } catch (e: Exception) {
                _notificationMessage.value = "Exception: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
                _showDialog.value = true
            }
        }
    }

    fun dismissDialog() {
        _showDialog.value = false
    }
}
