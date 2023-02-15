/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Tim Krüger
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
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
import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.json.signaling.DataChannelMessage;
import com.nextcloud.talk.models.json.signaling.NCIceCandidate;
import com.nextcloud.talk.models.json.signaling.NCMessagePayload;
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;
import com.nextcloud.talk.signaling.SignalingMessageReceiver;
import com.nextcloud.talk.signaling.SignalingMessageSender;

import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import autodagger.AutoInjector;

@AutoInjector(NextcloudTalkApplication.class)
public class PeerConnectionWrapper {

    /**
     * Listener for data channel messages.
     *
     * The messages are bound to a specific peer connection, so each listener is expected to handle messages only for
     * a single peer connection.
     *
     * All methods are called on the so called "signaling" thread of WebRTC, which is an internal thread created by the
     * WebRTC library and NOT the same thread where signaling messages are received.
     */
    public interface DataChannelMessageListener {
        void onAudioOn();
        void onAudioOff();
        void onVideoOn();
        void onVideoOff();
        void onNickChanged(String nick);
    }

    /**
     * Observer for changes on the peer connection.
     *
     * The changes are bound to a specific peer connection, so each observer is expected to handle messages only for
     * a single peer connection.
     *
     * All methods are called on the so called "signaling" thread of WebRTC, which is an internal thread created by the
     * WebRTC library and NOT the same thread where signaling messages are received.
     */
    public interface PeerConnectionObserver {
        void onStreamAdded(MediaStream mediaStream);
        void onStreamRemoved(MediaStream mediaStream);
        void onIceConnectionStateChanged(PeerConnection.IceConnectionState iceConnectionState);
    }

    private static final String TAG = PeerConnectionWrapper.class.getCanonicalName();

    private final SignalingMessageReceiver signalingMessageReceiver;
    private final WebRtcMessageListener webRtcMessageListener = new WebRtcMessageListener();

    private final SignalingMessageSender signalingMessageSender;

    private final DataChannelMessageNotifier dataChannelMessageNotifier = new DataChannelMessageNotifier();

    private final PeerConnectionNotifier peerConnectionNotifier = new PeerConnectionNotifier();

    private List<IceCandidate> iceCandidates = new ArrayList<>();
    private PeerConnection peerConnection;
    private String sessionId;
    private final MediaConstraints mediaConstraints;
    private DataChannel dataChannel;
    private final MagicSdpObserver magicSdpObserver;

    private final boolean hasInitiated;

    private final MediaStream localStream;
    private final boolean isMCUPublisher;
    private final String videoStreamType;

    // It is assumed that there will be at most one remote stream at each time.
    private MediaStream stream;

    @Inject
    Context context;

    public PeerConnectionWrapper(PeerConnectionFactory peerConnectionFactory,
                                 List<PeerConnection.IceServer> iceServerList,
                                 MediaConstraints mediaConstraints,
                                 String sessionId, String localSession, @Nullable MediaStream localStream,
                                 boolean isMCUPublisher, boolean hasMCU, String videoStreamType,
                                 SignalingMessageReceiver signalingMessageReceiver,
                                 SignalingMessageSender signalingMessageSender) {

        Objects.requireNonNull(NextcloudTalkApplication.Companion.getSharedApplication()).getComponentApplication().inject(this);

        this.localStream = localStream;
        this.videoStreamType = videoStreamType;

        this.sessionId = sessionId;
        this.mediaConstraints = mediaConstraints;

        magicSdpObserver = new MagicSdpObserver();
        hasInitiated = sessionId.compareTo(localSession) < 0;
        this.isMCUPublisher = isMCUPublisher;

        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(iceServerList);
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        peerConnection = peerConnectionFactory.createPeerConnection(configuration, new MagicPeerConnectionObserver());

        this.signalingMessageReceiver = signalingMessageReceiver;
        this.signalingMessageReceiver.addListener(webRtcMessageListener, sessionId, videoStreamType);

        this.signalingMessageSender = signalingMessageSender;

        if (peerConnection != null) {
            if (this.localStream != null) {
                List<String> localStreamIds = Collections.singletonList(this.localStream.getId());
                for(AudioTrack track : this.localStream.audioTracks) {
                    peerConnection.addTrack(track, localStreamIds);
                }
                for(VideoTrack track : this.localStream.videoTracks) {
                    peerConnection.addTrack(track, localStreamIds);
                }
            }

            if (hasMCU || hasInitiated) {
                DataChannel.Init init = new DataChannel.Init();
                init.negotiated = false;
                dataChannel = peerConnection.createDataChannel("status", init);
                dataChannel.registerObserver(new MagicDataChannelObserver());
                if (isMCUPublisher) {
                    peerConnection.createOffer(magicSdpObserver, mediaConstraints);
                } else if (hasMCU && "video".equals(this.videoStreamType)) {
                    // If the connection type is "screen" the client sharing the screen will send an
                    // offer; offers should be requested only for videos.
                    // "to" property is not actually needed in the "requestoffer" signaling message, but it is used to
                    // set the recipient session ID in the assembled call message.
                    NCSignalingMessage ncSignalingMessage = createBaseSignalingMessage("requestoffer");
                    signalingMessageSender.send(ncSignalingMessage);
                } else if (!hasMCU && hasInitiated && "video".equals(this.videoStreamType)) {
                    // If the connection type is "screen" the client sharing the screen will send an
                    // offer; offers should be created only for videos.
                    peerConnection.createOffer(magicSdpObserver, mediaConstraints);
                }
            }
        }
    }

