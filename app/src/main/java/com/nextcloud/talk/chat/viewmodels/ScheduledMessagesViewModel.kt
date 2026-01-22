/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.utils.message.SendMessageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ScheduledMessagesViewModel @Inject constructor(private val chatRepository: ChatMessageRepository) : ViewModel() {

    sealed interface GetScheduledMessagesState
    object GetScheduledMessagesIdleState : GetScheduledMessagesState
    object GetScheduledMessagesLoadingState : GetScheduledMessagesState
    data class GetScheduledMessagesSuccessState(val messages: List<ChatMessage>) : GetScheduledMessagesState
    data class GetScheduledMessagesErrorState(val error: Throwable? = null) : GetScheduledMessagesState

    private val _getScheduledMessagesState: MutableStateFlow<GetScheduledMessagesState> =
        MutableStateFlow(GetScheduledMessagesIdleState)
    val getScheduledMessagesState: StateFlow<GetScheduledMessagesState>
        get() = _getScheduledMessagesState

    sealed interface ScheduledMessageActionState
    object ScheduledMessageActionIdleState : ScheduledMessageActionState
    object ScheduledMessageActionLoadingState : ScheduledMessageActionState
    data class ScheduledMessageActionSuccessState(val response: ChatMessage? = null) :
        ScheduledMessageActionState

    data class ScheduledMessageErrorState(val error: Throwable? = null) : ScheduledMessageActionState

    sealed interface SendNowMessageState
    object SendNowMessageIdleState : SendNowMessageState
    object SendNowMessageLoadingState : SendNowMessageState
    data class SendNowMessageSuccessState(val message: ChatMessage? = null) : SendNowMessageState
    data class SendNowMessageErrorState(val error: Throwable? = null) : SendNowMessageState

    private val _sendNowState: MutableStateFlow<SendNowMessageState> =
        MutableStateFlow(SendNowMessageIdleState)
    val sendNowState: StateFlow<SendNowMessageState>
        get() = _sendNowState

    private val _rescheduleState: MutableStateFlow<ScheduledMessageActionState> =
        MutableStateFlow(ScheduledMessageActionIdleState)
    val rescheduleState: StateFlow<ScheduledMessageActionState>
        get() = _rescheduleState

    private val _editState: MutableStateFlow<ScheduledMessageActionState> =
        MutableStateFlow(ScheduledMessageActionIdleState)
    val editState: StateFlow<ScheduledMessageActionState>
        get() = _editState

    private val _deleteState: MutableStateFlow<ScheduledMessageActionState> =
        MutableStateFlow(ScheduledMessageActionIdleState)
    val deleteState: StateFlow<ScheduledMessageActionState>
        get() = _deleteState

    fun loadScheduledMessages(credentials: String, url: String) {
        _getScheduledMessagesState.value = GetScheduledMessagesLoadingState
        viewModelScope.launch {
            chatRepository.getScheduledChatMessages(credentials, url).collect { result ->
                if (result.isSuccess) {
                    _getScheduledMessagesState.value =
                        GetScheduledMessagesSuccessState(result.getOrNull().orEmpty())
                } else {
                    _getScheduledMessagesState.value = GetScheduledMessagesErrorState(result.exceptionOrNull())
                }
            }
        }
    }

    @Suppress("LongParameterList")
    fun sendNow(
        credentials: String,
        sendUrl: String,
        message: String,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        threadTitle: String?,
        deleteUrl: String
    ) {
        val referenceId = SendMessageUtils().generateReferenceId()
        _sendNowState.value = SendNowMessageLoadingState
        viewModelScope.launch {
            chatRepository.sendChatMessage(
                credentials = credentials,
                url = sendUrl,
                message = message,
                displayName = displayName,
                replyTo = replyTo,
                sendWithoutNotification = sendWithoutNotification,
                referenceId = referenceId,
                threadTitle = threadTitle
            ).collect { result ->
                if (result.isSuccess) {
                    SendNowMessageSuccessState(result.getOrNull())
                    deleteScheduledMessage(credentials, deleteUrl)
                } else {
                    SendNowMessageErrorState(result.exceptionOrNull())
                }
            }
        }
    }

    @Suppress("LongParameterList")
    fun reschedule(
        credentials: String,
        url: String,
        message: String,
        sendAt: Int?,
        replyTo: Int?,
        sendWithoutNotification: Boolean,
        threadTitle: String?,
        threadId: Long?
    ) {
        _rescheduleState.value = ScheduledMessageActionLoadingState
        viewModelScope.launch {
            chatRepository.updateScheduledChatMessage(
                credentials,
                url,
                message,
                sendAt,
                replyTo,
                sendWithoutNotification,
                threadTitle,
                threadId
            ).collect { result ->
                if (result.isSuccess) {
                    _rescheduleState.value =
                        ScheduledMessageActionSuccessState(result.getOrNull())
                } else {
                    _rescheduleState.value =
                        ScheduledMessageErrorState(result.exceptionOrNull())
                }
            }
        }
    }

    @Suppress("LongParameterList")
    fun edit(
        credentials: String,
        url: String,
        message: String,
        sendAt: Int?,
        replyTo: Int?,
        sendWithoutNotification: Boolean,
        threadTitle: String?,
        threadId: Long?
    ) {
        _editState.value = ScheduledMessageActionLoadingState
        viewModelScope.launch {
            chatRepository.updateScheduledChatMessage(
                credentials,
                url,
                message,
                sendAt,
                replyTo,
                sendWithoutNotification,
                threadTitle,
                threadId
            ).collect { result ->
                if (result.isSuccess) {
                    _editState.value =
                        ScheduledMessageActionSuccessState(result.getOrNull())
                } else {
                    _editState.value =
                        ScheduledMessageErrorState(result.exceptionOrNull())
                }
            }
        }
    }

    fun deleteScheduledMessage(credentials: String, url: String) {
        _deleteState.value = ScheduledMessageActionLoadingState
        viewModelScope.launch {
            chatRepository.deleteScheduledChatMessage(credentials, url).collect { result ->
                if (result.isSuccess) {
                    _deleteState.value =
                        ScheduledMessageActionSuccessState()
                } else {
                    _deleteState.value =
                        ScheduledMessageErrorState(result.exceptionOrNull())
                }
            }
        }
    }
}
