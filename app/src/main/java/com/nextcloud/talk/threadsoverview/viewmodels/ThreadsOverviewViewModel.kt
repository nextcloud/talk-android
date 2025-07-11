/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.threadsoverview.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.threads.ThreadInfo
import com.nextcloud.talk.threadsoverview.data.ThreadsRepository
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooGenericExceptionCaught")
class ThreadsOverviewViewModel @Inject constructor(
    private val threadsRepository: ThreadsRepository,
    private val currentUserProvider: CurrentUserProviderNew
) : ViewModel() {
    private val _currentUser = currentUserProvider.currentUser.blockingGet()
    val currentUser: User = _currentUser
    val credentials = ApiUtils.getCredentials(_currentUser.username, _currentUser.token) ?: ""

    private val _threadsListState = MutableStateFlow<ThreadsListUiState>(ThreadsListUiState.None)
    val threadsListState: StateFlow<ThreadsListUiState> = _threadsListState

    fun init(roomToken: String) {
        val url = ApiUtils.getUrlForRecentThreads(
            version = 1,
            baseUrl = _currentUser.baseUrl,
            token = roomToken
        )

        getThreads(credentials, url)
    }

    fun getThreads(credentials: String, url: String) {
        viewModelScope.launch {
            try {
                val threads = threadsRepository.getThreads(credentials, url)
                _threadsListState.value = ThreadsListUiState.Success(threads.ocs?.data)
            } catch (exception: Exception) {
                _threadsListState.value = ThreadsListUiState.Error(exception)
            }
        }
    }

    sealed class ThreadsListUiState {
        data object None : ThreadsListUiState()
        data class Success(val threadsList: List<ThreadInfo>?) : ThreadsListUiState()
        data class Error(val exception: Exception) : ThreadsListUiState()
    }
}
