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
 * Helper class to send messages to participants in a call.
 * <p>
 * A specific subclass has to be created depending on whether an MCU is being used or not.
 * <p>
 * Note that, unlike signaling messages, data channel messages require a peer connection. Therefore data channel
 * messages may not be received by a participant if there is no peer connection with that participant (for example, if
 * neither the local and remote participants have publishing rights).
 */
public abstract class MessageSender {

    protected final List<PeerConnectionWrapper> peerConnectionWrappers;

    public MessageSender(List<PeerConnectionWrapper> peerConnectionWrappers) {
        this.peerConnectionWrappers = peerConnectionWrappers;
    }

    /**
     * Sends the given data channel message to all the participants in the call.
     *
     * @param dataChannelMessage the message to send
     */
    public abstract void sendToAll(DataChannelMessage dataChannelMessage);

    protected PeerConnectionWrapper getPeerConnectionWrapper(String sessionId) {
        for (PeerConnectionWrapper peerConnectionWrapper: peerConnectionWrappers) {
            if (peerConnectionWrapper.getSessionId().equals(sessionId)) {
                return peerConnectionWrapper;
            }
        }

        return null;
    }
}
