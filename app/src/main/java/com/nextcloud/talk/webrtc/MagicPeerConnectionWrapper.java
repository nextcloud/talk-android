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

package com.nextcloud.talk.webrtc;

import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.api.models.json.signaling.DataChannelMessage;
import com.nextcloud.talk.api.models.json.signaling.NCIceCandidate;
import com.nextcloud.talk.events.MediaStreamEvent;
import com.nextcloud.talk.events.PeerConnectionEvent;
import com.nextcloud.talk.events.SessionDescriptionSendEvent;

import org.greenrobot.eventbus.EventBus;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MagicPeerConnectionWrapper {
    private static String TAG = "MagicPeerConnectionWrapper";
    List<IceCandidate> iceCandidates = new ArrayList<>();
    private PeerConnection peerConnection;
    private List<PeerConnection.IceServer> iceServers;
    private String sessionId;
    private String localSession;
    private String nick;
    private MediaConstraints mediaConstraints;
    private DataChannel magicDataChannel;
    private MagicSdpObserver magicSdpObserver;

    private boolean remoteVideoOn;
    private boolean remoteAudioOn;

    private boolean hasInitiated;

    private MediaStream localMediaStream;

    public MagicPeerConnectionWrapper(PeerConnectionFactory peerConnectionFactory,
                                      List<PeerConnection.IceServer> iceServerList,
                                      MediaConstraints mediaConstraints,
                                      String sessionId, String localSession, MediaStream mediaStream) {

        this.iceServers = iceServerList;
        this.localSession = localSession;
        this.localMediaStream = mediaStream;

        peerConnection = peerConnectionFactory.createPeerConnection(iceServerList, mediaConstraints,
                new MagicPeerConnectionObserver());
        peerConnection.addStream(localMediaStream);

        this.sessionId = sessionId;
        this.mediaConstraints = mediaConstraints;

        magicSdpObserver = new MagicSdpObserver();
        hasInitiated = sessionId.compareTo(localSession) < 0;
        if (hasInitiated) {
            DataChannel.Init init = new DataChannel.Init();
            init.negotiated = false;
            magicDataChannel = peerConnection.createDataChannel("status", init);
            magicDataChannel.registerObserver(new MagicDataChannelObserver());
            peerConnection.createOffer(magicSdpObserver, mediaConstraints);
        }
    }

    public void removePeerConnection(boolean isFinal) {
        if (peerConnection != null && localMediaStream != null) {
            peerConnection.close();
            peerConnection.removeStream(localMediaStream);
            if (isFinal) {
                peerConnection.dispose();
            }
            peerConnection = null;
        }
    }

    public void drainIceCandidates() {

        for (IceCandidate iceCandidate : iceCandidates) {
            peerConnection.addIceCandidate(iceCandidate);
        }

        iceCandidates = new ArrayList<>();

    }

    public MagicSdpObserver getMagicSdpObserver() {
        return magicSdpObserver;
    }

    public void addCandidate(IceCandidate iceCandidate) {
        if (peerConnection.getRemoteDescription() != null) {
            peerConnection.addIceCandidate(iceCandidate);
        } else {
            iceCandidates.add(iceCandidate);
        }
    }

    public void sendChannelData(DataChannelMessage dataChannelMessage) {
        ByteBuffer buffer = null;
        if (magicDataChannel != null) {
            try {
                buffer = ByteBuffer.wrap(LoganSquare.serialize(dataChannelMessage).getBytes());
                magicDataChannel.send(new DataChannel.Buffer(buffer, false));
            } catch (IOException e) {
                Log.d(TAG, "Failed to send channel data");
            }
        }
    }

    public PeerConnection getPeerConnection() {
        return peerConnection;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getNick() {
        if (nick != null) {
            return nick;
        } else {
            return "";
        }
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    private void sendInitialMediaStatus() {
        if (localMediaStream.videoTracks.size() == 1 && localMediaStream.videoTracks.get(0).enabled()) {
            sendChannelData(new DataChannelMessage("videoOn"));
        } else {
            sendChannelData(new DataChannelMessage("videoOff"));
        }

        if (localMediaStream.audioTracks.size() == 1 && localMediaStream.audioTracks.get(0).enabled()) {
            sendChannelData(new DataChannelMessage("audioOn"));
        } else {
            sendChannelData(new DataChannelMessage("audioOff"));
        }
    }

    private class MagicDataChannelObserver implements DataChannel.Observer {

        @Override
        public void onBufferedAmountChange(long l) {

        }

        @Override
        public void onStateChange() {
            if (magicDataChannel.state().equals(DataChannel.State.OPEN) &&
                    magicDataChannel.label().equals("status")) {
                sendInitialMediaStatus();
            }
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            if (buffer.binary) {
                Log.d(TAG, "Received binary msg over " + TAG + " " + sessionId);
                return;
            }

            ByteBuffer data = buffer.data;
            final byte[] bytes = new byte[data.capacity()];
            data.get(bytes);
            String strData = new String(bytes);
            Log.d(TAG, "Got msg: " + strData + " over " + TAG + " " + sessionId);

            try {
                DataChannelMessage dataChannelMessage = LoganSquare.parse(strData, DataChannelMessage.class);

                if ("nickChanged".equals(dataChannelMessage.getType())) {
                    nick = dataChannelMessage.getPayload();
                    EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                            .NICK_CHANGE, sessionId, nick, null));
                } else if ("audioOn".equals(dataChannelMessage.getType())) {
                    remoteAudioOn = true;
                    EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                            .AUDIO_CHANGE, sessionId, null, remoteAudioOn));
                } else if ("audioOff".equals(dataChannelMessage.getType())) {
                    remoteAudioOn = false;
                    EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                            .AUDIO_CHANGE, sessionId, null, remoteAudioOn));
                } else if ("videoOn".equals(dataChannelMessage.getType())) {
                    remoteVideoOn = true;
                    EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                            .VIDEO_CHANGE, sessionId, null, remoteVideoOn));
                } else if ("videoOff".equals(dataChannelMessage.getType())) {
                    remoteVideoOn = false;
                    EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                            .VIDEO_CHANGE, sessionId, null, remoteVideoOn));
                }
            } catch (IOException e) {
                Log.d(TAG, "Failed to parse data channel message");
            }
        }
    }

    private class MagicPeerConnectionObserver implements PeerConnection.Observer {
        private final String TAG = "MagicPeerConnectionObserver";

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            if (signalingState.equals(PeerConnection.SignalingState.CLOSED)) {
                EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                        .CLOSE_PEER, sessionId, null, null));
            }
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            if (iceConnectionState.equals(PeerConnection.IceConnectionState.CONNECTED) && hasInitiated) {
                sendInitialMediaStatus();
            } else if (iceConnectionState.equals(PeerConnection.IceConnectionState.FAILED)) {
                EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                        .CLOSE_PEER, sessionId, null, null));
            }
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            NCIceCandidate ncIceCandidate = new NCIceCandidate();
            ncIceCandidate.setSdpMid(iceCandidate.sdpMid);
            ncIceCandidate.setSdpMLineIndex(iceCandidate.sdpMLineIndex);
            ncIceCandidate.setCandidate(iceCandidate.sdp);
            EventBus.getDefault().post(new SessionDescriptionSendEvent(null, sessionId,
                    "candidate", ncIceCandidate));
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            EventBus.getDefault().post(new MediaStreamEvent(mediaStream, sessionId));
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            EventBus.getDefault().post(new MediaStreamEvent(null, sessionId));
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            if (dataChannel.label().equals("status")) {
                magicDataChannel = dataChannel;
                magicDataChannel.registerObserver(new MagicDataChannelObserver());
            }
        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        }
    }

    private class MagicSdpObserver implements SdpObserver {
        private final String TAG = "MagicSdpObserver";

        @Override
        public void onCreateFailure(String s) {
            Log.d(TAG, s);
        }

        @Override
        public void onSetFailure(String s) {
            Log.d(TAG, s);
        }

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            String sessionDescriptionStringWithPreferredCodec = MagicWebRTCUtils.preferCodec
                    (sessionDescription.description,
                            "VP8", false);

            SessionDescription sessionDescriptionWithPreferredCodec = new SessionDescription(
                    sessionDescription.type,
                    sessionDescriptionStringWithPreferredCodec);

            EventBus.getDefault().post(new SessionDescriptionSendEvent(sessionDescriptionWithPreferredCodec, sessionId,
                    sessionDescription.type.canonicalForm().toLowerCase(), null));
            peerConnection.setLocalDescription(magicSdpObserver, sessionDescriptionWithPreferredCodec);
        }

        @Override
        public void onSetSuccess() {
            if (peerConnection != null) {
                if (peerConnection.getLocalDescription() == null) {
                    peerConnection.createAnswer(magicSdpObserver, mediaConstraints);
                }

                if (peerConnection.getRemoteDescription() != null) {
                    drainIceCandidates();
                }
            }
        }
    }
}
