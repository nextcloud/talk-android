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

import android.os.Handler;

import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;

import java.util.Objects;

/**
 * Read-only data model for (remote) call participants.
 *
 * The received audio and video are available only if the participant is sending them and also has them enabled.
 * Before a connection is established it is not known whether audio and video are available or not, so null is returned
 * in that case (therefore it should not be autoboxed to a plain boolean without checking that).
 *
 * Audio and video in screen shares, on the other hand, are always seen as available.
 *
 * Clients of the model can observe it with CallParticipantModel.Observer to be notified when any value changes.
 * Getters called after receiving a notification are guaranteed to provide at least the value that triggered the
 * notification, but it may return even a more up to date one (so getting the value again on the following
 * notification may return the same value as before).
 */
public class CallParticipantModel {

    public interface Observer {
        void onChange();
    }

    protected class Data<T> {

        private T value;

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            if (Objects.equals(this.value, value)) {
                return;
            }

            this.value = value;

            callParticipantModelNotifier.notifyChange();
        }
    }

    private final CallParticipantModelNotifier callParticipantModelNotifier = new CallParticipantModelNotifier();

    protected final String sessionId;

    protected Data<String> userId;
    protected Data<String> nick;

    protected Data<PeerConnection.IceConnectionState> iceConnectionState;
    protected Data<MediaStream> mediaStream;
    protected Data<Boolean> audioAvailable;
    protected Data<Boolean> videoAvailable;

    protected Data<PeerConnection.IceConnectionState> screenIceConnectionState;
    protected Data<MediaStream> screenMediaStream;

    public CallParticipantModel(String sessionId) {
        this.sessionId = sessionId;

        this.userId = new Data<>();
        this.nick = new Data<>();

        this.iceConnectionState = new Data<>();
        this.mediaStream = new Data<>();
        this.audioAvailable = new Data<>();
        this.videoAvailable = new Data<>();

        this.screenIceConnectionState = new Data<>();
        this.screenMediaStream = new Data<>();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId.getValue();
    }

    public String getNick() {
        return nick.getValue();
    }

    public PeerConnection.IceConnectionState getIceConnectionState() {
        return iceConnectionState.getValue();
    }

    public MediaStream getMediaStream() {
        return mediaStream.getValue();
    }

    public Boolean isAudioAvailable() {
        return audioAvailable.getValue();
    }

    public Boolean isVideoAvailable() {
        return videoAvailable.getValue();
    }

    public PeerConnection.IceConnectionState getScreenIceConnectionState() {
        return screenIceConnectionState.getValue();
    }

    public MediaStream getScreenMediaStream() {
        return screenMediaStream.getValue();
    }

    /**
     * Adds an Observer to be notified when any value changes.
     *
     * @param observer the Observer
     * @see CallParticipantModel#addObserver(Observer, Handler)
     */
    public void addObserver(Observer observer) {
        addObserver(observer, null);
    }

    /**
     * Adds an observer to be notified when any value changes.
     *
     * The observer will be notified on the thread associated to the given handler. If no handler is given the
     * observer will be immediately notified on the same thread that changed the value; the observer will be
     * immediately notified too if the thread of the handler is the same thread that changed the value.
     *
     * An observer is expected to be added only once. If the same observer is added again it will be notified just
     * once on the thread of the last handler.
     *
     * @param observer the Observer
     * @param handler a Handler for the thread to be notified on
     */
    public void addObserver(Observer observer, Handler handler) {
        callParticipantModelNotifier.addObserver(observer, handler);
    }

    public void removeObserver(Observer observer) {
        callParticipantModelNotifier.removeObserver(observer);
    }
}
