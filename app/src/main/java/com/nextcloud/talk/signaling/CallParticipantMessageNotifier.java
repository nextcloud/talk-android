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
import java.util.Iterator;
import java.util.List;

/**
 * Helper class to register and notify CallParticipantMessageListeners.
 *
 * This class is only meant for internal use by SignalingMessageReceiver; listeners must register themselves against
 * a SignalingMessageReceiver rather than against a CallParticipantMessageNotifier.
 */
class CallParticipantMessageNotifier {

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

    private final List<CallParticipantMessageListenerFrom> callParticipantMessageListenersFrom = new ArrayList<>();

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

    public synchronized void notifyUnshareScreen(String sessionId) {
        for (SignalingMessageReceiver.CallParticipantMessageListener listener : getListenersFor(sessionId)) {
            listener.onUnshareScreen();
        }
    }
}
