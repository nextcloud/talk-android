/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ConversationCreationViewModel @Inject constructor(
    private val repository: ConversationCreationRepository
) : ViewModel() {

    private val _roomName = MutableStateFlow("")
    val roomName: StateFlow<String> = _roomName
    private val _conversationDescription = MutableStateFlow("")
    val conversationDescription: StateFlow<String> = _conversationDescription
    var isGuestsAllowed = mutableStateOf(false)
    var isConversationAvailableForRegisteredUsers = mutableStateOf(false)
    var openForGuestAppUsers = mutableStateOf(false)

    fun updateRoomName(roomName: String) {
        _roomName.value = roomName
    }

    fun updateConversationDescription(conversationDescription: String) {
        _conversationDescription.value = conversationDescription
    }

    fun renameConversation(roomToken: String) {
        viewModelScope.launch {
            try {
                repository.renameConversation(roomToken, roomName.value)
            } catch (e: Exception) {
                Log.d("ConversationCreationViewModel", "${e.message}")
            }
        }
    }
    fun setConversationDescription(roomToken: String) {
        viewModelScope.launch {
            try {
                repository.setConversationDescription(roomToken, conversationDescription.value)
            } catch (e: Exception) {
                Log.d("ConversationCreationViewModel", "${e.message}")
            }
        }
    }
}
