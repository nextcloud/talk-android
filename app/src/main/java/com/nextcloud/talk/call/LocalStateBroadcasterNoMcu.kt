/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Helper class to send the local participant state to the other participants in the call when an MCU is not used.
 * <p>
 * Sending the state when it changes is handled by the base class; this subclass only handles sending the initial
 * state when a remote participant is added.
 * <p>
 * The state is sent when a connection with another participant is first established (which implicitly broadcasts the
 * initial state when the local participant joins the call, as a connection is established with all the remote
 * participants). Note that, as long as that participant stays in the call, the initial state is not sent again, even
 * after a temporary disconnection; data channels use a reliable transport by default, so even if the state changes
 * while the connection is temporarily interrupted the normal state update messages should be received by the other
 * participant once the connection is restored.
 * <p>
 * Nevertheless, in case of a failed connection and an ICE restart it is unclear whether the data channel messages
 * would be received or not (as the data channel transport may be the one that failed and needs to be restarted).
 * However, the state (except the speaking state) is also sent through signaling messages, which need to be
 * explicitly fetched from the internal signaling server, so even in case of a failed connection they will be
 * eventually received once the remote participant connects again.
 */
public class LocalStateBroadcasterNoMcu extends LocalStateBroadcaster {

    private final MessageSenderNoMcu messageSender;

    private final Map<String, IceConnectionStateObserver> iceConnectionStateObservers = new HashMap<>();

    private class IceConnectionStateObserver implements CallParticipantModel.Observer {

        private final CallParticipantModel callParticipantModel;

        private PeerConnection.IceConnectionState iceConnectionState;

        public IceConnectionStateObserver(CallParticipantModel callParticipantModel) {
            this.callParticipantModel = callParticipantModel;

            callParticipantModel.addObserver(this);
            iceConnectionStateObservers.put(callParticipantModel.getSessionId(), this);
        }

        @Override
        public void onChange() {
            if (Objects.equals(iceConnectionState, callParticipantModel.getIceConnectionState())) {
                return;
            }

            iceConnectionState = callParticipantModel.getIceConnectionState();

            if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED ||
                iceConnectionState == PeerConnection.IceConnectionState.COMPLETED) {
                remove();

                sendState(callParticipantModel.getSessionId());
            }
        }

        @Override
        public void onReaction(String reaction) {
        }

        public void remove() {
            callParticipantModel.removeObserver(this);
            iceConnectionStateObservers.remove(callParticipantModel.getSessionId());
        }
    }

    public LocalStateBroadcasterNoMcu(LocalCallParticipantModel localCallParticipantModel,
                                      MessageSenderNoMcu messageSender) {
        super(localCallParticipantModel, messageSender);

        this.messageSender = messageSender;
    }

    public void destroy() {
        super.destroy();

        // The observers remove themselves from the map, so a copy is needed to remove them while iterating.
        List<IceConnectionStateObserver> iceConnectionStateObserversCopy =
            new ArrayList<>(iceConnectionStateObservers.values());
        for (IceConnectionStateObserver iceConnectionStateObserver : iceConnectionStateObserversCopy) {
            iceConnectionStateObserver.remove();
        }
    }

    @Override
    public void handleCallParticipantAdded(CallParticipantModel callParticipantModel) {
        IceConnectionStateObserver iceConnectionStateObserver =
            iceConnectionStateObservers.get(callParticipantModel.getSessionId());
        if (iceConnectionStateObserver != null) {
            iceConnectionStateObserver.remove();
        }

        iceConnectionStateObserver = new IceConnectionStateObserver(callParticipantModel);
        iceConnectionStateObservers.put(callParticipantModel.getSessionId(), iceConnectionStateObserver);
    }

    @Override
    public void handleCallParticipantRemoved(CallParticipantModel callParticipantModel) {
        IceConnectionStateObserver iceConnectionStateObserver =
            iceConnectionStateObservers.get(callParticipantModel.getSessionId());
        if (iceConnectionStateObserver != null) {
            iceConnectionStateObserver.remove();
        }
    }

    private void sendState(String sessionId) {
        messageSender.send(getDataChannelMessageForAudioState(), sessionId);
        messageSender.send(getDataChannelMessageForSpeakingState(), sessionId);
        messageSender.send(getDataChannelMessageForVideoState(), sessionId);

        messageSender.send(getSignalingMessageForAudioState(), sessionId);
        messageSender.send(getSignalingMessageForVideoState(), sessionId);
    }
}
