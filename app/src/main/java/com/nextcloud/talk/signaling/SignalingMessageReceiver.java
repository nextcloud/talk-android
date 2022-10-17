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

import com.nextcloud.talk.models.json.signaling.NCIceCandidate;
import com.nextcloud.talk.models.json.signaling.NCMessagePayload;
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;

/**
 * Hub to register listeners for signaling messages of different kinds.
 *
 * Adding and removing listeners, as well as notifying them is internally synchronized. This should be kept in mind
 * if listeners are added or removed when handling an event to prevent deadlocks (nevertheless, just adding or
 * removing a listener in the same thread handling the event is fine, and in most cases it will be fine too if done
 * in a different thread, as long as the notifier thread is not forced to wait until the listener is added or removed).
 *
 * SignalingMessageReceiver does not fetch the signaling messages itself; subclasses must fetch them and then call
 * the appropriate protected methods to process the messages and notify the listeners.
 */
public abstract class SignalingMessageReceiver {

    /**
     * Listener for WebRTC messages.
     *
     * The messages are bound to a specific peer connection, so each listener is expected to handle messages only for
     * a single peer connection.
     */
    public interface WebRtcMessageListener {
        void onOffer(String sdp, String nick);
        void onAnswer(String sdp, String nick);
        void onCandidate(String sdpMid, int sdpMLineIndex, String sdp);
        void onEndOfCandidates();
    }

    private final WebRtcMessageNotifier webRtcMessageNotifier = new WebRtcMessageNotifier();

    /**
     * Adds a listener for WebRTC messages from the given session ID and room type.
     *
     * A listener is expected to be added only once. If the same listener is added again it will no longer be notified
     * for the messages from the previous session ID or room type.
     *
     * @param listener the WebRtcMessageListener
     * @param sessionId the ID of the session that messages come from
     * @param roomType the room type that messages come from
     */
    public void addListener(WebRtcMessageListener listener, String sessionId, String roomType) {
        webRtcMessageNotifier.addListener(listener, sessionId, roomType);
    }

    public void removeListener(WebRtcMessageListener listener) {
        webRtcMessageNotifier.removeListener(listener);
    }

    protected void processSignalingMessage(NCSignalingMessage signalingMessage) {
        // Note that in the internal signaling server message "data" is the String representation of a JSON
        // object, although it is already decoded when used here.

        String type = signalingMessage.getType();

        String sessionId = signalingMessage.getFrom();
        String roomType = signalingMessage.getRoomType();

        if ("offer".equals(type)) {
            // Message schema (external signaling server):
            // {
            //     "type": "message",
            //     "message": {
            //         "sender": {
            //             ...
            //         },
            //         "data": {
            //             "to": #STRING#,
            //             "from":  #STRING#,
            //             "type": "offer",
            //             "roomType": #STRING#, // "video" or "screen"
            //             "payload": {
            //                 "type": "offer",
            //                 "sdp": #STRING#,
            //             },
            //             "sid": #STRING#, // external signaling server >= 0.5.0
            //         },
            //     },
            // }
            //
            // Message schema (internal signaling server):
            // {
            //     "type": "message",
            //     "data": {
            //         "to": #STRING#,
            //         "sid": #STRING#,
            //         "roomType": #STRING#, // "video" or "screen"
            //         "type": "offer",
            //         "payload": {
            //             "type": "offer",
            //             "sdp": #STRING#,
            //             "nick": #STRING#, // Optional
            //         },
            //         "from": #STRING#,
            //     },
            // }

            NCMessagePayload payload = signalingMessage.getPayload();
            if (payload == null) {
                // Broken message, this should not happen.
                return;
            }

            String sdp = payload.getSdp();
            String nick = payload.getNick();

            webRtcMessageNotifier.notifyOffer(sessionId, roomType, sdp, nick);

            return;
        }

        if ("answer".equals(type)) {
            // Message schema: same as offers, but with type "answer".

            NCMessagePayload payload = signalingMessage.getPayload();
            if (payload == null) {
                // Broken message, this should not happen.
                return;
            }

            String sdp = payload.getSdp();
            String nick = payload.getNick();

            webRtcMessageNotifier.notifyAnswer(sessionId, roomType, sdp, nick);

            return;
        }

        if ("candidate".equals(type)) {
            // Message schema (external signaling server):
            // {
            //     "type": "message",
            //     "message": {
            //         "sender": {
            //             ...
            //         },
            //         "data": {
            //             "to": #STRING#,
            //             "from":  #STRING#,
            //             "type": "candidate",
            //             "roomType": #STRING#, // "video" or "screen"
            //             "payload": {
            //                 "candidate": {
            //                     "candidate": #STRING#,
            //                     "sdpMid": #STRING#,
            //                     "sdpMLineIndex": #INTEGER#,
            //                 },
            //             },
            //             "sid": #STRING#, // external signaling server >= 0.5.0
            //         },
            //     },
            // }
            //
            // Message schema (internal signaling server):
            // {
            //     "type": "message",
            //     "data": {
            //         "to": #STRING#,
            //         "sid": #STRING#,
            //         "roomType": #STRING#, // "video" or "screen"
            //         "type": "candidate",
            //         "payload": {
            //             "candidate": {
            //                 "candidate": #STRING#,
            //                 "sdpMid": #STRING#,
            //                 "sdpMLineIndex": #INTEGER#,
            //             },
            //         },
            //         "from": #STRING#,
            //     },
            // }

            NCMessagePayload payload = signalingMessage.getPayload();
            if (payload == null) {
                // Broken message, this should not happen.
                return;
            }

            NCIceCandidate ncIceCandidate = payload.getIceCandidate();
            if (ncIceCandidate == null) {
                // Broken message, this should not happen.
                return;
            }

            webRtcMessageNotifier.notifyCandidate(sessionId,
                                                  roomType,
                                                  ncIceCandidate.getSdpMid(),
                                                  ncIceCandidate.getSdpMLineIndex(),
                                                  ncIceCandidate.getCandidate());

            return;
        }

        if ("endOfCandidates".equals(type)) {
            webRtcMessageNotifier.notifyEndOfCandidates(sessionId, roomType);

            return;
        }
    }
}
