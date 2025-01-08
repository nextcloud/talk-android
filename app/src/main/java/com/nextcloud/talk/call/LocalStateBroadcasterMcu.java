/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import java.util.HashMap;
import java.util.Map;
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
 * <p>
 * Similarly, in the case of signaling messages it is not possible either to know when the remote participants have
 * "seen" the local participant and thus are ready to handle signaling messages about the state. However, in the case
 * of signaling messages it is possible to send them to a specific participant, so the initial state is sent several
 * times with an increasing delay directly to the participant that was added. Moreover, if the participant is removed
 * the state is no longer directly sent.
 * <p>
 * In any case, note that the state is sent only when the remote participant joins the call. Even in case of
 * temporary disconnections the normal state updates sent when the state changes are expected to be received by the
 * other participant, as signaling messages are sent through a WebSocket and are therefore reliable. Moreover, even
 * if the WebSocket is restarted and the connection resumed (rather than joining with a new session ID) the messages
 * would be also received, as in that case they would be queued until the WebSocket is connected again.
 * <p>
 * Data channel messages, on the other hand, could be lost if the remote participant restarts the peer receiver
 * connection (although they would be received in case of temporary disconnections, as data channels use a reliable
 * transport by default). Therefore, as the speaking state is sent only through data channels, updates of the speaking
 * state could be not received by remote participants.
 */
public class LocalStateBroadcasterMcu extends LocalStateBroadcaster {

    private final MessageSender messageSender;

    private final Map<String, Disposable> sendStateWithRepetitionByParticipant = new HashMap<>();

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

        for (Disposable sendStateWithRepetitionForParticipant: sendStateWithRepetitionByParticipant.values()) {
            sendStateWithRepetitionForParticipant.dispose();
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

        String sessionId = callParticipantModel.getSessionId();
        Disposable sendStateWithRepetitionForParticipant = sendStateWithRepetitionByParticipant.get(sessionId);
        if (sendStateWithRepetitionForParticipant != null) {
            sendStateWithRepetitionForParticipant.dispose();
        }

        sendStateWithRepetitionByParticipant.put(sessionId, Observable
            .fromArray(new Integer[]{0, 1, 2, 4, 8, 16})
            .concatMap(i -> Observable.just(i).delay(i, TimeUnit.SECONDS, Schedulers.io()))
            .subscribe(value -> sendState(sessionId)));
    }

    @Override
    public void handleCallParticipantRemoved(CallParticipantModel callParticipantModel) {
        String sessionId = callParticipantModel.getSessionId();
        Disposable sendStateWithRepetitionForParticipant = sendStateWithRepetitionByParticipant.get(sessionId);
        if (sendStateWithRepetitionForParticipant != null) {
            sendStateWithRepetitionForParticipant.dispose();
        }
    }

    private void sendState() {
        messageSender.sendToAll(getDataChannelMessageForAudioState());
        messageSender.sendToAll(getDataChannelMessageForSpeakingState());
        messageSender.sendToAll(getDataChannelMessageForVideoState());
    }

    private void sendState(String sessionId) {
        messageSender.send(getSignalingMessageForAudioState(), sessionId);
        messageSender.send(getSignalingMessageForVideoState(), sessionId);
    }
}
