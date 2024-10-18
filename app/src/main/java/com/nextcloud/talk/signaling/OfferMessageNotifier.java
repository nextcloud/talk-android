/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helper class to register and notify OfferMessageListeners.
 * <p>
 * This class is only meant for internal use by SignalingMessageReceiver; listeners must register themselves against
 * a SignalingMessageReceiver rather than against an OfferMessageNotifier.
 */
class OfferMessageNotifier {

    private final Set<SignalingMessageReceiver.OfferMessageListener> offerMessageListeners = new LinkedHashSet<>();

    public synchronized void addListener(SignalingMessageReceiver.OfferMessageListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("OfferMessageListener can not be null");
        }

        offerMessageListeners.add(listener);
    }

    public synchronized void removeListener(SignalingMessageReceiver.OfferMessageListener listener) {
        offerMessageListeners.remove(listener);
    }

    public synchronized void notifyOffer(String sessionId, String roomType, String sdp, String nick) {
        for (SignalingMessageReceiver.OfferMessageListener listener : new ArrayList<>(offerMessageListeners)) {
            listener.onOffer(sessionId, roomType, sdp, nick);
        }
    }
}
