/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.signaling.SignalingMessageReceiver.CallParticipantMessageListener
import com.nextcloud.talk.webrtc.PeerConnectionWrapper
import com.nextcloud.talk.webrtc.PeerConnectionWrapper.DataChannelMessageListener
import com.nextcloud.talk.webrtc.PeerConnectionWrapper.PeerConnectionObserver
import org.webrtc.MediaStream
import org.webrtc.PeerConnection.IceConnectionState
import java.lang.Boolean
import kotlin.Long
import kotlin.String

/**
 * Model for (remote) call participants.
 *
 *
 * This class keeps track of the state changes in a call participant and updates its data model as needed. View classes
 * are expected to directly use the read-only data model.
 */
class CallParticipant(sessionId: String?, private val signalingMessageReceiver: SignalingMessageReceiver?) {
    private val callParticipantMessageListener: CallParticipantMessageListener =
        object : CallParticipantMessageListener {
            override fun onRaiseHand(state: kotlin.Boolean, timestamp: Long) {
                callParticipantModel.update(raisedHand = RaisedHand(state, timestamp))
            }

            override fun onReaction(reaction: String?) {
                // callParticipantModel.emitReaction(reaction) TODO
            }

            override fun onUnshareScreen() {
            }
        }

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
            callParticipantModel.update(mediaStream = mediaStream)
        }

        override fun onStreamRemoved(mediaStream: MediaStream?) {
            callParticipantModel.update(mediaStream = null)
        }

        override fun onIceConnectionStateChanged(iceConnectionState: IceConnectionState?) {
            callParticipantModel.update(iceState = iceConnectionState)
        }
    }

    // DataChannel messages are sent only in video peers; (sender) screen peers do not even open them.
    private val dataChannelMessageListener: DataChannelMessageListener = object : DataChannelMessageListener {
        override fun onAudioOn() {
            callParticipantModel.update(isAudioEnabled = true)
        }

        override fun onAudioOff() {
            callParticipantModel.update(isAudioEnabled = false)
        }

        override fun onVideoOn() {
            callParticipantModel.update(isStreamEnabled = true)
        }

        override fun onVideoOff() {
            callParticipantModel.update(isStreamEnabled = false)
        }

        override fun onNickChanged(nick: String?) {
            callParticipantModel.update(
                nick
            )
        }
    }

    val callParticipantModel: CallParticipantModel = CallParticipantModel(sessionId, null)

    private var peerConnectionWrapper: PeerConnectionWrapper? = null
    private var screenPeerConnectionWrapper: PeerConnectionWrapper? = null

    init {
        signalingMessageReceiver?.addListener(callParticipantMessageListener, sessionId)
    }

    fun destroy() {
        signalingMessageReceiver?.removeListener(callParticipantMessageListener)

        if (peerConnectionWrapper != null) {
            peerConnectionWrapper!!.removeObserver(peerConnectionObserver)
            peerConnectionWrapper!!.removeListener(dataChannelMessageListener)
        }
        if (screenPeerConnectionWrapper != null) {
            screenPeerConnectionWrapper!!.removeObserver(screenPeerConnectionObserver)
        }
    }

    fun setActor(actorType: Participant.ActorType?, actorId: String?) {
        callParticipantModel.update(
            actorType = actorType,
            actorId = actorId
        )
    }

    fun setUserId(userId: String?) {
        callParticipantModel.update(userId = userId)
    }

    fun setNick(nick: String?) {
        callParticipantModel.update(
            nick = nick
        )
    }

    fun setInternal(internal: kotlin.Boolean?) {
        callParticipantModel.update(isInternal = internal)
    }

    fun setPeerConnectionWrapper(peerConnectionWrapper: PeerConnectionWrapper?) {
        if (this.peerConnectionWrapper != null) {
            this.peerConnectionWrapper!!.removeObserver(peerConnectionObserver)
            this.peerConnectionWrapper!!.removeListener(dataChannelMessageListener)
        }

        this.peerConnectionWrapper = peerConnectionWrapper

        if (this.peerConnectionWrapper == null) {
            callParticipantModel.update(iceState = null)
            callParticipantModel.update(mediaStream = null)
            callParticipantModel.update(isAudioEnabled = null)
            callParticipantModel.update(isStreamEnabled = null)

            return
        }

        handleIceConnectionStateChange(this.peerConnectionWrapper!!.peerConnection.iceConnectionState())
        handleStreamChange(this.peerConnectionWrapper!!.stream)

        this.peerConnectionWrapper!!.addObserver(peerConnectionObserver)
        this.peerConnectionWrapper!!.addListener(dataChannelMessageListener)
    }

    private fun handleIceConnectionStateChange(iceConnectionState: IceConnectionState?) {
        callParticipantModel.update(iceState = iceConnectionState)

        if (iceConnectionState == IceConnectionState.NEW ||
            iceConnectionState == IceConnectionState.CHECKING
        ) {
            callParticipantModel.update(isAudioEnabled = null)
            callParticipantModel.update(isStreamEnabled = null)
        }
    }

    private fun handleStreamChange(mediaStream: MediaStream?) {
        if (mediaStream == null) {
            callParticipantModel.update(mediaStream = null)
            callParticipantModel.update(isStreamEnabled = false)

            return
        }

        val hasAtLeastOneVideoStream = mediaStream.videoTracks != null && !mediaStream.videoTracks.isEmpty()

        callParticipantModel.update(mediaStream = mediaStream)
        callParticipantModel.update(isStreamEnabled = hasAtLeastOneVideoStream)
    }

    fun setScreenPeerConnectionWrapper(screenPeerConnectionWrapper: PeerConnectionWrapper?) {
        if (this.screenPeerConnectionWrapper != null) {
            this.screenPeerConnectionWrapper!!.removeObserver(screenPeerConnectionObserver)
        }

        this.screenPeerConnectionWrapper = screenPeerConnectionWrapper

        if (this.screenPeerConnectionWrapper == null) {
            callParticipantModel.update(iceState = null)
            callParticipantModel.update(mediaStream = null)

            return
        }

        callParticipantModel.update(
            iceState = this.screenPeerConnectionWrapper!!.peerConnection.iceConnectionState()
        )
        callParticipantModel.update(mediaStream = this.screenPeerConnectionWrapper!!.stream)

        this.screenPeerConnectionWrapper!!.addObserver(screenPeerConnectionObserver)
    }
}
