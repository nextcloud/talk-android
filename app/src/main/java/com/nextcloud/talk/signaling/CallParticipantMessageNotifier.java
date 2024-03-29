/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Helper class to register and notify CallParticipantMessageListeners.
 * <p>
 * This class is only meant for internal use by SignalingMessageReceiver; listeners must register themselves against
 * a SignalingMessageReceiver rather than against a CallParticipantMessageNotifier.
 */
class CallParticipantMessageNotifier {

    private final List<CallParticipantMessageListenerFrom> callParticipantMessageListenersFrom = new ArrayList<>();

    /**
     * Helper class to associate a CallParticipantMessageListener with a session ID.
     */
    private static class CallParticipantMessageListenerFrom {
        public final SignalingMessageReceiver.CallParticipantMessageListener listener;
        public final String sessionId;

        private CallParticipantMessageListenerFrom(SignalingMessageReceiver.CallParticipantMessageListener listener,
                                                   String sessionId) {
            this.listener = listener;
            this.sessionId = sessionId;
        }
    }

    public synchronized void addListener(SignalingMessageReceiver.CallParticipantMessageListener listener, String sessionId) {
        if (listener == null) {
            throw new IllegalArgumentException("CallParticipantMessageListener can not be null");
        }

        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId can not be null");
        }

        removeListener(listener);

        callParticipantMessageListenersFrom.add(new CallParticipantMessageListenerFrom(listener, sessionId));
    }

    public synchronized void removeListener(SignalingMessageReceiver.CallParticipantMessageListener listener) {
        Iterator<CallParticipantMessageListenerFrom> it = callParticipantMessageListenersFrom.iterator();
        while (it.hasNext()) {
            CallParticipantMessageListenerFrom listenerFrom = it.next();

            if (listenerFrom.listener == listener) {
                it.remove();

                return;
            }
        }
    }

    private List<SignalingMessageReceiver.CallParticipantMessageListener> getListenersFor(String sessionId) {
        List<SignalingMessageReceiver.CallParticipantMessageListener> callParticipantMessageListeners =
            new ArrayList<>(callParticipantMessageListenersFrom.size());

        for (CallParticipantMessageListenerFrom listenerFrom : callParticipantMessageListenersFrom) {
            if (listenerFrom.sessionId.equals(sessionId)) {
                callParticipantMessageListeners.add(listenerFrom.listener);
            }
        }

        return callParticipantMessageListeners;
    }

    public synchronized void notifyRaiseHand(String sessionId, boolean state, long timestamp) {
        for (SignalingMessageReceiver.CallParticipantMessageListener listener : getListenersFor(sessionId)) {
            listener.onRaiseHand(state, timestamp);
        }
    }

    public void notifyReaction(String sessionId, String reaction) {
        for (SignalingMessageReceiver.CallParticipantMessageListener listener : getListenersFor(sessionId)) {
            listener.onReaction(reaction);
        }
    }

    public synchronized void notifyUnshareScreen(String sessionId) {
        for (SignalingMessageReceiver.CallParticipantMessageListener listener : getListenersFor(sessionId)) {
            listener.onUnshareScreen();
        }
    }
}
