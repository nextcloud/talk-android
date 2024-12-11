/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import com.nextcloud.talk.models.json.signaling.DataChannelMessage;

import java.util.Objects;

/**
 * Helper class to send the local participant state to the other participants in the call.
 * <p>
 * Once created, and until destroyed, the LocalStateBroadcaster will send the changes in the local participant state to
 * all the participants in the call. Note that the LocalStateBroadcaster does not check whether the local participant
 * is actually in the call or not; it is expected that the LocalStateBroadcaster will be created and destroyed when the
 * local participant joins and leaves the call.
 * <p>
 * The LocalStateBroadcaster also sends the current state to remote participants when they join (which implicitly
 * sends it to all remote participants when the local participant joins the call) so they can set an initial state
 * for the local participant.
 */
public abstract class LocalStateBroadcaster {

    private final LocalCallParticipantModel localCallParticipantModel;

    private final LocalCallParticipantModelObserver localCallParticipantModelObserver;

    private final MessageSender messageSender;

    private class LocalCallParticipantModelObserver implements LocalCallParticipantModel.Observer {

        private Boolean audioEnabled;
        private Boolean speaking;
        private Boolean videoEnabled;

        public LocalCallParticipantModelObserver(LocalCallParticipantModel localCallParticipantModel) {
            audioEnabled = localCallParticipantModel.isAudioEnabled();
            speaking = localCallParticipantModel.isSpeaking();
            videoEnabled = localCallParticipantModel.isVideoEnabled();
        }

        @Override
        public void onChange() {
            if (!Objects.equals(audioEnabled, localCallParticipantModel.isAudioEnabled())) {
                audioEnabled = localCallParticipantModel.isAudioEnabled();

                messageSender.sendToAll(getDataChannelMessageForAudioState());
            }

            if (!Objects.equals(speaking, localCallParticipantModel.isSpeaking())) {
                speaking = localCallParticipantModel.isSpeaking();

                messageSender.sendToAll(getDataChannelMessageForSpeakingState());
            }

            if (!Objects.equals(videoEnabled, localCallParticipantModel.isVideoEnabled())) {
                videoEnabled = localCallParticipantModel.isVideoEnabled();

                messageSender.sendToAll(getDataChannelMessageForVideoState());
            }
        }
    }

    public LocalStateBroadcaster(LocalCallParticipantModel localCallParticipantModel,
                                 MessageSender messageSender) {
        this.localCallParticipantModel = localCallParticipantModel;
        this.localCallParticipantModelObserver = new LocalCallParticipantModelObserver(localCallParticipantModel);
        this.messageSender = messageSender;

        this.localCallParticipantModel.addObserver(localCallParticipantModelObserver);
    }

    public void destroy() {
        this.localCallParticipantModel.removeObserver(localCallParticipantModelObserver);
    }

    public abstract void handleCallParticipantAdded(CallParticipantModel callParticipantModel);
    public abstract void handleCallParticipantRemoved(CallParticipantModel callParticipantModel);

    protected DataChannelMessage getDataChannelMessageForAudioState() {
        String type = "audioOff";
        if (localCallParticipantModel.isAudioEnabled() != null && localCallParticipantModel.isAudioEnabled()) {
            type = "audioOn";
        }

        return new DataChannelMessage(type);
    }

    protected DataChannelMessage getDataChannelMessageForSpeakingState() {
        String type = "stoppedSpeaking";
        if (localCallParticipantModel.isSpeaking() != null && localCallParticipantModel.isSpeaking()) {
            type = "speaking";
        }

        return new DataChannelMessage(type);
    }

    protected DataChannelMessage getDataChannelMessageForVideoState() {
        String type = "videoOff";
        if (localCallParticipantModel.isVideoEnabled() != null && localCallParticipantModel.isVideoEnabled()) {
            type = "videoOn";
        }

        return new DataChannelMessage(type);
    }
}
