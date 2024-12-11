/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Helper class to send the local participant state to the other participants in the call when an MCU is used.
 * <p>
 * Sending the state when it changes is handled by the base class; this subclass only handles sending the initial
 * state when a remote participant is added.
 * <p>
 * When Janus is used data channel messages are sent to all remote participants (with a peer connection to receive from
 * the local participant). Moreover, it is not possible to know when the remote participants open the data channel to
 * receive the messages, or even when they establish the receiver connection; it is only possible to know when the
 * data channel is open for the publisher connection of the local participant. Due to all that the state is sent
 * several times with an increasing delay whenever a participant joins the call (which implicitly broadcasts the
 * initial state when the local participant joins the call, as all the remote participants joined from the point of
 * view of the local participant). If the state was already being sent the sending is restarted with each new
 * participant that joins.
 */
public class LocalStateBroadcasterMcu extends LocalStateBroadcaster {

    private final MessageSender messageSender;

    private Disposable sendStateWithRepetition;

    public LocalStateBroadcasterMcu(LocalCallParticipantModel localCallParticipantModel,
                                    MessageSender messageSender) {
        super(localCallParticipantModel, messageSender);

        this.messageSender = messageSender;
    }

    public void destroy() {
        super.destroy();

        if (sendStateWithRepetition != null) {
            sendStateWithRepetition.dispose();
        }
    }

    @Override
    public void handleCallParticipantAdded(CallParticipantModel callParticipantModel) {
        if (sendStateWithRepetition != null) {
            sendStateWithRepetition.dispose();
        }

        sendStateWithRepetition = Observable
            .fromArray(new Integer[]{0, 1, 2, 4, 8, 16})
            .concatMap(i -> Observable.just(i).delay(i, TimeUnit.SECONDS, Schedulers.io()))
            .subscribe(value -> sendState());
    }

    @Override
    public void handleCallParticipantRemoved(CallParticipantModel callParticipantModel) {
    }

    private void sendState() {
        messageSender.sendToAll(getDataChannelMessageForAudioState());
        messageSender.sendToAll(getDataChannelMessageForSpeakingState());
        messageSender.sendToAll(getDataChannelMessageForVideoState());
    }
}
