/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import com.nextcloud.talk.models.json.signaling.DataChannelMessage;
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;
import com.nextcloud.talk.signaling.SignalingMessageSender;
import com.nextcloud.talk.webrtc.PeerConnectionWrapper;

import java.util.List;
import java.util.Set;

/**
 * Helper class to send messages to participants in a call.
 * <p>
 * A specific subclass has to be created depending on whether an MCU is being used or not.
 * <p>
 * Note that recipients of signaling messages are not validated, so no error will be triggered if trying to send a
 * message to a participant with a session ID that does not exist or is not in the call.
 * <p>
 * Also note that, unlike signaling messages, data channel messages require a peer connection. Therefore data channel
 * messages may not be received by a participant if there is no peer connection with that participant (for example, if
 * neither the local and remote participants have publishing rights). Moreover, data channel messages are expected to
 * be received only on peer connections with type "video", so data channel messages will not be sent on other peer
 * connections.
 */
public abstract class MessageSender {

    private final SignalingMessageSender signalingMessageSender;

    private final Set<String> callParticipantSessionIds;

    protected final List<PeerConnectionWrapper> peerConnectionWrappers;

    public MessageSender(SignalingMessageSender signalingMessageSender,
                         Set<String> callParticipantSessionIds,
                         List<PeerConnectionWrapper> peerConnectionWrappers) {
        this.signalingMessageSender = signalingMessageSender;
        this.callParticipantSessionIds = callParticipantSessionIds;
        this.peerConnectionWrappers = peerConnectionWrappers;
    }

    /**
     * Sends the given data channel message to all the participants in the call.
     *
     * @param dataChannelMessage the message to send
     */
    public abstract void sendToAll(DataChannelMessage dataChannelMessage);

    /**
     * Sends the given signaling message to the given session ID.
     * <p>
     * Note that the signaling message will be modified to set the recipient in the "to" field.
     *
     * @param ncSignalingMessage the message to send
     * @param sessionId the signaling session ID of the participant to send the message to
     */
    public void send(NCSignalingMessage ncSignalingMessage, String sessionId) {
        ncSignalingMessage.setTo(sessionId);

        signalingMessageSender.send(ncSignalingMessage);
    }

    /**
     * Sends the given signaling message to all the participants in the call.
     * <p>
     * Note that the signaling message will be modified to set each of the recipients in the "to" field.
     *
     * @param ncSignalingMessage the message to send
     */
    public void sendToAll(NCSignalingMessage ncSignalingMessage) {
        for (String sessionId: callParticipantSessionIds) {
            ncSignalingMessage.setTo(sessionId);

            signalingMessageSender.send(ncSignalingMessage);
        }
    }

    protected PeerConnectionWrapper getPeerConnectionWrapper(String sessionId) {
        for (PeerConnectionWrapper peerConnectionWrapper: peerConnectionWrappers) {
            if (peerConnectionWrapper.getSessionId().equals(sessionId)
                    && "video".equals(peerConnectionWrapper.getVideoStreamType())) {
                return peerConnectionWrapper;
            }
        }

        return null;
    }
}
