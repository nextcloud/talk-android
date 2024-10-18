/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helper class to register and notify LocalParticipantMessageListeners.
 * <p>
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
