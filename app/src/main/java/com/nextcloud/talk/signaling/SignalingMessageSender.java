/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling;

import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;

/**
 * Interface to send signaling messages.
 */
public interface SignalingMessageSender {

    /**
     * Sends the given signaling message.
     *
     * @param ncSignalingMessage the message to send
     */
    void send(NCSignalingMessage ncSignalingMessage);

}
