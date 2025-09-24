/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import android.os.Handler;

import com.nextcloud.talk.models.json.participants.Participant;

import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;

import java.util.Objects;

/**
 * Read-only data model for (remote) call participants.
 * <p>
 * If the hand was never raised null is returned by "getRaisedHand()". Otherwise a RaisedHand object is returned with
 * the current state (raised or not) and the timestamp when the raised hand state last changed.
 * <p>
 * The received audio and video are available only if the participant is sending them and also has them enabled.
 * Before a connection is established it is not known whether audio and video are available or not, so null is returned
 * in that case (therefore it should not be autoboxed to a plain boolean without checking that).
 * <p>
 * Audio and video in screen shares, on the other hand, are always seen as available.
 * <p>
 * Actor type and actor id will be set only in Talk >= 20.
 * <p>
 * Clients of the model can observe it with CallParticipantModel.Observer to be notified when any value changes.
 * Getters called after receiving a notification are guaranteed to provide at least the value that triggered the
 * notification, but it may return even a more up to date one (so getting the value again on the following
 * notification may return the same value as before).
 * <p>
 * Besides onChange(), which notifies about changes in the model values, CallParticipantModel.Observer provides
 * additional methods to be notified about one-time events that are not reflected in the model values, like reactions.
 */
public class CallParticipantModel {

    protected final CallParticipantModelNotifier callParticipantModelNotifier = new CallParticipantModelNotifier();

    protected final String sessionId;

    protected Data<Participant.ActorType> actorType;
    protected Data<String> actorId;
    protected Data<String> userId;
    protected Data<String> nick;

    protected Data<Boolean> internal;

    protected Data<RaisedHand> raisedHand;

    protected Data<PeerConnection.IceConnectionState> iceConnectionState;
    protected Data<MediaStream> mediaStream;
    protected Data<Boolean> audioAvailable;
    protected Data<Boolean> videoAvailable;

    protected Data<PeerConnection.IceConnectionState> screenIceConnectionState;
    protected Data<MediaStream> screenMediaStream;

    public interface Observer {
        void onChange();
        void onReaction(String reaction);
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

    public CallParticipantModel(String sessionId) {
        this.sessionId = sessionId;

        this.actorType = new Data<>();
        this.actorId = new Data<>();
        this.userId = new Data<>();
        this.nick = new Data<>();

        this.internal = new Data<>();

        this.raisedHand = new Data<>();

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

    public Participant.ActorType getActorType() {
        return actorType.getValue();
    }

    public String getActorId() {
        return actorId.getValue();
    }

    public String getUserId() {
        return userId.getValue();
    }

    public String getNick() {
        return nick.getValue();
    }

    public Boolean isInternal() {
        return internal.getValue();
    }

    public RaisedHand getRaisedHand() {
        return raisedHand.getValue();
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
     * <p>
     * The observer will be notified on the thread associated to the given handler. If no handler is given the
     * observer will be immediately notified on the same thread that changed the value; the observer will be
     * immediately notified too if the thread of the handler is the same thread that changed the value.
     * <p>
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
