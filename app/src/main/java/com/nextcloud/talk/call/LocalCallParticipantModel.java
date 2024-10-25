/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import android.os.Handler;

import java.util.Objects;

/**
 * Read-only data model for local call participants.
 * <p>
 * Clients of the model can observe it with LocalCallParticipantModel.Observer to be notified when any value changes.
 * Getters called after receiving a notification are guaranteed to provide at least the value that triggered the
 * notification, but it may return even a more up to date one (so getting the value again on the following notification
 * may return the same value as before).
 */
public class LocalCallParticipantModel {

    protected final LocalCallParticipantModelNotifier localCallParticipantModelNotifier =
        new LocalCallParticipantModelNotifier();

    protected Data<Boolean> audioEnabled;
    protected Data<Boolean> speaking;
    protected Data<Boolean> speakingWhileMuted;
    protected Data<Boolean> videoEnabled;

    public interface Observer {
        void onChange();
    }

    protected class Data<T> {

        private T value;

        public Data() {
        }

        public Data(T value) {
            this.value = value;
        }

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            if (Objects.equals(this.value, value)) {
                return;
            }

            this.value = value;

            localCallParticipantModelNotifier.notifyChange();
        }
    }

    public LocalCallParticipantModel() {
        this.audioEnabled = new Data<>(Boolean.FALSE);
        this.speaking = new Data<>(Boolean.FALSE);
        this.speakingWhileMuted = new Data<>(Boolean.FALSE);
        this.videoEnabled = new Data<>(Boolean.FALSE);
    }

    public Boolean isAudioEnabled() {
        return audioEnabled.getValue();
    }

    public Boolean isSpeaking() {
        return speaking.getValue();
    }

    public Boolean isSpeakingWhileMuted() {
        return speakingWhileMuted.getValue();
    }

    public Boolean isVideoEnabled() {
        return videoEnabled.getValue();
    }

    /**
     * Adds an Observer to be notified when any value changes.
     *
     * @param observer the Observer
     * @see LocalCallParticipantModel#addObserver(Observer, Handler)
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
        localCallParticipantModelNotifier.addObserver(observer, handler);
    }

    public void removeObserver(Observer observer) {
        localCallParticipantModelNotifier.removeObserver(observer);
    }
}
