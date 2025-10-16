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
 * The state is sent when a connection with another participant is first established (which implicitly broadcasts the
 * initial state when the local participant joins the call, as a connection is established with all the remote
 * participants). Note that, as long as that participant stays in the call, the initial state is not sent again, even
 * after a temporary disconnection; data channels use a reliable transport by default, so even if the state changes
 * while the connection is temporarily interrupted the normal state update messages should be received by the other
 * participant once the connection is restored.
 *
 *
 * Nevertheless, in case of a failed connection and an ICE restart it is unclear whether the data channel messages
 * would be received or not (as the data channel transport may be the one that failed and needs to be restarted).
 * However, the state (except the speaking state) is also sent through signaling messages, which need to be
 * explicitly fetched from the internal signaling server, so even in case of a failed connection they will be
 * eventually received once the remote participant connects again.
 */
import com.nextcloud.talk.activities.ParticipantUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.webrtc.PeerConnection.IceConnectionState
import java.util.concurrent.ConcurrentHashMap

class LocalStateBroadcasterNoMcu(
    private val localCallParticipantModel: LocalCallParticipantModel,
    private val messageSender: MessageSenderNoMcu,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
) : LocalStateBroadcaster(localCallParticipantModel, messageSender) {

    // Map sessionId -> observer wrapper (Flow collector job)
    private val iceConnectionStateObservers = ConcurrentHashMap<String, IceConnectionStateObserver>()

    private inner class IceConnectionStateObserver(val uiState: ParticipantUiState) {
        private var job: Job? = null

        init {
            handleStateChange(uiState)
        }

        private fun handleStateChange(uiState: ParticipantUiState) {
            // Determine ICE connection state
            val iceState = if (uiState.isConnected) IceConnectionState.CONNECTED else IceConnectionState.NEW

            if (iceState == IceConnectionState.CONNECTED) {
                remove()
                sendState(uiState.sessionKey)
            }
        }

        fun remove() {
            job?.cancel()
            iceConnectionStateObservers.remove(uiState.sessionKey)
        }
    }

    override fun handleCallParticipantAdded(uiState: ParticipantUiState) {
        uiState.sessionKey?.let {
            iceConnectionStateObservers[it]?.remove()

            iceConnectionStateObservers[it] =
                IceConnectionStateObserver(uiState)
        }
    }

    override fun handleCallParticipantRemoved(sessionId: String) {
        iceConnectionStateObservers[sessionId]?.remove()
    }

    override fun destroy() {
        super.destroy()
        // Cancel all collectors safely
        val observersCopy = iceConnectionStateObservers.values.toList()
        for (observer in observersCopy) {
            observer.remove()
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
