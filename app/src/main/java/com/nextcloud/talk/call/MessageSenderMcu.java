/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import com.nextcloud.talk.models.json.signaling.DataChannelMessage;
import com.nextcloud.talk.webrtc.PeerConnectionWrapper;

import java.util.List;

/**
 * Helper class to send messages to participants in a call when an MCU is used.
 */
public class MessageSenderMcu extends MessageSender {

    private final String ownSessionId;

    public MessageSenderMcu(List<PeerConnectionWrapper> peerConnectionWrappers,
                            String ownSessionId) {
        super(peerConnectionWrappers);

        this.ownSessionId = ownSessionId;
    }

    public void sendToAll(DataChannelMessage dataChannelMessage) {
        PeerConnectionWrapper ownPeerConnectionWrapper = getPeerConnectionWrapper(ownSessionId);
        if (ownPeerConnectionWrapper != null) {
            ownPeerConnectionWrapper.send(dataChannelMessage);
        }
    }
}
