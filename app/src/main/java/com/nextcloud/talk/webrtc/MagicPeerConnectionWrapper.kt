/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.nextcloud.talk.webrtc

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.events.MediaStreamEvent
import com.nextcloud.talk.events.PeerConnectionEvent
import com.nextcloud.talk.events.SessionDescriptionSendEvent
import com.nextcloud.talk.events.WebSocketCommunicationEvent
import com.nextcloud.talk.models.json.signaling.DataChannelMessage
import com.nextcloud.talk.models.json.signaling.DataChannelMessageNick
import com.nextcloud.talk.models.json.signaling.NCIceCandidate
import com.nextcloud.talk.utils.LoggingUtils.writeLogEntryToFile
import org.greenrobot.eventbus.EventBus
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.webrtc.*
import org.webrtc.PeerConnection.*
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

class MagicPeerConnectionWrapper(peerConnectionFactory: PeerConnectionFactory,
                                 iceServerList: List<IceServer?>?,
                                 sdpConstraints: MediaConstraints,
                                 sessionId: String, localSession: String?, mediaStream: MediaStream?,
                                 isMCUPublisher: Boolean, hasMCU: Boolean, videoStreamType: String) : KoinComponent {
    val context: Context by inject()
    private var iceCandidates: MutableList<IceCandidate> = ArrayList()
    var peerConnection: PeerConnection?
        private set
    var sessionId: String
    var nick: String? = null
    private val sdpConstraints: MediaConstraints
    private var magicDataChannel: DataChannel? = null
    val magicSdpObserver: MagicSdpObserver
    private var remoteMediaStream: MediaStream? = null
    private var remoteVideoOn = false
    private var remoteAudioOn = false
    private val hasInitiated: Boolean
    private val localMediaStream: MediaStream?
    val isMCUPublisher: Boolean
    private val hasMCU: Boolean
    val videoStreamType: String
    private var connectionAttempts = 0
    var peerIceConnectionState: IceConnectionState? = null
        private set

    fun removePeerConnection() {
        if (magicDataChannel != null) {
            magicDataChannel!!.dispose()
            magicDataChannel = null
        }
        if (peerConnection != null) {
            if (localMediaStream != null) {
                peerConnection!!.removeStream(localMediaStream)
            }
            peerConnection!!.close()
            peerConnection = null
        }
    }

    fun drainIceCandidates() {
        if (peerConnection != null) {
            for (iceCandidate in iceCandidates) {
                peerConnection!!.addIceCandidate(iceCandidate)
            }
            iceCandidates = ArrayList()
        }
    }

    fun addCandidate(iceCandidate: IceCandidate) {
        if (peerConnection != null && peerConnection!!.remoteDescription != null) {
            peerConnection!!.addIceCandidate(iceCandidate)
        } else {
            iceCandidates.add(iceCandidate)
        }
    }

    @SuppressLint("LongLogTag")
    fun sendNickChannelData(dataChannelMessage: DataChannelMessageNick) {
        val buffer: ByteBuffer
        if (magicDataChannel != null) {
            try {
                buffer = ByteBuffer.wrap(LoganSquare.serialize(dataChannelMessage).toByteArray())
                magicDataChannel!!.send(DataChannel.Buffer(buffer, false))
            } catch (e: IOException) {
                Log.d(TAG,
                        "Failed to send channel data, attempting regular $dataChannelMessage")
            }
        }
    }

    @SuppressLint("LongLogTag")
    fun sendChannelData(dataChannelMessage: DataChannelMessage) {
        val buffer: ByteBuffer
        if (magicDataChannel != null) {
            try {
                buffer = ByteBuffer.wrap(LoganSquare.serialize(dataChannelMessage).toByteArray())
                magicDataChannel!!.send(DataChannel.Buffer(buffer, false))
            } catch (e: IOException) {
                Log.d(TAG,
                        "Failed to send channel data, attempting regular $dataChannelMessage")
            }
        }
    }

    private fun sendInitialMediaStatus() {
        if (localMediaStream != null) {
            if (localMediaStream.videoTracks.size == 1 && localMediaStream.videoTracks[0]
                            .enabled()) {
                sendChannelData(DataChannelMessage("videoOn"))
            } else {
                sendChannelData(DataChannelMessage("videoOff"))
            }
            if (localMediaStream.audioTracks.size == 1 && localMediaStream.audioTracks[0]
                            .enabled()) {
                sendChannelData(DataChannelMessage("audioOn"))
            } else {
                sendChannelData(DataChannelMessage("audioOff"))
            }
        }
    }

    private fun restartIce() {
        if (connectionAttempts <= 5) {
            if (!hasMCU || isMCUPublisher) {
                val iceRestartConstraint = MediaConstraints.KeyValuePair("IceRestart", "true")
                if (sdpConstraints.mandatory.contains(iceRestartConstraint)) {
                    sdpConstraints.mandatory.add(iceRestartConstraint)
                }
                peerConnection!!.createOffer(magicSdpObserver, sdpConstraints)
            } else { // we have an MCU and this is not the publisher
// Do something if we have an MCU
            }
            connectionAttempts++
        }
    }

    private inner class MagicDataChannelObserver : DataChannel.Observer {
        override fun onBufferedAmountChange(l: Long) {}
        override fun onStateChange() {
            if (magicDataChannel != null && magicDataChannel!!.state() == DataChannel.State.OPEN && magicDataChannel!!.label() == "status") {
                sendInitialMediaStatus()
            }
        }

        @SuppressLint("LongLogTag")
        override fun onMessage(buffer: DataChannel.Buffer) {
            if (buffer.binary) {
                Log.d(TAG, "Received binary msg over $TAG $sessionId")
                return
            }
            val data = buffer.data
            val bytes = ByteArray(data.capacity())
            data[bytes]
            val strData = String(bytes)
            Log.d(TAG, "Got msg: $strData over $TAG $sessionId")
            writeLogEntryToFile(context,
                    "Got msg: " + strData + " over " + peerConnection.hashCode() + " " + sessionId)
            try {
                val dataChannelMessage = LoganSquare.parse(strData, DataChannelMessage::class.java)
                val internalNick: String
                if ("nickChanged" == dataChannelMessage.type) {
                    if (dataChannelMessage.payload is String) {
                        internalNick = dataChannelMessage.payload as String
                        if (internalNick != nick) {
                            nick = internalNick
                            EventBus.getDefault()
                                    .post(PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType.NICK_CHANGE, sessionId, nick, null, videoStreamType))
                        }
                    } else {
                        if (dataChannelMessage.payload != null) {
                            val payloadHashMap = dataChannelMessage.payload as HashMap<String, String>
                            EventBus.getDefault()
                                    .post(PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType.NICK_CHANGE, payloadHashMap["userid"], payloadHashMap["name"], null,
                                            videoStreamType))
                        }
                    }
                } else if ("audioOn" == dataChannelMessage.type) {
                    remoteAudioOn = true
                    EventBus.getDefault()
                            .post(PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType.AUDIO_CHANGE, sessionId, null, remoteAudioOn, videoStreamType))
                } else if ("audioOff" == dataChannelMessage.type) {
                    remoteAudioOn = false
                    EventBus.getDefault()
                            .post(PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType.AUDIO_CHANGE, sessionId, null, remoteAudioOn, videoStreamType))
                } else if ("videoOn" == dataChannelMessage.type) {
                    remoteVideoOn = true
                    EventBus.getDefault()
                            .post(PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType.VIDEO_CHANGE, sessionId, null, remoteVideoOn, videoStreamType))
                } else if ("videoOff" == dataChannelMessage.type) {
                    remoteVideoOn = false
                    EventBus.getDefault()
                            .post(PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType.VIDEO_CHANGE, sessionId, null, remoteVideoOn, videoStreamType))
                }
            } catch (e: IOException) {
                Log.d(TAG, "Failed to parse data channel message")
            }
        }
    }

    private inner class MagicPeerConnectionObserver : PeerConnection.Observer {
        private val TAG = "MagicPeerConnectionObserver"
        override fun onSignalingChange(signalingState: SignalingState) {}
        override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
            peerIceConnectionState = iceConnectionState
            writeLogEntryToFile(context,
                    "iceConnectionChangeTo: "
                            + iceConnectionState.name
                            + " over "
                            + peerConnection.hashCode()
                            + " "
                            + sessionId)
            Log.d("iceConnectionChangeTo: ",
                    iceConnectionState.name + " over " + peerConnection.hashCode() + " " + sessionId)
            if (iceConnectionState == IceConnectionState.CONNECTED) {
                connectionAttempts = 0
                /*EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                        .PEER_CONNECTED, sessionId, null, null));*/if (!isMCUPublisher) {
                    EventBus.getDefault()
                            .post(MediaStreamEvent(remoteMediaStream, sessionId, videoStreamType))
                }
                if (hasInitiated) {
                    sendInitialMediaStatus()
                }
            } else if (iceConnectionState == IceConnectionState.CLOSED) {
                EventBus.getDefault()
                        .post(PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType.PEER_CLOSED, sessionId, null, null, videoStreamType))
                connectionAttempts = 0
            } else if (iceConnectionState == IceConnectionState.FAILED) { /*if (MerlinTheWizard.isConnectedToInternet() && connectionAttempts < 5) {
                    restartIce();
                }*/
                if (isMCUPublisher) {
                    EventBus.getDefault()
                            .post(PeerConnectionEvent(
                                    PeerConnectionEvent.PeerConnectionEventType.PUBLISHER_FAILED, sessionId, null,
                                    null, null))
                }
            }
        }

        override fun onIceConnectionReceivingChange(b: Boolean) {}
        override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {}
        override fun onIceCandidate(iceCandidate: IceCandidate) {
            val ncIceCandidate = NCIceCandidate()
            ncIceCandidate.sdpMid = iceCandidate.sdpMid
            ncIceCandidate.sdpMLineIndex = iceCandidate.sdpMLineIndex
            ncIceCandidate.candidate = iceCandidate.sdp
            EventBus.getDefault().post(SessionDescriptionSendEvent(null, sessionId,
                    "candidate", ncIceCandidate, videoStreamType))
        }

        override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {}
        override fun onAddStream(mediaStream: MediaStream) {
            remoteMediaStream = mediaStream
        }

        override fun onRemoveStream(mediaStream: MediaStream) {
            if (!isMCUPublisher) {
                EventBus.getDefault().post(MediaStreamEvent(null, sessionId, videoStreamType))
            }
        }

        override fun onDataChannel(dataChannel: DataChannel) {
            if (dataChannel.label() == "status") {
                magicDataChannel = dataChannel
                magicDataChannel!!.registerObserver(MagicDataChannelObserver())
            }
        }

        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<MediaStream>) {}
    }

    inner class MagicSdpObserver : SdpObserver {
        private val TAG = "MagicSdpObserver"
        override fun onCreateFailure(s: String) {
            Log.d(TAG, s)
            writeLogEntryToFile(context,
                    "SDPObserver createFailure: "
                            + s
                            + " over "
                            + peerConnection.hashCode()
                            + " "
                            + sessionId)
        }

        override fun onSetFailure(s: String) {
            Log.d(TAG, s)
            writeLogEntryToFile(context,
                    "SDPObserver setFailure: " + s + " over " + peerConnection.hashCode() + " " + sessionId)
        }

        override fun onCreateSuccess(sessionDescription: SessionDescription) {
            val sessionDescriptionWithPreferredCodec: SessionDescription
            val sessionDescriptionStringWithPreferredCodec = MagicWebRTCUtils.preferCodec(sessionDescription.description,
                    "H264", false)
            sessionDescriptionWithPreferredCodec = SessionDescription(
                    sessionDescription.type,
                    sessionDescriptionStringWithPreferredCodec)
            EventBus.getDefault()
                    .post(SessionDescriptionSendEvent(sessionDescriptionWithPreferredCodec, sessionId,
                            sessionDescription.type.canonicalForm().toLowerCase(), null, videoStreamType))
            if (peerConnection != null) {
                peerConnection!!.setLocalDescription(magicSdpObserver, sessionDescriptionWithPreferredCodec)
            }
        }

        override fun onSetSuccess() {
            if (peerConnection != null) {
                if (peerConnection!!.localDescription == null) {
                    peerConnection!!.createAnswer(magicSdpObserver, sdpConstraints)
                }
                if (peerConnection!!.remoteDescription != null) {
                    drainIceCandidates()
                }
            }
        }
    }

    companion object {
        private const val TAG = "MagicPeerConnectionWrapper"
    }

    init {
        localMediaStream = mediaStream
        this.videoStreamType = videoStreamType
        this.hasMCU = hasMCU
        this.sessionId = sessionId
        this.sdpConstraints = sdpConstraints
        magicSdpObserver = MagicSdpObserver()
        hasInitiated = sessionId.compareTo(localSession!!) < 0
        this.isMCUPublisher = isMCUPublisher
        peerConnection = peerConnectionFactory.createPeerConnection(iceServerList, sdpConstraints,
                MagicPeerConnectionObserver())
        if (peerConnection != null) {
            if (localMediaStream != null) {
                peerConnection!!.addStream(localMediaStream)
            }
            if (hasMCU || hasInitiated) {
                val init = DataChannel.Init()
                init.negotiated = false
                magicDataChannel = peerConnection!!.createDataChannel("status", init)
                magicDataChannel!!.registerObserver(MagicDataChannelObserver())
                if (isMCUPublisher) {
                    peerConnection!!.createOffer(magicSdpObserver, sdpConstraints)
                } else if (hasMCU) {
                    val hashMap = HashMap<String, String>()
                    hashMap["sessionId"] = sessionId
                    EventBus.getDefault()
                            .post(WebSocketCommunicationEvent("peerReadyForRequestingOffer", hashMap))
                } else if (hasInitiated) {
                    peerConnection!!.createOffer(magicSdpObserver, sdpConstraints)
                }
            }
        }
    }
}