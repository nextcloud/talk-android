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

import com.nextcloud.talk.api.models.json.signaling.DataChannelMessage;
import com.nextcloud.talk.api.models.json.signaling.NCIceCandidate;
import com.nextcloud.talk.events.SessionDescriptionSend;

import org.greenrobot.eventbus.EventBus;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;

import java.nio.ByteBuffer;
import java.util.List;

public class PeerConnectionWrapper {
    private static PeerConnection peerConnection;
    private String sessionId;
    private String nick;
    private boolean local;
    private MediaConstraints mediaConstraints;
    private DataChannel dataChannel;

    public PeerConnectionWrapper(PeerConnectionFactory peerConnectionFactory,
                                 List<PeerConnection.IceServer> iceServerList,
                                 MediaConstraints mediaConstraints,
                                 MagicPeerConnectionObserver magicPeerConnectionObserver,
                                 String sessionId, boolean isLocalPeer) {
        peerConnection = peerConnectionFactory.createPeerConnection(iceServerList, mediaConstraints,
                magicPeerConnectionObserver);
        this.sessionId = sessionId;
        this.local = isLocalPeer;
        this.mediaConstraints = mediaConstraints;
    }

    public void addCandidate(IceCandidate iceCandidate) {
        if (peerConnection.getRemoteDescription() != null) {
            // queue
        } else {
            peerConnection.addIceCandidate(iceCandidate);
        }

        peerConnection.addIceCandidate(iceCandidate);
        NCIceCandidate ncIceCandidate = new NCIceCandidate();
        ncIceCandidate.setType("candidate");
        ncIceCandidate.setSdpMid(iceCandidate.sdpMid);
        ncIceCandidate.setSdpMLineIndex(iceCandidate.sdpMLineIndex);
        ncIceCandidate.setCandidate(iceCandidate.sdp);
        EventBus.getDefault().post(new SessionDescriptionSend(null, sessionId, "candidate",
                ncIceCandidate));
    }

    public void sendAnswer() {

        MagicSdpObserver magicSdpObserver = new MagicSdpObserver() {
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                peerConnection.setLocalDescription(new MagicSdpObserver(), sessionDescription);
                EventBus.getDefault().post(new SessionDescriptionSend(sessionDescription, sessionId, "answer",
                        null));
            }
        };

        peerConnection.createAnswer(magicSdpObserver, mediaConstraints);
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
                EventBus.getDefault().post(new SessionDescriptionSend(sessionDescription, sessionId, "offer", null));
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
