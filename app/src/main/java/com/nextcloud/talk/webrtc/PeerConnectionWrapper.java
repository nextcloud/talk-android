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
import org.webrtc.SessionDescription;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class PeerConnectionWrapper {
    private static String TAG = "PeerConnectionWrapper";
    private static PeerConnection peerConnection;
    List<IceCandidate> iceCandidates = new ArrayList<>();
    List<PeerConnection.IceServer> iceServers;
    List<IceCandidate> candidatesToSend = new ArrayList<>();
    List<SessionDescription> sessionDescriptionsQueue = new ArrayList<>();
    List<NCIceCandidate> localCandidates = new ArrayList<>();
    List<SessionDescriptionSendEvent> sessionDescriptionSendEvents = new ArrayList<>();
    private String sessionId;
    private String callToken;
    private String nick;
    private boolean local;
    private MediaConstraints mediaConstraints;
    private DataChannel dataChannel;
    private MagicSdpObserver magicSdpObserver;
    private MagicPeerConnectionObserver magicPeerConnectionObserver;
    private boolean isInitiator;

    public PeerConnectionWrapper(PeerConnectionFactory peerConnectionFactory,
                                 List<PeerConnection.IceServer> iceServerList,
                                 MediaConstraints mediaConstraints,
                                 String sessionId, boolean isLocalPeer, String callToken) {

        this.iceServers = iceServerList;

        magicPeerConnectionObserver = new MagicPeerConnectionObserver() {
            @Override
            public void onIceConnectionReceivingChange(boolean b) {

            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d("MARIO_ICE", iceConnectionState.name());
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                EventBus.getDefault().post(new MediaStreamEvent(mediaStream));
            }

            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d("MARIO", signalingState.name());
            }


            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                /*if (iceGatheringState.equals(PeerConnection.IceGatheringState.COMPLETE)) {
                    sendLocalCandidates();
                }*/
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                NCIceCandidate ncIceCandidate = new NCIceCandidate();
                ncIceCandidate.setSdpMid(iceCandidate.sdpMid);
                ncIceCandidate.setSdpMLineIndex(iceCandidate.sdpMLineIndex);
                ncIceCandidate.setCandidate(iceCandidate.sdp);
                if (peerConnection.getRemoteDescription() == null) {
                    localCandidates.add(ncIceCandidate);
                } else {
                    EventBus.getDefault().post(new SessionDescriptionSendEvent(null, sessionId,
                            "candidate", ncIceCandidate));
                }
            }

        };

        peerConnection = peerConnectionFactory.createPeerConnection(iceServerList, mediaConstraints,
                magicPeerConnectionObserver);

        this.sessionId = sessionId;
        this.local = isLocalPeer;
        this.mediaConstraints = mediaConstraints;
        this.callToken = callToken;
        isInitiator = this.sessionId.compareTo(callToken) < 0;

        magicSdpObserver = new MagicSdpObserver() {
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
                super.onCreateSuccess(sessionDescription);
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
                } else {
                    drainIceCandidates();
                    sendLocalCandidates();
                }
            }

        };

    }


    public void sendLocalCandidates() {
        for (NCIceCandidate ncIceCandidate : localCandidates) {
            EventBus.getDefault().post(new SessionDescriptionSendEvent(null, sessionId,
                    "candidate", ncIceCandidate));
        }

        localCandidates = new ArrayList<>();
    }

    public void drainIceCandidates() {
        Log.d("MARIO", "DRAINING");

        for (IceCandidate iceCandidate : iceCandidates) {
            peerConnection.addIceCandidate(iceCandidate);
        }

        iceCandidates = new ArrayList<>();

    }

    public MagicSdpObserver getMagicSdpObserver() {
        return magicSdpObserver;
    }

    public void addCandidate(IceCandidate iceCandidate) {
        Log.d("MARIO", "RECEIVING CANDIDATE");
        if (peerConnection.getRemoteDescription() != null) {
            Log.d("MARIO", "DIRECT ADDING");
            peerConnection.addIceCandidate(iceCandidate);
        } else {
            Log.d("MARIO", "DIRECT QUEUE");
            iceCandidates.add(iceCandidate);
        }
    }

    private void sendChannelData(DataChannelMessage dataChannelMessage) {
        ByteBuffer buffer = ByteBuffer.wrap(dataChannelMessage.toString().getBytes());
        dataChannel.send(new DataChannel.Buffer(buffer, false));
    }


    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public PeerConnection getPeerConnection() {
        return peerConnection;
    }

    public static void setPeerConnection(PeerConnection peerConnection) {
        PeerConnectionWrapper.peerConnection = peerConnection;
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
}
