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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class CallViewModel @Inject constructor() : ViewModel() {

    private val participantHandlers: MutableMap<String, ParticipantHandler> = ConcurrentHashMap()

    private val _participants = MutableStateFlow<List<ParticipantUiState>>(emptyList())
    val participants: StateFlow<List<ParticipantUiState>> = _participants.asStateFlow()

    private val _activeScreenShareSession = MutableStateFlow<ParticipantUiState?>(null)
    val activeScreenShareSession: StateFlow<ParticipantUiState?> = _activeScreenShareSession.asStateFlow()

    fun getParticipant(sessionId: String?): ParticipantHandler? = participantHandlers[sessionId]

    fun doesParticipantExist(sessionId: String?): Boolean = (participantHandlers.containsKey(sessionId))

    fun addParticipant(
        baseUrl: String,
        roomToken: String,
        sessionId: String,
        signalingMessageReceiver: SignalingMessageReceiver
    ) {
        if (participantHandlers.containsKey(sessionId)) return

        val participantHandler = ParticipantHandler(
            sessionId,
            baseUrl,
            roomToken,
            signalingMessageReceiver,
            onParticipantShareScreen = {
                onShareScreen(it)
            },
            onParticipantUnshareScreen = {
                onUnshareScreen(it)
            }
        )
        participantHandlers[sessionId] = participantHandler

        viewModelScope.launch {
            participantHandler.uiState.collect {
                _participants.value = participantHandlers.values.map { it.uiState.value }
            }
        }
    }

    fun onShareScreen(sessionId: String?) {
        setActiveScreenShareSession(sessionId)
    }

    fun onUnshareScreen(sessionId: String?) {
        if (_activeScreenShareSession.value?.sessionKey.equals(sessionId)) {
            setActiveScreenShareSession(null)
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

    public override fun onCleared() {
        participantHandlers.values.forEach { it.destroy() }
        participantHandlers.clear()
        _participants.value = emptyList()
    }
}
