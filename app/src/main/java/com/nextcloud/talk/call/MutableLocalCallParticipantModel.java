/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import java.util.Objects;

/**
 * Mutable data model for local call participants.
 * <p>
 * Setting "speaking" will automatically set "speaking" or "speakingWhileMuted" as needed, depending on whether audio is
 * enabled or not. Similarly, setting whether the audio is enabled or disabled will automatically switch between
 * "speaking" and "speakingWhileMuted" as needed.
 * <p>
 * There is no synchronization when setting the values; if needed, it should be handled by the clients of the model.
 */
public class MutableLocalCallParticipantModel extends LocalCallParticipantModel {

    public void setAudioEnabled(Boolean audioEnabled) {
        if (Objects.equals(this.audioEnabled.getValue(), audioEnabled)) {
            return;
        }

        if (audioEnabled == null || !audioEnabled) {
            this.speakingWhileMuted.setValue(this.speaking.getValue());
            this.speaking.setValue(Boolean.FALSE);
        }

        this.audioEnabled.setValue(audioEnabled);

        if (audioEnabled != null && audioEnabled) {
            this.speaking.setValue(this.speakingWhileMuted.getValue());
            this.speakingWhileMuted.setValue(Boolean.FALSE);
        }
    }

    public void setSpeaking(Boolean speaking) {
        if (this.audioEnabled.getValue() != null && this.audioEnabled.getValue()) {
            this.speaking.setValue(speaking);
        } else {
            this.speakingWhileMuted.setValue(speaking);
        }
    }

    public void setVideoEnabled(Boolean videoEnabled) {
        this.videoEnabled.setValue(videoEnabled);
    }
}
