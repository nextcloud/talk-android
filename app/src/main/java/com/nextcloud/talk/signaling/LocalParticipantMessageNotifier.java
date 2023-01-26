/*
 * Nextcloud Talk application
 *
 * @author Daniel Calviño Sánchez
 * Copyright (C) 2023 Daniel Calviño Sánchez <danxuliu@gmail.com>
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
 * Helper class to register and notify LocalParticipantMessageListeners.
 *
 * This class is only meant for internal use by SignalingMessageReceiver; listeners must register themselves against
 * a SignalingMessageReceiver rather than against a LocalParticipantMessageNotifier.
 */
class LocalParticipantMessageNotifier {

    private final Set<SignalingMessageReceiver.LocalParticipantMessageListener> localParticipantMessageListeners = new LinkedHashSet<>();

    public synchronized void addListener(SignalingMessageReceiver.LocalParticipantMessageListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("localParticipantMessageListener can not be null");
        }

        localParticipantMessageListeners.add(listener);
    }

    public synchronized void removeListener(SignalingMessageReceiver.LocalParticipantMessageListener listener) {
        localParticipantMessageListeners.remove(listener);
    }

    public synchronized void notifySwitchTo(String token) {
        for (SignalingMessageReceiver.LocalParticipantMessageListener listener : new ArrayList<>(localParticipantMessageListeners)) {
            listener.onSwitchTo(token);
        }
    }
}