    public void raiseHand(Boolean raise) {

        // TODO: fix how to build&send the message
//        NCMessagePayload ncMessagePayload = new NCMessagePayload();
//        ncMessagePayload.setState(raise);
//        ncMessagePayload.setTimestamp(System.currentTimeMillis());
//
//
//        NCSignalingMessage ncSignalingMessage = new NCSignalingMessage();
////        ncSignalingMessage.setFrom();
//        ncSignalingMessage.setTo(sessionId);
////        ncSignalingMessage.setSid();
//        ncSignalingMessage.setType("raiseHand");
//        ncSignalingMessage.setPayload(ncMessagePayload);
//        ncSignalingMessage.setRoomType(videoStreamType);
//
//        signalingMessageSender.send(ncSignalingMessage);
    }

    /**
     * Adds a listener for data channel messages.
     *
     * A listener is expected to be added only once. If the same listener is added again it will be notified just once.
     *
     * @param listener the DataChannelMessageListener
     */
    public void addListener(DataChannelMessageListener listener) {
        dataChannelMessageNotifier.addListener(listener);
    }

    public void removeListener(DataChannelMessageListener listener) {
        dataChannelMessageNotifier.removeListener(listener);
    }

    /**
     * Adds an observer for peer connection changes.
     *
     * An observer is expected to be added only once. If the same observer is added again it will be notified just once.
     *
     * @param observer the PeerConnectionObserver
     */
    public void addObserver(PeerConnectionObserver observer) {
        peerConnectionNotifier.addObserver(observer);
    }

    public void removeObserver(PeerConnectionObserver observer) {
        peerConnectionNotifier.removeObserver(observer);
    }

    public String getVideoStreamType() {
        return videoStreamType;
    }

    public MediaStream getStream() {
        return stream;
    }

