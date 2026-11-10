/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.call.components.CallViewMode
import com.nextcloud.talk.call.components.initialSpeakerViewOrder
import com.nextcloud.talk.call.components.promoteSpeakers
import com.nextcloud.talk.call.components.sortParticipants
import com.nextcloud.talk.call.components.updateSpeakerViewOrder
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

    private val _isLocalScreenSharing = MutableStateFlow(false)
    val isLocalScreenSharing: StateFlow<Boolean> = _isLocalScreenSharing.asStateFlow()

    private val _isScreenSharingAllowed = MutableStateFlow(false)
    val isScreenSharingAllowed: StateFlow<Boolean> = _isScreenSharingAllowed.asStateFlow()

    private val _speakers = MutableStateFlow<List<String>>(emptyList())

    /** Session keys of participants promoted for having spoken while not visible, most recent first. */
    val speakers: StateFlow<List<String>> = _speakers.asStateFlow()

    private val _viewMode = MutableStateFlow(CallViewMode.GRID)
    val viewMode: StateFlow<CallViewMode> = _viewMode.asStateFlow()

    /** Whether a view mode was set, so the preferred one does not need to be restored again. */
    var hasViewMode = false
        private set

    private val _speakerViewOrder = MutableStateFlow<List<String>>(emptyList())

    /**
     * Session keys of the participants in the speaker view: the promoted participant first, followed by the
     * participants in the participant bar. Only updated while the speaker view is shown.
     */
    val speakerViewOrder: StateFlow<List<String>> = _speakerViewOrder.asStateFlow()

    private var participantSessionKeys = emptyList<String>()
    private var activeSpeakerSessionKeys = emptySet<String>()
    private var visibleSessionKeys = emptySet<String>()

    fun getParticipant(sessionId: String?): ParticipantHandler? {
        if (sessionId == null) {
            Log.w(TAG, "Attempted to get participant with null sessionId.")
            return null
        }
        return participantHandlers[sessionId]
    }

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
                updateParticipants()
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
        // Participants are removed from the signaling thread, the participant state is only updated on the main thread
        viewModelScope.launch {
            updateParticipants()
        }
    }

    private fun updateParticipants() {
        val participants = participantHandlers.values.map { it.uiState.value }
        _participants.value = participants

        val sessionKeys = participants.mapNotNull { it.sessionKey }
        val activeSpeakers = participants.filter { it.isActiveSpeaker }.mapNotNull { it.sessionKey }.toSet()
        if (sessionKeys == participantSessionKeys && activeSpeakers == activeSpeakerSessionKeys) return

        val startedSpeaking = activeSpeakers - activeSpeakerSessionKeys
        val previousActiveSpeakers = activeSpeakerSessionKeys
        participantSessionKeys = sessionKeys
        activeSpeakerSessionKeys = activeSpeakers

        _speakers.value = promoteSpeakers(
            speakers = _speakers.value,
            startedSpeaking = startedSpeaking,
            visibleSessionKeys = visibleSessionKeys,
            participantSessionKeys = sessionKeys.toSet()
        )

        if (_viewMode.value.isSpeakerView) {
            _speakerViewOrder.value = updateSpeakerViewOrder(
                order = _speakerViewOrder.value,
                participantSessionKeys = sessionKeys,
                activeSpeakerSessionKeys = activeSpeakers,
                previousActiveSpeakerSessionKeys = previousActiveSpeakers,
                speakers = _speakers.value
            )
        }
    }

    /** Sets the participants currently shown, so participants that start speaking while not shown are promoted. */
    fun setVisibleSessionKeys(sessionKeys: Set<String>) {
        visibleSessionKeys = sessionKeys
    }

    /** Sets the view mode the user prefers, unless a view mode was already set for this call. */
    fun restoreViewMode(viewMode: CallViewMode) {
        if (!hasViewMode) setViewMode(viewMode)
    }

    fun setViewMode(viewMode: CallViewMode) {
        hasViewMode = true
        val wasSpeakerView = _viewMode.value.isSpeakerView
        _viewMode.value = viewMode
        if (!viewMode.isSpeakerView || wasSpeakerView) return

        val sortedSessionKeys = sortParticipants(_participants.value, _speakers.value).mapNotNull { it.sessionKey }
        _speakerViewOrder.value = initialSpeakerViewOrder(
            participantSessionKeys = sortedSessionKeys,
            activeSpeakerSessionKeys = activeSpeakerSessionKeys,
            previouslyPromotedSessionKey = _speakerViewOrder.value.firstOrNull(),
            speakers = _speakers.value
        )
    }

    fun setActiveScreenShareSession(session: String?) {
        _activeScreenShareSession.value = session?.let {
            participantHandlers[it]?.uiState?.value
        }
    }

    fun setLocalScreenSharing(isSharing: Boolean) {
        _isLocalScreenSharing.value = isSharing
    }

    fun setScreenSharingAllowed(isAllowed: Boolean) {
        _isScreenSharingAllowed.value = isAllowed
    }

    public override fun onCleared() {
        participantHandlers.values.forEach { it.destroy() }
        participantHandlers.clear()
        _participants.value = emptyList()
    }

    companion object {
        private val TAG = CallViewModel::class.java.simpleName
    }
}
