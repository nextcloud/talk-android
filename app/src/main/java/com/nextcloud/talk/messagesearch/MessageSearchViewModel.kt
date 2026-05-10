/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.messagesearch

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOld
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Install PlantUML plugin to render this state diagram
 * @startuml
 * hide empty description
 * [*] --> InitialState
 * InitialState --> LoadingState
 * LoadingState --> EmptyState
 * LoadingState --> LoadedState
 * LoadingState --> LoadingState
 * LoadedState --> LoadingState
 * EmptyState --> LoadingState
 * LoadingState --> ErrorState
 * ErrorState --> LoadingState
 * @enduml
 */
class MessageSearchViewModel @Inject constructor(
    private val unifiedSearchRepository: UnifiedSearchRepository,
    private val currentUserProvider: CurrentUserProviderOld
) : ViewModel() {

    sealed class ViewState
    object InitialState : ViewState()
    object LoadingState : ViewState()
    object EmptyState : ViewState()
    object ErrorState : ViewState()
    class LoadedState(val results: List<SearchMessageEntry>, val hasMore: Boolean) : ViewState()
    class FinishedState(val selectedMessageId: String, val selectedThreadId: String?) : ViewState()

    private lateinit var messageSearchHelper: MessageSearchHelper

    private val _state: MutableLiveData<ViewState> = MutableLiveData(InitialState)
    val state: LiveData<ViewState>
        get() = _state

    private var currentUser: User = currentUserProvider.currentUser.blockingGet()
    private var searchJob: Job? = null

    fun initialize(roomToken: String) {
        messageSearchHelper = MessageSearchHelper(
            unifiedSearchRepository,
            currentUser,
            roomToken
        )
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun onQueryTextChange(newText: String) {
        if (newText.length >= MIN_CHARS_FOR_SEARCH) {
            _state.value = LoadingState
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                try {
                    val results = messageSearchHelper.startMessageSearch(newText)
                    onReceiveResults(results)
                } catch (e: Exception) {
                    onError(e)
                }
            }
        }
    }

    fun loadMore() {
        _state.value = LoadingState
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val results = messageSearchHelper.loadMore()
                results?.let { onReceiveResults(it) }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    private fun onReceiveResults(results: MessageSearchHelper.MessageSearchResults) {
        if (results.messages.isEmpty()) {
            _state.value = EmptyState
        } else {
            _state.value = LoadedState(results.messages, results.hasMore)
        }
    }

    private fun onError(throwable: Throwable) {
        Log.e(TAG, "onError:", throwable)
        _state.value = ErrorState
    }

    fun refresh(query: String?) {
        query?.let { onQueryTextChange(it) }
    }

    fun selectMessage(messageEntry: SearchMessageEntry) {
        _state.value = FinishedState(messageEntry.messageId!!, messageEntry.threadId)
    }

    companion object {
        private val TAG = MessageSearchViewModel::class.simpleName
        private const val MIN_CHARS_FOR_SEARCH = 2
    }
}
