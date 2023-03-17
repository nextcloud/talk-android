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

import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;

/**
 * Mutable data model for (remote) call participants.
 *
 * There is no synchronization when setting the values; if needed, it should be handled by the clients of the model.
 */
public class MutableCallParticipantModel extends CallParticipantModel {

    public MutableCallParticipantModel(String sessionId) {
        super(sessionId);
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
}