    public void removePeerConnection() {
        signalingMessageReceiver.removeListener(webRtcMessageListener);

        if (dataChannel != null) {
            dataChannel.dispose();
            dataChannel = null;
            Log.d(TAG, "Disposed DataChannel");
        } else {
            Log.d(TAG, "DataChannel is null.");
        }

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
            Log.d(TAG, "Disposed PeerConnection");
        } else {
            Log.d(TAG, "PeerConnection is null.");
        }
    }

    private void drainIceCandidates() {

        if (peerConnection != null) {
            for (IceCandidate iceCandidate : iceCandidates) {
                peerConnection.addIceCandidate(iceCandidate);
            }

            iceCandidates = new ArrayList<>();
        }
    }

    private void addCandidate(IceCandidate iceCandidate) {
        if (peerConnection != null && peerConnection.getRemoteDescription() != null) {
            peerConnection.addIceCandidate(iceCandidate);
        } else {
            iceCandidates.add(iceCandidate);
        }
    }

    public void sendChannelData(DataChannelMessage dataChannelMessage) {
        ByteBuffer buffer;
        if (dataChannel != null && dataChannelMessage != null) {
            try {
                buffer = ByteBuffer.wrap(LoganSquare.serialize(dataChannelMessage).getBytes());
                dataChannel.send(new DataChannel.Buffer(buffer, false));
            } catch (Exception e) {
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

    private void sendInitialMediaStatus() {
        if (localStream != null) {
            if (localStream.videoTracks.size() == 1 && localStream.videoTracks.get(0).enabled()) {
                sendChannelData(new DataChannelMessage("videoOn"));
            } else {
                sendChannelData(new DataChannelMessage("videoOff"));
            }

            if (localStream.audioTracks.size() == 1 && localStream.audioTracks.get(0).enabled()) {
                sendChannelData(new DataChannelMessage("audioOn"));
            } else {
                sendChannelData(new DataChannelMessage("audioOff"));
            }
        }
    }

    public boolean isMCUPublisher() {
        return isMCUPublisher;
    }

    private boolean shouldNotReceiveVideo() {
        for (MediaConstraints.KeyValuePair keyValuePair : mediaConstraints.mandatory) {
            if ("OfferToReceiveVideo".equals(keyValuePair.getKey())) {
                return !Boolean.parseBoolean(keyValuePair.getValue());
            }
        }
        return false;
    }

    private NCSignalingMessage createBaseSignalingMessage(String type) {
        NCSignalingMessage ncSignalingMessage = new NCSignalingMessage();
        ncSignalingMessage.setTo(sessionId);
        ncSignalingMessage.setRoomType(videoStreamType);
        ncSignalingMessage.setType(type);

        return ncSignalingMessage;
    }

    private class WebRtcMessageListener implements SignalingMessageReceiver.WebRtcMessageListener {

        public void onOffer(String sdp, String nick) {
            onOfferOrAnswer("offer", sdp);
        }

        public void onAnswer(String sdp, String nick) {
            onOfferOrAnswer("answer", sdp);
        }

        private void onOfferOrAnswer(String type, String sdp) {
            SessionDescription sessionDescriptionWithPreferredCodec;

            boolean isAudio = false;
            String sessionDescriptionStringWithPreferredCodec = MagicWebRTCUtils.preferCodec(sdp, "H264", isAudio);

            sessionDescriptionWithPreferredCodec = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(type),
                sessionDescriptionStringWithPreferredCodec);

            if (getPeerConnection() != null) {
                getPeerConnection().setRemoteDescription(magicSdpObserver, sessionDescriptionWithPreferredCodec);
            }
        }

        public void onCandidate(String sdpMid, int sdpMLineIndex, String sdp) {
            IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
            addCandidate(iceCandidate);
        }

        public void onEndOfCandidates() {
            drainIceCandidates();
        }
    }

    private class MagicDataChannelObserver implements DataChannel.Observer {

        @Override
        public void onBufferedAmountChange(long l) {

        }

        @Override
        public void onStateChange() {
            if (dataChannel != null &&
                dataChannel.state() == DataChannel.State.OPEN &&
                "status".equals(dataChannel.label())) {
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

            DataChannelMessage dataChannelMessage;
            try {
                dataChannelMessage = LoganSquare.parse(strData, DataChannelMessage.class);
            } catch (IOException e) {
                Log.d(TAG, "Failed to parse data channel message");

                return;
            }

            if ("nickChanged".equals(dataChannelMessage.getType())) {
                String nick = null;
                if (dataChannelMessage.getPayload() instanceof String) {
                    nick = (String) dataChannelMessage.getPayload();
                } else if (dataChannelMessage.getPayload() instanceof Map) {
                    Map<String, String> payloadMap = (Map<String, String>) dataChannelMessage.getPayload();
                    nick = payloadMap.get("name");
                }

                if (nick != null) {
                    dataChannelMessageNotifier.notifyNickChanged(nick);
                }

                return;
            }

            if ("audioOn".equals(dataChannelMessage.getType())) {
                dataChannelMessageNotifier.notifyAudioOn();

                return;
            }

            if ("audioOff".equals(dataChannelMessage.getType())) {
                dataChannelMessageNotifier.notifyAudioOff();

                return;
            }

            if ("videoOn".equals(dataChannelMessage.getType())) {
                dataChannelMessageNotifier.notifyVideoOn();

                return;
            }

            if ("videoOff".equals(dataChannelMessage.getType())) {
                dataChannelMessageNotifier.notifyVideoOff();

                return;
            }
        }
    }

    private class MagicPeerConnectionObserver implements PeerConnection.Observer {

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

            Log.d("iceConnectionChangeTo: ", iceConnectionState.name() + " over " + peerConnection.hashCode() + " " + sessionId);
            if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                if (hasInitiated) {
                    sendInitialMediaStatus();
                }
            }

            peerConnectionNotifier.notifyIceConnectionStateChanged(iceConnectionState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            NCSignalingMessage ncSignalingMessage = createBaseSignalingMessage("candidate");
            NCMessagePayload ncMessagePayload = new NCMessagePayload();
            ncMessagePayload.setType("candidate");

            NCIceCandidate ncIceCandidate = new NCIceCandidate();
            ncIceCandidate.setSdpMid(iceCandidate.sdpMid);
            ncIceCandidate.setSdpMLineIndex(iceCandidate.sdpMLineIndex);
            ncIceCandidate.setCandidate(iceCandidate.sdp);
            ncMessagePayload.setIceCandidate(ncIceCandidate);

            ncSignalingMessage.setPayload(ncMessagePayload);

            signalingMessageSender.send(ncSignalingMessage);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            stream = mediaStream;

            peerConnectionNotifier.notifyStreamAdded(mediaStream);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            stream = null;

            peerConnectionNotifier.notifyStreamRemoved(mediaStream);
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            if ("status".equals(dataChannel.label()) || "JanusDataChannel".equals(dataChannel.label())) {
                PeerConnectionWrapper.this.dataChannel = dataChannel;
                PeerConnectionWrapper.this.dataChannel.registerObserver(new MagicDataChannelObserver());
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
            String type = sessionDescription.type.canonicalForm();

            NCSignalingMessage ncSignalingMessage = createBaseSignalingMessage(type);
            NCMessagePayload ncMessagePayload = new NCMessagePayload();
            ncMessagePayload.setType(type);

            SessionDescription sessionDescriptionWithPreferredCodec;
            String sessionDescriptionStringWithPreferredCodec = MagicWebRTCUtils.preferCodec
                    (sessionDescription.description,
                            "H264", false);
            sessionDescriptionWithPreferredCodec = new SessionDescription(
                    sessionDescription.type,
                    sessionDescriptionStringWithPreferredCodec);

            ncMessagePayload.setSdp(sessionDescriptionWithPreferredCodec.description);

            ncSignalingMessage.setPayload(ncMessagePayload);

            signalingMessageSender.send(ncSignalingMessage);

            if (peerConnection != null) {
                peerConnection.setLocalDescription(magicSdpObserver, sessionDescriptionWithPreferredCodec);
            }
        }

        @Override
        public void onSetSuccess() {
            if (peerConnection != null) {
                if (peerConnection.getLocalDescription() == null) {

                    if (shouldNotReceiveVideo()) {
                        for (RtpTransceiver t : peerConnection.getTransceivers()) {
                            if (t.getMediaType() == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                                t.stop();
                            }
                        }
                        Log.d(TAG, "Stop all Transceivers for MEDIA_TYPE_VIDEO.");
                    }

                    /*
                        Passed 'MediaConstraints' will be ignored by WebRTC when using UNIFIED PLAN.
                        See for details: https://docs.google.com/document/d/1PPHWV6108znP1tk_rkCnyagH9FK205hHeE9k5mhUzOg/edit#heading=h.9dcmkavg608r
                     */
                    peerConnection.createAnswer(magicSdpObserver, new MediaConstraints());

                }

                if (peerConnection.getRemoteDescription() != null) {
                    drainIceCandidates();
                }
            }
        }
    }
}