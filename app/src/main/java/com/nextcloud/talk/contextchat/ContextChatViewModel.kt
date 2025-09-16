/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contextchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.chat.viewmodels.ChatViewModel.ContextChatRetrieveUiState
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ContextChatViewModel @Inject constructor(private val chatNetworkDataSource: ChatNetworkDataSource) :
    ViewModel() {

    @Inject
    lateinit var chatViewModel: ChatViewModel

    @Inject
    lateinit var userManager: UserManager

    var threadId: String? = null

    private val _getContextChatMessagesState =
        MutableStateFlow<ContextChatRetrieveUiState>(ContextChatRetrieveUiState.None)
    val getContextChatMessagesState: StateFlow<ContextChatRetrieveUiState> = _getContextChatMessagesState

    fun getContextForChatMessages(
        credentials: String,
        baseUrl: String,
        token: String,
        threadId: String?,
        messageId: String,
        title: String
    ) {
        var finalTitle: String? = title

        viewModelScope.launch {
            var messages = chatNetworkDataSource.getContextForChatMessage(
                credentials = credentials,
                baseUrl = baseUrl,
                token = token,
                messageId = messageId,
                limit = LIMIT,
                threadId = threadId?.toInt()
            )

            if (threadId?.isEmpty() == true) {
                messages = messages.filter { it.id == it.threadId }
            }

            if (threadId?.isNotEmpty() == true) {
                finalTitle = messages.firstOrNull()?.threadTitle
            }

            _getContextChatMessagesState.value = ContextChatRetrieveUiState.Success(messageId, messages, finalTitle)
        }
    }

    companion object {
        private const val LIMIT = 50
    }
}
