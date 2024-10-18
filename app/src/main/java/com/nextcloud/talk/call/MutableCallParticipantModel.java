/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import com.nextcloud.talk.models.json.participants.Participant;

import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;

/**
 * Mutable data model for (remote) call participants.
 * <p>
 * There is no synchronization when setting the values; if needed, it should be handled by the clients of the model.
 */
public class MutableCallParticipantModel extends CallParticipantModel {

    public MutableCallParticipantModel(String sessionId) {
        super(sessionId);
    }

    public void setActor(Participant.ActorType actorType, String actorId) {
        this.actorType.setValue(actorType);
        this.actorId.setValue(actorId);
    }

    public void setUserId(String userId) {
        this.userId.setValue(userId);
    }

    public void setNick(String nick) {
        this.nick.setValue(nick);
    }

    public void setInternal(Boolean internal) {
        this.internal.setValue(internal);
    }

    public void setRaisedHand(boolean state, long timestamp) {
        this.raisedHand.setValue(new RaisedHand(state, timestamp));
    }

    public void setIceConnectionState(PeerConnection.IceConnectionState iceConnectionState) {
        this.iceConnectionState.setValue(iceConnectionState);
    }

    public void setMediaStream(MediaStream mediaStream) {
        this.mediaStream.setValue(mediaStream);
    }

    public void setAudioAvailable(Boolean audioAvailable) {
        this.audioAvailable.setValue(audioAvailable);
    }

    public void setVideoAvailable(Boolean videoAvailable) {
        this.videoAvailable.setValue(videoAvailable);
    }

    public void setScreenIceConnectionState(PeerConnection.IceConnectionState screenIceConnectionState) {
        this.screenIceConnectionState.setValue(screenIceConnectionState);
    }

    public void setScreenMediaStream(MediaStream screenMediaStream) {
        this.screenMediaStream.setValue(screenMediaStream);
    }

    public void emitReaction(String reaction) {
        this.callParticipantModelNotifier.notifyReaction(reaction);
    }
}
