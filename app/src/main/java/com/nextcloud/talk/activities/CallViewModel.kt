/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class CallViewModel @Inject constructor() : ViewModel() {

    private val participantHandlers = mutableMapOf<String, ParticipantHandler>()

    private val _participants = MutableStateFlow<List<ParticipantUiState>>(emptyList())
    val participants: StateFlow<List<ParticipantUiState>> = _participants.asStateFlow()

    private val _activeScreenShareSession = MutableStateFlow<ParticipantUiState?>(null)
    val activeScreenShareSession: StateFlow<ParticipantUiState?> = _activeScreenShareSession.asStateFlow()

    fun getParticipant(sessionId: String?): ParticipantHandler? = participantHandlers[sessionId]

    fun doesParticipantExist(sessionId: String?): Boolean = (participantHandlers.containsKey(sessionId))

    fun addParticipant(sessionId: String, signalingMessageReceiver: SignalingMessageReceiver) {
        if (participantHandlers.containsKey(sessionId)) return

        val participantHandler = ParticipantHandler(sessionId, signalingMessageReceiver)
        participantHandlers[sessionId] = participantHandler

        viewModelScope.launch {
            participantHandler.uiState.collect {
                _participants.value = participantHandlers.values.map { it.uiState.value }
            }
        }
    }

    fun removeParticipant(sessionId: String) {
        participantHandlers[sessionId]?.destroy()
        participantHandlers.remove(sessionId)
        _participants.value = participantHandlers.values.map { it.uiState.value }
    }

    fun setActiveScreenShareSession(session: String?) {
        _activeScreenShareSession.value = session?.let {
            participantHandlers[it]?.uiState?.value
        }
    }

    override fun onCleared() {
        participantHandlers.values.forEach { it.destroy() }
    }
}
