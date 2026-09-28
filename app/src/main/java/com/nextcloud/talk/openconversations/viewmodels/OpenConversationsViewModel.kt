/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.openconversations.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.ConversationDto
import com.nextcloud.talk.openconversations.data.OpenConversationsRepository
import com.nextcloud.talk.utils.ApiUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OpenConversationsViewModel @AssistedInject constructor(
    private val repository: OpenConversationsRepository,
    @Assisted private val user: User
) : ViewModel() {

    sealed interface ViewState

    object FetchConversationsStartState : ViewState
    object FetchConversationsEmptyState : ViewState
    object FetchConversationsErrorState : ViewState
    open class FetchConversationsSuccessState(val conversations: List<ConversationDto>) : ViewState

    private val _viewState: MutableStateFlow<ViewState> = MutableStateFlow(FetchConversationsStartState)
    val viewState: StateFlow<ViewState>
        get() = _viewState

    private val _searchTerm: MutableStateFlow<String> = MutableStateFlow("")
    val searchTerm: StateFlow<String>
        get() = _searchTerm

    fun fetchConversations() {
        _viewState.value = FetchConversationsStartState

        viewModelScope.launch {
            val apiVersion = ApiUtils.getConversationApiVersion(
                user,
                intArrayOf(
                    ApiUtils.API_V4,
                    ApiUtils.API_V3,
                    1
                )
            )
            val url = ApiUtils.getUrlForOpenConversations(apiVersion, user.baseUrl!!)

            repository.fetchConversations(
                user,
                url,
                _searchTerm.value
            )
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

    @AssistedFactory
    interface Factory {
        fun build(user: User): OpenConversationsViewModel
    }

    companion object {
        private val TAG = OpenConversationsViewModel::class.simpleName
    }
}
