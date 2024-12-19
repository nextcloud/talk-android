/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import com.nextcloud.talk.models.json.signaling.DataChannelMessage;
import com.nextcloud.talk.signaling.SignalingMessageSender;
import com.nextcloud.talk.webrtc.PeerConnectionWrapper;

import java.util.List;
import java.util.Set;

/**
 * Helper class to send messages to participants in a call when an MCU is used.
 * <p>
 * Note that when Janus is used it is not possible to send a data channel message to a specific participant. Any data
 * channel message will be broadcast to all the subscribers of the publisher peer connection (the own peer connection).
 */
public class MessageSenderMcu extends MessageSender {

    private final String ownSessionId;

    public MessageSenderMcu(SignalingMessageSender signalingMessageSender,
                            Set<String> callParticipantSessionIds,
                            List<PeerConnectionWrapper> peerConnectionWrappers,
                            String ownSessionId) {
        super(signalingMessageSender, callParticipantSessionIds, peerConnectionWrappers);

        this.ownSessionId = ownSessionId;
    }

    public void sendToAll(DataChannelMessage dataChannelMessage) {
        PeerConnectionWrapper ownPeerConnectionWrapper = getPeerConnectionWrapper(ownSessionId);
        if (ownPeerConnectionWrapper != null) {
            ownPeerConnectionWrapper.send(dataChannelMessage);
        }
    }
}
