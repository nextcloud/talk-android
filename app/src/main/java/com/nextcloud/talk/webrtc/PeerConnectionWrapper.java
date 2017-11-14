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
import com.nextcloud.talk.events.SessionDescriptionSendEvent;

import org.greenrobot.eventbus.EventBus;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class PeerConnectionWrapper {
    private static PeerConnection peerConnection;
    private String sessionId;
    private String callToken;
    private String nick;
    private boolean local;
    private MediaConstraints mediaConstraints;
    private DataChannel dataChannel;
    private MagicSdpObserver magicSdpObserver;

    List<IceCandidate> iceCandidates = new ArrayList<>();

    public PeerConnectionWrapper(PeerConnectionFactory peerConnectionFactory,
                                 List<PeerConnection.IceServer> iceServerList,
                                 MediaConstraints mediaConstraints,
                                 MagicPeerConnectionObserver magicPeerConnectionObserver,
                                 String sessionId, boolean isLocalPeer, String callToken) {
        peerConnection = peerConnectionFactory.createPeerConnection(iceServerList, mediaConstraints,
                magicPeerConnectionObserver);
        this.sessionId = sessionId;
        this.local = isLocalPeer;
        this.mediaConstraints = mediaConstraints;
        this.callToken = callToken;
        boolean isInitiator = this.sessionId.compareTo(callToken) < 0;

        magicSdpObserver = new MagicSdpObserver() {
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                peerConnection.setLocalDescription(magicSdpObserver, sessionDescription);
            }

            @Override
            public void onSetSuccess() {
                if (isInitiator) {
                    // For offering peer connection we first create offer and set
                    // local SDP, then after receiving answer set remote SDP.
                    if (peerConnection.getRemoteDescription() == null) {
                        // We've just set our local SDP so time to send it.
                        EventBus.getDefault().post(new SessionDescriptionSendEvent(peerConnection.getLocalDescription(), sessionId,
                                "offer", null));
                    } else {
                        // We've just set remote description, so drain remote
                        // and send local ICE candidates.
                        drainIceCandidates();
                    }
                } else {
                    // For answering peer connection we set remote SDP and then
                    // create answer and set local SDP.
                    if (peerConnection.getLocalDescription() != null) {
                        // We've just set our local SDP so time to send it, drain
                        // remote and send local ICE candidates.
                        EventBus.getDefault().post(new SessionDescriptionSendEvent(peerConnection.getLocalDescription(), sessionId,
                                "answer", null));
                        drainIceCandidates();
                    } else {
                        // We've just set remote SDP - do nothing for now -
                        // answer will be created soon.
                    }
                }
            }

        };

    }

    private void drainIceCandidates() {
        for (IceCandidate iceCandidate : iceCandidates) {
            peerConnection.addIceCandidate(iceCandidate);
        }

        iceCandidates = new ArrayList<>();
    }

    public void addCandidate(IceCandidate iceCandidate) {
        if (peerConnection.getRemoteDescription() != null) {
            // queue
            peerConnection.addIceCandidate(iceCandidate);
        } else {
            iceCandidates.add(iceCandidate);
        }
    }

    public void sendMessage(boolean isAnswer) {

        Log.d("MARIO", "PREPARING " + isAnswer);
        if (!isAnswer) {
            peerConnection.createOffer(magicSdpObserver, mediaConstraints);
        } else {
            peerConnection.createAnswer(magicSdpObserver, mediaConstraints);
        }
    }

    private void sendChannelData(DataChannelMessage dataChannelMessage) {
        ByteBuffer buffer = ByteBuffer.wrap(dataChannelMessage.toString().getBytes());
        dataChannel.send(new DataChannel.Buffer(buffer, false));
    }


    public void sendOffer() {
        DataChannel.Init dcInit = new DataChannel.Init();
        dcInit.negotiated = false;
        dataChannel = peerConnection.createDataChannel("status", dcInit);
        dataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {

            }

            @Override
            public void onStateChange() {
                if (dataChannel.state() == DataChannel.State.OPEN && dataChannel.label().equals("status")) {
                    DataChannelMessage dataChannelMessage = new DataChannelMessage();
                    dataChannelMessage.setType("videoOn");
                    sendChannelData(dataChannelMessage);
                    dataChannelMessage.setType("audioOn");
                    sendChannelData(dataChannelMessage);
                }

            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                ByteBuffer data = buffer.data;
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                final String command = new String(bytes);
            }
        });

        MagicSdpObserver magicSdpObserver = new MagicSdpObserver() {
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                peerConnection.setLocalDescription(new MagicSdpObserver(), sessionDescription);
            }

            @Override
            public void onSetSuccess() {
                EventBus.getDefault().post(new SessionDescriptionSendEvent(peerConnection.getLocalDescription(), sessionId,
                        "offer", null));
            }

        };

        peerConnection.createOffer(magicSdpObserver, mediaConstraints);
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
