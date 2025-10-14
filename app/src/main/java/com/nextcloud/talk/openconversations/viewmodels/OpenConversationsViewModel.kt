/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.openconversations.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.openconversations.data.OpenConversationsRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

class OpenConversationsViewModel @Inject constructor(private val repository: OpenConversationsRepository) :
    ViewModel() {

    sealed interface ViewState

    object FetchConversationsStartState : ViewState
    object FetchConversationsEmptyState : ViewState
    object FetchConversationsErrorState : ViewState
    open class FetchConversationsSuccessState(val conversations: List<Conversation>) : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(FetchConversationsStartState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    private val _searchTerm: MutableLiveData<String> = MutableLiveData("")
    val searchTerm: LiveData<String>
        get() = _searchTerm

    fun fetchConversations() {
        _viewState.value = FetchConversationsStartState

        viewModelScope.launch {
            repository.fetchConversations(_searchTerm.value ?: "")
                .onSuccess { conversations ->
                    if (conversations.isEmpty()) {
                        _viewState.value = FetchConversationsEmptyState
                    } else {
                        _viewState.value = FetchConversationsSuccessState(conversations)
                    }
                }
                .onFailure { exception ->
                    Log.e(TAG, "Failed to fetch conversations", exception)
                    _viewState.value = FetchConversationsErrorState
                }
        }
    }

    fun updateSearchTerm(newTerm: String) {
        _searchTerm.value = newTerm
    }

    companion object {
        private val TAG = OpenConversationsViewModel::class.simpleName
    }
}
