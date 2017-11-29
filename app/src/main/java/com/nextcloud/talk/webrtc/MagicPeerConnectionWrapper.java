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
 */

package com.nextcloud.talk.webrtc;

import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.api.models.json.signaling.DataChannelMessage;
import com.nextcloud.talk.api.models.json.signaling.NCIceCandidate;
import com.nextcloud.talk.events.MediaStreamEvent;
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
    private static PeerConnection peerConnection;
    List<IceCandidate> iceCandidates = new ArrayList<>();
    private List<PeerConnection.IceServer> iceServers;
    private List<NCIceCandidate> localCandidates = new ArrayList<>();
    private String sessionId;
    private String localSession;
    private String nick;
    private MediaConstraints mediaConstraints;
    private DataChannel magicDataChannel;
    private MagicSdpObserver magicSdpObserver;

    private boolean audioOn;
    private boolean videoOn;

    public MagicPeerConnectionWrapper(PeerConnectionFactory peerConnectionFactory,
                                      List<PeerConnection.IceServer> iceServerList,
                                      MediaConstraints mediaConstraints,
                                      String sessionId, String localSession) {

        this.iceServers = iceServerList;

        peerConnection = peerConnectionFactory.createPeerConnection(iceServerList, mediaConstraints,
                new MagicPeerConnectionObserver());

        if (sessionId.compareTo(localSession) < 0) {
            DataChannel.Init init = new DataChannel.Init();
            init.negotiated = false;
            magicDataChannel = peerConnection.createDataChannel("status", init);
            magicDataChannel.registerObserver(new MagicDataChannelObserver());
        }

        this.sessionId = sessionId;
        this.localSession = localSession;
        this.mediaConstraints = mediaConstraints;

        magicSdpObserver = new MagicSdpObserver();

    }


    public void sendLocalCandidates() {
        for (NCIceCandidate ncIceCandidate : localCandidates) {
            EventBus.getDefault().post(new SessionDescriptionSendEvent(null, sessionId,
                    "candidate", ncIceCandidate));
        }

        localCandidates = new ArrayList<>();
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

    private void sendChannelData(DataChannelMessage dataChannelMessage) {
        ByteBuffer buffer = null;
        try {
            buffer = ByteBuffer.wrap(LoganSquare.serialize(dataChannelMessage).getBytes());
            magicDataChannel.send(new DataChannel.Buffer(buffer, false));
        } catch (IOException e) {
            Log.d(TAG, "Failed to send channel data");
        }
    }


    public PeerConnection getPeerConnection() {
        return peerConnection;
    }

    public static void setPeerConnection(PeerConnection peerConnection) {
        MagicPeerConnectionWrapper.peerConnection = peerConnection;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    private class MagicDataChannelObserver implements DataChannel.Observer {

        @Override
        public void onBufferedAmountChange(long l) {

        }

        @Override
        public void onStateChange() {
            if (magicDataChannel.state().equals(DataChannel.State.OPEN) &&
                    magicDataChannel.label().equals("status")) {
                sendChannelData(new DataChannelMessage("videoOn"));
                sendChannelData(new DataChannelMessage("audioOn"));
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

            // We use media stream to determine if audio or video is on rather than data
            // channel messages
        }
    }

    private class MagicPeerConnectionObserver implements PeerConnection.Observer {
        private final String TAG = "MagicPeerConnectionObserver";

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d("MARIO", signalingState.name());
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
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
                /*if (peerConnection.getRemoteDescription() == null) {
                    localCandidates.add(ncIceCandidate);
                } else {
                    EventBus.getDefault().post(new SessionDescriptionSendEvent(null, sessionId,
                            "candidate", ncIceCandidate));
                }*/
            EventBus.getDefault().post(new SessionDescriptionSendEvent(null, sessionId,
                    "candidate", ncIceCandidate));
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            videoOn = mediaStream.videoTracks.size() == 1;
            audioOn = mediaStream.audioTracks.size() == 1;
            EventBus.getDefault().post(new MediaStreamEvent(mediaStream, sessionId));
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            videoOn = mediaStream.videoTracks.size() == 1;
            audioOn = mediaStream.audioTracks.size() == 1;
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.d("MARIO", "DATA");
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
            peerConnection.setLocalDescription(magicSdpObserver, sessionDescription);
        }

        @Override
        public void onSetSuccess() {
            if (peerConnection.getRemoteDescription() == null) {
                EventBus.getDefault().post(new SessionDescriptionSendEvent(peerConnection.getLocalDescription(), sessionId,
                        peerConnection.getLocalDescription().type.canonicalForm(), null));

            } else if (peerConnection.getLocalDescription() == null && peerConnection.getRemoteDescription().type
                    .canonicalForm().equals
                            ("offer")) {
                peerConnection.createAnswer(magicSdpObserver, mediaConstraints);
            } else if ((peerConnection.getLocalDescription() != null && peerConnection.getRemoteDescription().type
                    .canonicalForm().equals
                            ("offer"))) {
                EventBus.getDefault().post(new SessionDescriptionSendEvent(peerConnection.getLocalDescription(), sessionId,
                        peerConnection.getLocalDescription().type.canonicalForm(), null));
            } else if (peerConnection.getRemoteDescription() != null) {
                drainIceCandidates();
                sendLocalCandidates();
            }
        }
    }
}
