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
 * Helper class to register and notify WebRtcMessageListeners.
 *
 * This class is only meant for internal use by SignalingMessageReceiver; listeners must register themselves against
 * a SignalingMessageReceiver rather than against a WebRtcMessageNotifier.
 */
class WebRtcMessageNotifier {

    /**
     * Helper class to associate a WebRtcMessageListener with a session ID and room type.
     */
    private static class WebRtcMessageListenerFrom {
        public final SignalingMessageReceiver.WebRtcMessageListener listener;
        public final String sessionId;
        public final String roomType;

        private WebRtcMessageListenerFrom(SignalingMessageReceiver.WebRtcMessageListener listener,
                                          String sessionId,
                                          String roomType) {
            this.listener = listener;
            this.sessionId = sessionId;
            this.roomType = roomType;
        }
    }

    private final List<WebRtcMessageListenerFrom> webRtcMessageListenersFrom = new ArrayList<>();

    public synchronized void addListener(SignalingMessageReceiver.WebRtcMessageListener listener, String sessionId, String roomType) {
        if (listener == null) {
            throw new IllegalArgumentException("WebRtcMessageListener can not be null");
        }

        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId can not be null");
        }

        if (roomType == null) {
            throw new IllegalArgumentException("roomType can not be null");
        }

        removeListener(listener);

        webRtcMessageListenersFrom.add(new WebRtcMessageListenerFrom(listener, sessionId, roomType));
    }

    public synchronized void removeListener(SignalingMessageReceiver.WebRtcMessageListener listener) {
        Iterator<WebRtcMessageListenerFrom> it = webRtcMessageListenersFrom.iterator();
        while (it.hasNext()) {
            WebRtcMessageListenerFrom listenerFrom = it.next();

            if (listenerFrom.listener == listener) {
                it.remove();

                return;
            }
        }
    }

    private List<SignalingMessageReceiver.WebRtcMessageListener> getListenersFor(String sessionId, String roomType) {
        List<SignalingMessageReceiver.WebRtcMessageListener> webRtcMessageListeners =
            new ArrayList<>(webRtcMessageListenersFrom.size());

        for (WebRtcMessageListenerFrom listenerFrom : webRtcMessageListenersFrom) {
            if (listenerFrom.sessionId.equals(sessionId) && listenerFrom.roomType.equals(roomType)) {
                webRtcMessageListeners.add(listenerFrom.listener);
            }
        }

        return webRtcMessageListeners;
    }

    public synchronized void notifyOffer(String sessionId, String roomType, String sdp, String nick) {
        for (SignalingMessageReceiver.WebRtcMessageListener listener : getListenersFor(sessionId, roomType)) {
            listener.onOffer(sdp, nick);
        }
    }

    public synchronized void notifyAnswer(String sessionId, String roomType, String sdp, String nick) {
        for (SignalingMessageReceiver.WebRtcMessageListener listener : getListenersFor(sessionId, roomType)) {
            listener.onAnswer(sdp, nick);
        }
    }

    public synchronized void notifyCandidate(String sessionId, String roomType, String sdpMid, int sdpMLineIndex, String sdp) {
        for (SignalingMessageReceiver.WebRtcMessageListener listener : getListenersFor(sessionId, roomType)) {
            listener.onCandidate(sdpMid, sdpMLineIndex, sdp);
        }
    }

    public synchronized void notifyEndOfCandidates(String sessionId, String roomType) {
        for (SignalingMessageReceiver.WebRtcMessageListener listener : getListenersFor(sessionId, roomType)) {
            listener.onEndOfCandidates();
        }
    }
}
