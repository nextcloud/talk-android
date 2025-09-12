/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.threadsoverview.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.conversationlist.viewmodels.ConversationsListViewModel.Companion.FOLLOWED_THREADS_EXIST
import com.nextcloud.talk.conversationlist.viewmodels.ConversationsListViewModel.Companion.FOLLOWED_THREADS_EXIST_LAST_CHECK
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.threads.ThreadInfo
import com.nextcloud.talk.models.json.threads.ThreadsOverall
import com.nextcloud.talk.threadsoverview.data.ThreadsRepository
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.UserIdUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooGenericExceptionCaught")
class ThreadsOverviewViewModel @Inject constructor(
    private val threadsRepository: ThreadsRepository,
    private val currentUserProvider: CurrentUserProviderNew,
    private val arbitraryStorageManager: ArbitraryStorageManager
) : ViewModel() {
    private val _currentUser = currentUserProvider.currentUser.blockingGet()
    val currentUser: User = _currentUser
    val credentials = ApiUtils.getCredentials(_currentUser.username, _currentUser.token) ?: ""

    private val _threadsListState = MutableStateFlow<ThreadsListUiState>(ThreadsListUiState.None)
    val threadsListState: StateFlow<ThreadsListUiState> = _threadsListState

    fun init(url: String) {
        getThreads(credentials, url)
    }

    fun getThreads(credentials: String, url: String) {
        viewModelScope.launch {
            try {
                val threads = threadsRepository.getThreads(credentials, url, null)
                _threadsListState.value = ThreadsListUiState.Success(threads.ocs?.data)
                updateFollowedThreadsIndicator(url, threads)
            } catch (exception: Exception) {
                _threadsListState.value = ThreadsListUiState.Error(exception)
            }
        }
    }

    private fun updateFollowedThreadsIndicator(url: String, threads: ThreadsOverall) {
        val subscribedThreadsEndpoint = "subscribed-threads"
        if (url.contains(subscribedThreadsEndpoint) && threads.ocs?.data?.isEmpty() == true) {
            val accountId = UserIdUtils.getIdForUser(currentUserProvider.currentUser.blockingGet())
            arbitraryStorageManager.storeStorageSetting(
                accountId,
                FOLLOWED_THREADS_EXIST,
                false.toString(),
                ""
            )
            arbitraryStorageManager.storeStorageSetting(
                accountId,
                FOLLOWED_THREADS_EXIST_LAST_CHECK,
                null,
                ""
            )
        }
    }

    sealed class ThreadsListUiState {
        data object None : ThreadsListUiState()
        data class Success(val threadsList: List<ThreadInfo>?) : ThreadsListUiState()
        data class Error(val exception: Exception) : ThreadsListUiState()
    }
}
