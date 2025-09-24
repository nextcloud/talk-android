/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.signaling.SignalingMessageReceiver;
import com.nextcloud.talk.webrtc.PeerConnectionWrapper;

import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;

/**
 * Model for (remote) call participants.
 * <p>
 * This class keeps track of the state changes in a call participant and updates its data model as needed. View classes
 * are expected to directly use the read-only data model.
 */
public class CallParticipant {

    private final SignalingMessageReceiver.CallParticipantMessageListener callParticipantMessageListener =
            new SignalingMessageReceiver.CallParticipantMessageListener() {
        @Override
        public void onRaiseHand(boolean state, long timestamp) {
            callParticipantModel.setRaisedHand(state, timestamp);
        }

        @Override
        public void onReaction(String reaction) {
            callParticipantModel.emitReaction(reaction);
        }

        @Override
        public void onUnshareScreen() {
        }
    };

    private final PeerConnectionWrapper.PeerConnectionObserver peerConnectionObserver =
            new PeerConnectionWrapper.PeerConnectionObserver() {
        @Override
        public void onStreamAdded(MediaStream mediaStream) {
            handleStreamChange(mediaStream);
        }

        @Override
        public void onStreamRemoved(MediaStream mediaStream) {
            handleStreamChange(mediaStream);
        }

        @Override
        public void onIceConnectionStateChanged(PeerConnection.IceConnectionState iceConnectionState) {
            handleIceConnectionStateChange(iceConnectionState);
        }
    };

    private final PeerConnectionWrapper.PeerConnectionObserver screenPeerConnectionObserver =
            new PeerConnectionWrapper.PeerConnectionObserver() {
        @Override
        public void onStreamAdded(MediaStream mediaStream) {
            callParticipantModel.setScreenMediaStream(mediaStream);
        }

        @Override
        public void onStreamRemoved(MediaStream mediaStream) {
            callParticipantModel.setScreenMediaStream(null);
        }

        @Override
        public void onIceConnectionStateChanged(PeerConnection.IceConnectionState iceConnectionState) {
            callParticipantModel.setScreenIceConnectionState(iceConnectionState);
        }
    };

    // DataChannel messages are sent only in video peers; (sender) screen peers do not even open them.
    private final PeerConnectionWrapper.DataChannelMessageListener dataChannelMessageListener =
            new PeerConnectionWrapper.DataChannelMessageListener() {
        @Override
        public void onAudioOn() {
            callParticipantModel.setAudioAvailable(Boolean.TRUE);
        }

        @Override
        public void onAudioOff() {
            callParticipantModel.setAudioAvailable(Boolean.FALSE);
        }

        @Override
        public void onVideoOn() {
            callParticipantModel.setVideoAvailable(Boolean.TRUE);
        }

        @Override
        public void onVideoOff() {
            callParticipantModel.setVideoAvailable(Boolean.FALSE);
        }

        @Override
        public void onNickChanged(String nick) {
            callParticipantModel.setNick(nick);
        }
    };

    private final MutableCallParticipantModel callParticipantModel;

    private final SignalingMessageReceiver signalingMessageReceiver;

    private PeerConnectionWrapper peerConnectionWrapper;
    private PeerConnectionWrapper screenPeerConnectionWrapper;

    public CallParticipant(String sessionId, SignalingMessageReceiver signalingMessageReceiver) {
        callParticipantModel = new MutableCallParticipantModel(sessionId);

        this.signalingMessageReceiver = signalingMessageReceiver;
        signalingMessageReceiver.addListener(callParticipantMessageListener, sessionId);
    }

    public void destroy() {
        signalingMessageReceiver.removeListener(callParticipantMessageListener);

        if (peerConnectionWrapper != null) {
            peerConnectionWrapper.removeObserver(peerConnectionObserver);
            peerConnectionWrapper.removeListener(dataChannelMessageListener);
        }
        if (screenPeerConnectionWrapper != null) {
            screenPeerConnectionWrapper.removeObserver(screenPeerConnectionObserver);
        }
    }

    public CallParticipantModel getCallParticipantModel() {
        return callParticipantModel;
    }

    public void setActor(Participant.ActorType actorType, String actorId) {
        callParticipantModel.setActor(actorType, actorId);
    }

    public void setUserId(String userId) {
        callParticipantModel.setUserId(userId);
    }

    public void setNick(String nick) {
        callParticipantModel.setNick(nick);
    }

    public void setInternal(Boolean internal) {
        callParticipantModel.setInternal(internal);
    }

    public void setPeerConnectionWrapper(PeerConnectionWrapper peerConnectionWrapper) {
        if (this.peerConnectionWrapper != null) {
            this.peerConnectionWrapper.removeObserver(peerConnectionObserver);
            this.peerConnectionWrapper.removeListener(dataChannelMessageListener);
        }

        this.peerConnectionWrapper = peerConnectionWrapper;

        if (this.peerConnectionWrapper == null) {
            callParticipantModel.setIceConnectionState(null);
            callParticipantModel.setMediaStream(null);
            callParticipantModel.setAudioAvailable(null);
            callParticipantModel.setVideoAvailable(null);

            return;
        }

        handleIceConnectionStateChange(this.peerConnectionWrapper.getPeerConnection().iceConnectionState());
        handleStreamChange(this.peerConnectionWrapper.getStream());

        this.peerConnectionWrapper.addObserver(peerConnectionObserver);
        this.peerConnectionWrapper.addListener(dataChannelMessageListener);
    }

    private void handleIceConnectionStateChange(PeerConnection.IceConnectionState iceConnectionState) {
        callParticipantModel.setIceConnectionState(iceConnectionState);

        if (iceConnectionState == PeerConnection.IceConnectionState.NEW ||
                iceConnectionState == PeerConnection.IceConnectionState.CHECKING) {
            callParticipantModel.setAudioAvailable(null);
            callParticipantModel.setVideoAvailable(null);
        }
    }

    private void handleStreamChange(MediaStream mediaStream) {
        if (mediaStream == null) {
            callParticipantModel.setMediaStream(null);
            callParticipantModel.setVideoAvailable(Boolean.FALSE);

            return;
        }

        boolean hasAtLeastOneVideoStream = mediaStream.videoTracks != null && !mediaStream.videoTracks.isEmpty();

        callParticipantModel.setMediaStream(mediaStream);
        callParticipantModel.setVideoAvailable(hasAtLeastOneVideoStream);
    }

    public void setScreenPeerConnectionWrapper(PeerConnectionWrapper screenPeerConnectionWrapper) {
        if (this.screenPeerConnectionWrapper != null) {
            this.screenPeerConnectionWrapper.removeObserver(screenPeerConnectionObserver);
        }

        this.screenPeerConnectionWrapper = screenPeerConnectionWrapper;

        if (this.screenPeerConnectionWrapper == null) {
            callParticipantModel.setScreenIceConnectionState(null);
            callParticipantModel.setScreenMediaStream(null);

            return;
        }

        callParticipantModel.setScreenIceConnectionState(this.screenPeerConnectionWrapper.getPeerConnection().iceConnectionState());
        callParticipantModel.setScreenMediaStream(this.screenPeerConnectionWrapper.getStream());

        this.screenPeerConnectionWrapper.addObserver(screenPeerConnectionObserver);
    }
}
