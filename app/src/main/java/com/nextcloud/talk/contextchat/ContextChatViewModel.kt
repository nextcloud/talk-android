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
import com.nextcloud.talk.models.json.chat.ChatMessageJson
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
            val user = userManager.currentUser.blockingGet()

            if (!user.hasSpreedFeatureCapability("chat-get-context") ||
                !user.hasSpreedFeatureCapability("federation-v1")
            ) {
                _getContextChatMessagesState.value = ContextChatRetrieveUiState.Error
            }

            var messages = chatNetworkDataSource.getContextForChatMessage(
                credentials = credentials,
                baseUrl = baseUrl,
                token = token,
                messageId = messageId,
                limit = LIMIT,
                threadId = threadId?.toInt()
            )

            if (threadId.isNullOrEmpty()) {
                messages = messages.filter { it.id == it.threadId }
            }

            if (threadId?.isNotEmpty() == true) {
                finalTitle = messages.firstOrNull()?.threadTitle
            }

            _getContextChatMessagesState.value = ContextChatRetrieveUiState.Success(
                messageId = messageId,
                threadId = threadId,
                messages = messages,
                title = finalTitle
            )
        }
    }

    fun clearContextChatState() {
        _getContextChatMessagesState.value = ContextChatRetrieveUiState.None
    }

    sealed class ContextChatRetrieveUiState {
        data object None : ContextChatRetrieveUiState()
        data class Success(
            val messageId: String,
            val threadId: String?,
            val messages: List<ChatMessageJson>,
            val title: String?
        ) : ContextChatRetrieveUiState()
        data object Error : ContextChatRetrieveUiState()
    }

    companion object {
        private const val LIMIT = 50
    }
}
