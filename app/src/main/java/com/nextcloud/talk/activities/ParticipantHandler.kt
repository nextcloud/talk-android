/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.activities

import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.webrtc.PeerConnectionWrapper
import com.nextcloud.talk.webrtc.PeerConnectionWrapper.DataChannelMessageListener
import com.nextcloud.talk.webrtc.PeerConnectionWrapper.PeerConnectionObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.webrtc.MediaStream
import org.webrtc.PeerConnection.IceConnectionState

class ParticipantHandler(
    private val sessionId: String,
    private val signalingMessageReceiver: SignalingMessageReceiver
) {
    private val _uiState = MutableStateFlow(
        ParticipantUiState(
            sessionKey = sessionId,
            nick = "Guest",
            isConnected = false,
            isAudioEnabled = false,
            isStreamEnabled = false,
            isScreenStreamEnabled = false,
            raisedHand = false,
            isInternal = false
        )
    )
    val uiState: StateFlow<ParticipantUiState> = _uiState.asStateFlow()

    private var peerConnection: PeerConnectionWrapper? = null
    private var screenPeerConnection: PeerConnectionWrapper? = null

    private val peerConnectionObserver: PeerConnectionObserver = object : PeerConnectionObserver {
        override fun onStreamAdded(mediaStream: MediaStream?) {
            handleStreamChange(mediaStream)
        }

        override fun onStreamRemoved(mediaStream: MediaStream?) {
            handleStreamChange(mediaStream)
        }

        override fun onIceConnectionStateChanged(iceConnectionState: IceConnectionState?) {
            handleIceConnectionStateChange(iceConnectionState)
        }
    }

    private val screenPeerConnectionObserver: PeerConnectionObserver = object : PeerConnectionObserver {
        override fun onStreamAdded(mediaStream: MediaStream?) {
            handleScreenStreamChange(mediaStream)
        }

        override fun onStreamRemoved(mediaStream: MediaStream?) {
            handleScreenStreamChange(mediaStream)
        }

        override fun onIceConnectionStateChanged(iceConnectionState: IceConnectionState?) {
            // TODO
            // callParticipantModel.setScreenIceConnectionState(iceConnectionState)
        }
    }

    private fun handleStreamChange(mediaStream: MediaStream?) {
        val hasAtLeastOneVideoStream = mediaStream?.videoTracks?.isNotEmpty() == true

        _uiState.update {
            it.copy(
                mediaStream = mediaStream,
                isStreamEnabled = hasAtLeastOneVideoStream
            )
        }
    }

    private fun handleScreenStreamChange(mediaStream: MediaStream?) {
        val hasAtLeastOneVideoStream = mediaStream?.videoTracks?.isNotEmpty() == true

        _uiState.update {
            it.copy(
                screenMediaStream = mediaStream,
                isScreenStreamEnabled = hasAtLeastOneVideoStream
            )
        }
    }

    private fun handleIceConnectionStateChange(iceConnectionState: IceConnectionState?) {
        if (iceConnectionState == IceConnectionState.NEW ||
            iceConnectionState == IceConnectionState.CHECKING
        ) {
            _uiState.update { it.copy(isAudioEnabled = false) }
            _uiState.update { it.copy(isStreamEnabled = false) }
        }

        _uiState.update { it.copy(isConnected = isConnected(iceConnectionState)) }
    }

    private val dataChannelMessageListener: DataChannelMessageListener = object : DataChannelMessageListener {
        override fun onAudioOn() {
            _uiState.update { it.copy(isAudioEnabled = true) }
        }

        override fun onAudioOff() {
            _uiState.update { it.copy(isAudioEnabled = false) }
        }

        override fun onVideoOn() {
            _uiState.update { it.copy(isStreamEnabled = true) }
        }

        override fun onVideoOff() {
            _uiState.update { it.copy(isStreamEnabled = false) }
        }

        override fun onNickChanged(nick: String?) {
            _uiState.update { it.copy(nick = nick) }
        }
    }

    // --- Signaling listeners ---
    private val listener = object : SignalingMessageReceiver.CallParticipantMessageListener {
        override fun onRaiseHand(state: Boolean, timestamp: Long) {
            _uiState.update { it.copy(raisedHand = state) }
        }

        override fun onReaction(reaction: String?) {
            // TODO: handle reactions
        }

        override fun onUnshareScreen() {
            handleScreenStreamChange(null)
        }
    }

    init {
        signalingMessageReceiver.addListener(listener, sessionId)
    }

    fun setPeerConnection(peerConnection: PeerConnectionWrapper?) {
        this.peerConnection?.let {
            it.removeObserver(peerConnectionObserver)
            it.removeListener(dataChannelMessageListener)
        }

        this.peerConnection = peerConnection

        if (this.peerConnection == null) {
            _uiState.update { it.copy(mediaStream = null) }
            _uiState.update { it.copy(isAudioEnabled = false) }
            _uiState.update { it.copy(isStreamEnabled = false) }
            return
        }

        handleIceConnectionStateChange(this.peerConnection?.peerConnection?.iceConnectionState())
        handleStreamChange(this.peerConnection?.stream)

        this.peerConnection?.addObserver(peerConnectionObserver)
        this.peerConnection?.addListener(dataChannelMessageListener)
    }

    fun setScreenPeerConnection(screenPeerConnectionWrapper: PeerConnectionWrapper?) {
        this.screenPeerConnection?.removeObserver(screenPeerConnectionObserver)

        this.screenPeerConnection = screenPeerConnectionWrapper

        if (this.screenPeerConnection == null) {
            // callParticipantModel.setScreenIceConnectionState(null)
            _uiState.update { it.copy(screenMediaStream = null) }
            return
        }

        _uiState.update { it.copy(screenMediaStream = screenPeerConnection?.stream) }

        this.screenPeerConnection?.addObserver(screenPeerConnectionObserver)
    }

    fun isConnected(iceConnectionState: IceConnectionState?): Boolean =
        iceConnectionState == IceConnectionState.CONNECTED ||
            iceConnectionState == IceConnectionState.COMPLETED ||
            iceConnectionState == null

    fun updateAudio(enabled: Boolean?) = _uiState.update { it.copy(isAudioEnabled = enabled ?: it.isAudioEnabled) }

    fun updateVideo(enabled: Boolean?) = _uiState.update { it.copy(isStreamEnabled = enabled ?: it.isStreamEnabled) }

    fun updateNick(nick: String?) = _uiState.update { it.copy(nick = nick ?: "Guest") }

    fun updateUserId(userId: String?) = _uiState.update { it.copy(userId = userId) }

    fun updateIsInternal(isInternal: Boolean) = _uiState.update { it.copy(isInternal = isInternal) }

    fun updateActor(actorType: Participant.ActorType?, actorId: String?) =
        _uiState.update { it.copy(actorType = actorType, actorId = actorId) }

    fun destroy() {
        signalingMessageReceiver.removeListener(listener)

        if (peerConnection != null) {
            peerConnection!!.removeObserver(peerConnectionObserver)
            peerConnection!!.removeListener(dataChannelMessageListener)
        }
        if (screenPeerConnection != null) {
            screenPeerConnection!!.removeObserver(screenPeerConnectionObserver)
        }

        peerConnection = null
        screenPeerConnection = null
    }
}
