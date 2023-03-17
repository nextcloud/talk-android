/*
 * Nextcloud Talk application
 *
 * @author Daniel Calviño Sánchez
 * Copyright (C) 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
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
package com.nextcloud.talk.call;

import com.nextcloud.talk.signaling.SignalingMessageReceiver;
import com.nextcloud.talk.webrtc.PeerConnectionWrapper;

import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;

/**
 * Model for (remote) call participants.
 *
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
