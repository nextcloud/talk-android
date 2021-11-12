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

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.MediaStreamEvent;
import com.nextcloud.talk.events.PeerConnectionEvent;
import com.nextcloud.talk.events.SessionDescriptionSendEvent;
import com.nextcloud.talk.events.WebSocketCommunicationEvent;
import com.nextcloud.talk.models.json.signaling.DataChannelMessage;
import com.nextcloud.talk.models.json.signaling.DataChannelMessageNick;
import com.nextcloud.talk.models.json.signaling.NCIceCandidate;

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
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import autodagger.AutoInjector;

@AutoInjector(NextcloudTalkApplication.class)
public class MagicPeerConnectionWrapper {
    private static final String TAG = "MagicPeerConWrapper";

    private List<IceCandidate> iceCandidates = new ArrayList<>();
    private PeerConnection peerConnection;
    private String sessionId;
    private String nick;
    private MediaConstraints sdpConstraints;
    private DataChannel magicDataChannel;
    private MagicSdpObserver magicSdpObserver;
    private MediaStream remoteMediaStream;

    private boolean remoteVideoOn;
    private boolean remoteAudioOn;

    private boolean hasInitiated;

    private MediaStream localMediaStream;
    private boolean isMCUPublisher;
    private boolean hasMCU;
    private String videoStreamType;

    private int connectionAttempts = 0;
    private PeerConnection.IceConnectionState peerIceConnectionState;

    @Inject
    Context context;

    public MagicPeerConnectionWrapper(PeerConnectionFactory peerConnectionFactory,
                                      List<PeerConnection.IceServer> iceServerList,
                                      MediaConstraints sdpConstraints,
                                      String sessionId, String localSession, @Nullable MediaStream mediaStream,
                                      boolean isMCUPublisher, boolean hasMCU, String videoStreamType) {

        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        this.localMediaStream = mediaStream;
        this.videoStreamType = videoStreamType;
        this.hasMCU = hasMCU;

        this.sessionId = sessionId;
        this.sdpConstraints = sdpConstraints;

        magicSdpObserver = new MagicSdpObserver();
        hasInitiated = sessionId.compareTo(localSession) < 0;
        this.isMCUPublisher = isMCUPublisher;
        
        peerConnection = peerConnectionFactory.createPeerConnection(iceServerList, sdpConstraints,
                new MagicPeerConnectionObserver());

        if (peerConnection != null) {
            if (localMediaStream != null) {
                peerConnection.addStream(localMediaStream);
            }

            if (hasMCU || hasInitiated) {
                DataChannel.Init init = new DataChannel.Init();
                init.negotiated = false;
                magicDataChannel = peerConnection.createDataChannel("status", init);
                magicDataChannel.registerObserver(new MagicDataChannelObserver());
                if (isMCUPublisher) {
                    peerConnection.createOffer(magicSdpObserver, sdpConstraints);
                } else if (hasMCU && this.videoStreamType.equals("video")) {
                    // If the connection type is "screen" the client sharing the screen will send an
                    // offer; offers should be requested only for videos.
                    HashMap<String, String> hashMap = new HashMap<>();
                    hashMap.put("sessionId", sessionId);
                    EventBus.getDefault().post(new WebSocketCommunicationEvent("peerReadyForRequestingOffer", hashMap));
                } else if (!hasMCU && hasInitiated) {
                    peerConnection.createOffer(magicSdpObserver, sdpConstraints);
                }
            }
        }
    }

    public String getVideoStreamType() {
        return videoStreamType;
    }

    public void removePeerConnection() {
        if (magicDataChannel != null) {
            magicDataChannel.dispose();
            magicDataChannel = null;
        }

        if (peerConnection != null) {
            if (localMediaStream != null) {
                peerConnection.removeStream(localMediaStream);
            }

            peerConnection.close();
            peerConnection = null;
        }
    }

