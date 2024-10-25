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
 * Helper class to send messages to participants in a call when an MCU is not used.
 */
public class MessageSenderNoMcu extends MessageSender {

    public MessageSenderNoMcu(List<PeerConnectionWrapper> peerConnectionWrappers) {
        super(peerConnectionWrappers);
    }

    public void sendToAll(DataChannelMessage dataChannelMessage) {
        for (PeerConnectionWrapper peerConnectionWrapper: peerConnectionWrappers) {
            if ("video".equals(peerConnectionWrapper.getVideoStreamType())){
                peerConnectionWrapper.send(dataChannelMessage);
            }
        }
    }
}
