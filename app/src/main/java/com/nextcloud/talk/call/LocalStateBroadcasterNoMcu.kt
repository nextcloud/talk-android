/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

/**
 * Helper class to send the local participant state to the other participants in the call when an MCU is not used.
 *
 *
 * Sending the state when it changes is handled by the base class; this subclass only handles sending the initial
 * state when a remote participant is added.
 *
 *
 * The state is sent the first time the ICE connection reaches a "connected" state for a given participant
 * (isConnected transitions from false/unknown to true).  The observer collects the participant's
 * uiState StateFlow so that if the connection is briefly lost and then restored (e.g. an ICE restart),
 * the state is re-sent on the next false → true transition.
 *
 *
 * Note that, as long as a participant stays in the call, the state is sent each time the ICE connection
 * goes from disconnected to connected; data channels use a reliable transport by default, so even if the
 * state changes while the connection is temporarily interrupted the normal state update messages should be
 * received by the other participant once the connection is restored.
 */
import com.nextcloud.talk.activities.ParticipantUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class LocalStateBroadcasterNoMcu(
    private val localCallParticipantModel: LocalCallParticipantModel,
    private val messageSender: MessageSenderNoMcu,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
) : LocalStateBroadcaster(localCallParticipantModel, messageSender) {

    private val iceConnectionStateObservers = ConcurrentHashMap<String, IceConnectionStateObserver>()

    /**
     * Primary entry point called by CallActivity.  Starts (or restarts) collecting the live
     * StateFlow so that the local state is sent every time the ICE connection transitions from
     * disconnected to connected.
     */
    override fun handleCallParticipantAdded(uiStateFlow: StateFlow<ParticipantUiState>) {
        val sessionId = uiStateFlow.value.sessionKey ?: return
        iceConnectionStateObservers[sessionId]?.remove()
        iceConnectionStateObservers[sessionId] = IceConnectionStateObserver(sessionId, uiStateFlow)
    }

    /**
     * Fallback for callers that only have a snapshot (e.g. tests that pre-date the StateFlow API).
     * Wraps the snapshot in a single-value StateFlow and delegates to the primary overload.
     */
    override fun handleCallParticipantAdded(uiState: ParticipantUiState) {
        handleCallParticipantAdded(MutableStateFlow(uiState) as StateFlow<ParticipantUiState>)
    }

    override fun handleCallParticipantRemoved(sessionId: String) {
        iceConnectionStateObservers[sessionId]?.remove()
    }

    override fun destroy() {
        super.destroy()
        val observersCopy = iceConnectionStateObservers.values.toList()
        for (observer in observersCopy) {
            observer.remove()
        }
    }

    private inner class IceConnectionStateObserver(
        private val sessionId: String,
        uiStateFlow: StateFlow<ParticipantUiState>
    ) {
        private val job: Job = scope.launch {
            var previousIsConnected: Boolean? = null
            uiStateFlow.collect { uiState ->
                val currentIsConnected = uiState.isConnected
                // Send state on every false → true (or null → true) transition so that the
                // remote participant receives the local state as soon as the data channel is ready.
                if (currentIsConnected && previousIsConnected != true) {
                    sendState(uiState.sessionKey)
                }
                previousIsConnected = currentIsConnected
            }
        }

        fun remove() {
            job.cancel()
            iceConnectionStateObservers.remove(sessionId)
        }
    }

    private fun sendState(sessionKey: String?) {
        messageSender.send(getDataChannelMessageForAudioState(), sessionKey)
        messageSender.send(getDataChannelMessageForSpeakingState(), sessionKey)
        messageSender.send(getDataChannelMessageForVideoState(), sessionKey)

        messageSender.send(getSignalingMessageForAudioState(), sessionKey)
        messageSender.send(getSignalingMessageForVideoState(), sessionKey)
    }
}