    public void drainIceCandidates() {

        if (peerConnection != null) {
            for (IceCandidate iceCandidate : iceCandidates) {
                peerConnection.addIceCandidate(iceCandidate);
            }

            iceCandidates = new ArrayList<>();
        }
    }

    public MagicSdpObserver getMagicSdpObserver() {
        return magicSdpObserver;
    }

    public void addCandidate(IceCandidate iceCandidate) {
        if (peerConnection != null && peerConnection.getRemoteDescription() != null) {
            peerConnection.addIceCandidate(iceCandidate);
        } else {
            iceCandidates.add(iceCandidate);
        }
    }

    public void sendNickChannelData(DataChannelMessageNick dataChannelMessage) {
        ByteBuffer buffer;
        if (magicDataChannel != null) {
            try {
                buffer = ByteBuffer.wrap(LoganSquare.serialize(dataChannelMessage).getBytes());
                magicDataChannel.send(new DataChannel.Buffer(buffer, false));
            } catch (IOException e) {
                Log.d(TAG, "Failed to send channel data, attempting regular " + dataChannelMessage);
            }
        }
    }

    public void sendChannelData(DataChannelMessage dataChannelMessage) {
        ByteBuffer buffer;
        if (magicDataChannel != null) {
            try {
                buffer = ByteBuffer.wrap(LoganSquare.serialize(dataChannelMessage).getBytes());
                magicDataChannel.send(new DataChannel.Buffer(buffer, false));
            } catch (IOException e) {
                Log.d(TAG, "Failed to send channel data, attempting regular " + dataChannelMessage);
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
        if (!TextUtils.isEmpty(nick)) {
            return nick;
        } else {
            return NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_nick_guest);
        }
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    private void sendInitialMediaStatus() {
        if (localMediaStream != null) {
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
    }

    public boolean isMCUPublisher() {
        return isMCUPublisher;
    }

    private class MagicDataChannelObserver implements DataChannel.Observer {

        @Override
        public void onBufferedAmountChange(long l) {

        }

        @Override
        public void onStateChange() {
            if (magicDataChannel != null && magicDataChannel.state().equals(DataChannel.State.OPEN) &&
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

                String internalNick;
                if ("nickChanged".equals(dataChannelMessage.getType())) {
                    if (dataChannelMessage.getPayload() instanceof String) {
                        internalNick = (String) dataChannelMessage.getPayload();
                        if (!internalNick.equals(nick)) {
                            setNick(internalNick);
                            EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                                    .NICK_CHANGE, sessionId, getNick(), null, videoStreamType));
                        }
                    } else {
                        if (dataChannelMessage.getPayload() != null) {
                            HashMap<String, String> payloadHashMap = (HashMap<String, String>) dataChannelMessage.getPayload();
                            EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                                    .NICK_CHANGE, sessionId, payloadHashMap.get("name"), null, videoStreamType));
                        }
                    }

                } else if ("audioOn".equals(dataChannelMessage.getType())) {
                    remoteAudioOn = true;
                    EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                            .AUDIO_CHANGE, sessionId, null, remoteAudioOn, videoStreamType));
                } else if ("audioOff".equals(dataChannelMessage.getType())) {
                    remoteAudioOn = false;
                    EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                            .AUDIO_CHANGE, sessionId, null, remoteAudioOn, videoStreamType));
                } else if ("videoOn".equals(dataChannelMessage.getType())) {
                    remoteVideoOn = true;
                    EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                            .VIDEO_CHANGE, sessionId, null, remoteVideoOn, videoStreamType));
                } else if ("videoOff".equals(dataChannelMessage.getType())) {
                    remoteVideoOn = false;
                    EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                            .VIDEO_CHANGE, sessionId, null, remoteVideoOn, videoStreamType));
                }
            } catch (IOException e) {
                Log.d(TAG, "Failed to parse data channel message");
            }
        }
    }

    private void restartIce() {
        if (connectionAttempts <= 5) {
            if (!hasMCU || isMCUPublisher) {
                MediaConstraints.KeyValuePair iceRestartConstraint =
                        new MediaConstraints.KeyValuePair("IceRestart", "true");

                if (sdpConstraints.mandatory.contains(iceRestartConstraint)) {
                    sdpConstraints.mandatory.add(iceRestartConstraint);
                }

                peerConnection.createOffer(magicSdpObserver, sdpConstraints);
            } else {
                // we have an MCU and this is not the publisher
                // Do something if we have an MCU
            }

            connectionAttempts++;
        }
    }

    private class MagicPeerConnectionObserver implements PeerConnection.Observer {

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            peerIceConnectionState = iceConnectionState;

            Log.d("iceConnectionChangeTo: ", iceConnectionState.name() + " over " + peerConnection.hashCode() + " " + sessionId);
            if (iceConnectionState.equals(PeerConnection.IceConnectionState.CONNECTED)) {
                connectionAttempts = 0;
                /*EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                        .PEER_CONNECTED, sessionId, null, null));*/

                if (!isMCUPublisher) {
                    EventBus.getDefault().post(new MediaStreamEvent(remoteMediaStream, sessionId, videoStreamType));
                }

                if (hasInitiated) {
                    sendInitialMediaStatus();
                }

            } else if (iceConnectionState.equals(PeerConnection.IceConnectionState.CLOSED)) {
                EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                        .PEER_CLOSED, sessionId, null, null, videoStreamType));
                connectionAttempts = 0;
            } else if (iceConnectionState.equals(PeerConnection.IceConnectionState.FAILED)) {
                /*if (MerlinTheWizard.isConnectedToInternet() && connectionAttempts < 5) {
                    restartIce();
                }*/
                if (isMCUPublisher) {
                    EventBus.getDefault().post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType.PUBLISHER_FAILED, sessionId, null, null, null));
                }
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
                    "candidate", ncIceCandidate, videoStreamType));
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            remoteMediaStream = mediaStream;
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            if (!isMCUPublisher) {
                EventBus.getDefault().post(new MediaStreamEvent(null, sessionId, videoStreamType));
            }
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            if (dataChannel.label().equals("status") || dataChannel.label().equals("JanusDataChannel")) {
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
        private static final String TAG = "MagicSdpObserver";

        @Override
        public void onCreateFailure(String s) {
            Log.d(TAG, "SDPObserver createFailure: " + s + " over " + peerConnection.hashCode() + " " + sessionId);

        }

        @Override
        public void onSetFailure(String s) {
            Log.d(TAG,"SDPObserver setFailure: " + s + " over " + peerConnection.hashCode() + " " + sessionId);
        }

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            SessionDescription sessionDescriptionWithPreferredCodec;
            String sessionDescriptionStringWithPreferredCodec = MagicWebRTCUtils.preferCodec
                    (sessionDescription.description,
                            "H264", false);
            sessionDescriptionWithPreferredCodec = new SessionDescription(
                    sessionDescription.type,
                    sessionDescriptionStringWithPreferredCodec);


            EventBus.getDefault().post(new SessionDescriptionSendEvent(sessionDescriptionWithPreferredCodec, sessionId,
                    sessionDescription.type.canonicalForm().toLowerCase(), null, videoStreamType));

            if (peerConnection != null) {
                peerConnection.setLocalDescription(magicSdpObserver, sessionDescriptionWithPreferredCodec);
            }
        }

        @Override
        public void onSetSuccess() {
            if (peerConnection != null) {
                if (peerConnection.getLocalDescription() == null) {
                    peerConnection.createAnswer(magicSdpObserver, sdpConstraints);
                }

                if (peerConnection.getRemoteDescription() != null) {
                    drainIceCandidates();
                }
            }
        }
    }

    public PeerConnection.IceConnectionState getPeerIceConnectionState() {
        return peerIceConnectionState;
    }
}