/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc;

import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
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
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.Nullable;

public class PeerConnectionWrapper {

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
    private final Map<String, DataChannel> dataChannels = new HashMap<>();
    private final SdpObserver sdpObserver;

    private final boolean hasInitiated;

    private final MediaStream localStream;
    private final boolean isMCUPublisher;
    private final String videoStreamType;

    // It is assumed that there will be at most one remote stream at each time.
    private MediaStream stream;

    /**
     * Listener for data channel messages.
     * <p>
     * Messages might have been received on any data channel, independently of its label or whether it was open by the
     * local or the remote peer.
     * <p>
     * The messages are bound to a specific peer connection, so each listener is expected to handle messages only for
     * a single peer connection.
     * <p>
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
     * <p>
     * The changes are bound to a specific peer connection, so each observer is expected to handle messages only for
     * a single peer connection.
     * <p>
     * All methods are called on the so called "signaling" thread of WebRTC, which is an internal thread created by the
     * WebRTC library and NOT the same thread where signaling messages are received.
     */
    public interface PeerConnectionObserver {
        void onStreamAdded(MediaStream mediaStream);
        void onStreamRemoved(MediaStream mediaStream);
        void onIceConnectionStateChanged(PeerConnection.IceConnectionState iceConnectionState);
    }

    public PeerConnectionWrapper(PeerConnectionFactory peerConnectionFactory,
                                 List<PeerConnection.IceServer> iceServerList,
                                 MediaConstraints mediaConstraints,
                                 String sessionId, String localSession, @Nullable MediaStream localStream,
                                 boolean isMCUPublisher, boolean hasMCU, String videoStreamType,
                                 SignalingMessageReceiver signalingMessageReceiver,
                                 SignalingMessageSender signalingMessageSender) {
        this.localStream = localStream;
        this.videoStreamType = videoStreamType;

        this.sessionId = sessionId;
        this.mediaConstraints = mediaConstraints;

        sdpObserver = new SdpObserver();
        hasInitiated = sessionId.compareTo(localSession) < 0;
        this.isMCUPublisher = isMCUPublisher;

        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(iceServerList);
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        peerConnection = peerConnectionFactory.createPeerConnection(configuration, new InitialPeerConnectionObserver());

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

                DataChannel statusDataChannel = peerConnection.createDataChannel("status", init);
                statusDataChannel.registerObserver(new DataChannelObserver(statusDataChannel));
                dataChannels.put("status", statusDataChannel);

                if (isMCUPublisher) {
                    peerConnection.createOffer(sdpObserver, mediaConstraints);
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
                    peerConnection.createOffer(sdpObserver, mediaConstraints);
                }
            }
        }
    }

    public void raiseHand(Boolean raise) {
        NCMessagePayload ncMessagePayload = new NCMessagePayload();
        ncMessagePayload.setState(raise);
        ncMessagePayload.setTimestamp(System.currentTimeMillis());

        NCSignalingMessage ncSignalingMessage = new NCSignalingMessage();
        ncSignalingMessage.setTo(sessionId);
        ncSignalingMessage.setType("raiseHand");
        ncSignalingMessage.setPayload(ncMessagePayload);
        ncSignalingMessage.setRoomType(videoStreamType);

        signalingMessageSender.send(ncSignalingMessage);
    }

    public void sendReaction(String emoji) {
        NCMessagePayload ncMessagePayload = new NCMessagePayload();
        ncMessagePayload.setReaction(emoji);
        ncMessagePayload.setTimestamp(System.currentTimeMillis());

        NCSignalingMessage ncSignalingMessage = new NCSignalingMessage();
        ncSignalingMessage.setTo(sessionId);
        ncSignalingMessage.setType("reaction");
        ncSignalingMessage.setPayload(ncMessagePayload);
        ncSignalingMessage.setRoomType(videoStreamType);

        signalingMessageSender.send(ncSignalingMessage);
    }

    /**
     * Adds a listener for data channel messages.
     * <p>
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
     * <p>
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

        for (DataChannel dataChannel: dataChannels.values()) {
            Log.d(TAG, "Disposed DataChannel " + dataChannel.label());

            dataChannel.dispose();
        }
        dataChannels.clear();

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

    /**
     * Sends a data channel message.
     * <p>
     * Data channel messages are always sent on the "status" data channel locally opened. However, if Janus is used,
     * messages can be sent only on publisher connections, even if subscriber connections have a "status" data channel;
     * messages sent on subscriber connections will be simply ignored. Moreover, even if the message is sent on the
     * "status" data channel subscriber connections will receive it on a data channel with a different label, as
     * Janus opens its own data channel on subscriber connections and "multiplexes" all the received data channel
     * messages on it, independently of on which data channel they were originally sent.
     *
     * @param dataChannelMessage the message to send
     */
    public void send(DataChannelMessage dataChannelMessage) {
        DataChannel statusDataChannel = dataChannels.get("status");
        if (statusDataChannel == null || dataChannelMessage == null) {
            return;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(LoganSquare.serialize(dataChannelMessage).getBytes());
            statusDataChannel.send(new DataChannel.Buffer(buffer, false));
        } catch (Exception e) {
            Log.d(TAG, "Failed to send channel data, attempting regular " + dataChannelMessage);
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
                send(new DataChannelMessage("videoOn"));
            } else {
                send(new DataChannelMessage("videoOff"));
            }

            if (localStream.audioTracks.size() == 1 && localStream.audioTracks.get(0).enabled()) {
                send(new DataChannelMessage("audioOn"));
            } else {
                send(new DataChannelMessage("audioOff"));
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
            String sessionDescriptionStringWithPreferredCodec = WebRTCUtils.preferCodec(sdp, "H264", isAudio);

            sessionDescriptionWithPreferredCodec = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(type),
                sessionDescriptionStringWithPreferredCodec);

            if (getPeerConnection() != null) {
                getPeerConnection().setRemoteDescription(sdpObserver, sessionDescriptionWithPreferredCodec);
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

    private class DataChannelObserver implements DataChannel.Observer {

        private final DataChannel dataChannel;

        public DataChannelObserver(DataChannel dataChannel) {
            this.dataChannel = Objects.requireNonNull(dataChannel);
        }

        @Override
        public void onBufferedAmountChange(long l) {

        }

        @Override
        public void onStateChange() {
            if (dataChannel.state() == DataChannel.State.OPEN) {
                sendInitialMediaStatus();
            }
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            if (buffer.binary) {
                Log.d(TAG, "Received binary data channel message over " + dataChannel.label() + " " + sessionId);
                return;
            }

            ByteBuffer data = buffer.data;
            final byte[] bytes = new byte[data.capacity()];
            data.get(bytes);
            String strData = new String(bytes);
            Log.d(TAG, "Received data channel message (" + strData + ") over " + dataChannel.label() + " " + sessionId);

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

    private class InitialPeerConnectionObserver implements PeerConnection.Observer {

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
            // Another data channel with the same label, no matter if the same instance or a different one, should not
            // be added, but just in case.
            DataChannel oldDataChannel = dataChannels.get(dataChannel.label());
            if (oldDataChannel == dataChannel) {
                Log.w(TAG, "Data channel with label " + dataChannel.label() + " added again");

                return;
            }

            if (oldDataChannel != null) {
                Log.w(TAG, "Data channel with label " + dataChannel.label() + " exists");

                oldDataChannel.dispose();
            }

            dataChannel.registerObserver(new DataChannelObserver(dataChannel));
            dataChannels.put(dataChannel.label(), dataChannel);
        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        }
    }

    private class SdpObserver implements org.webrtc.SdpObserver {
        private static final String TAG = "SdpObserver";

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
            String sessionDescriptionStringWithPreferredCodec = WebRTCUtils.preferCodec
                    (sessionDescription.description,
                            "H264", false);
            sessionDescriptionWithPreferredCodec = new SessionDescription(
                    sessionDescription.type,
                    sessionDescriptionStringWithPreferredCodec);

            ncMessagePayload.setSdp(sessionDescriptionWithPreferredCodec.description);

            ncSignalingMessage.setPayload(ncMessagePayload);

            signalingMessageSender.send(ncSignalingMessage);

            if (peerConnection != null) {
                peerConnection.setLocalDescription(sdpObserver, sessionDescriptionWithPreferredCodec);
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
                    peerConnection.createAnswer(sdpObserver, new MediaConstraints());

                }

                if (peerConnection.getRemoteDescription() != null) {
                    drainIceCandidates();
                }
            }
        }
    }
}
