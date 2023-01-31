/*
 * Nextcloud Talk application
 *
 * @author Daniel Calviño Sánchez
 * Copyright (C) 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.signaling;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helper class to register and notify OfferMessageListeners.
 *
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

    public synchronized void notifyOffer(String sessionId, String roomType, String sid, String sdp, String nick) {
        for (SignalingMessageReceiver.OfferMessageListener listener : new ArrayList<>(offerMessageListeners)) {
            listener.onOffer(sessionId, roomType, sid, sdp, nick);
        }
    }
}
