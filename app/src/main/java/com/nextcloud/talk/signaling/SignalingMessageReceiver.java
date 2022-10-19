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
 * In general, if a listener is added while an event is being handled the new listener will not receive that event.
 * An exception to that is adding a WebRtcMessageListener when handling an offer in an OfferMessageListener; in that
 * case the "onOffer()" method of the WebRtcMessageListener will be called for that same offer.
 *
 * Similarly, if a listener is removed while an event is being handled the removed listener will still receive that
 * event. Again the exception is removing a WebRtcMessageListener when handling an offer in an OfferMessageListener; in
 * that case the "onOffer()" method of the WebRtcMessageListener will not be called for that offer.
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
     * Listener for call participant messages.
     *
     * The messages are bound to a specific call participant (or, rather, session), so each listener is expected to
     * handle messages only for a single call participant.
     *
     * Although "unshareScreen" is technically bound to a specific peer connection it is instead treated as a general
     * message on the call participant.
     */
    public interface CallParticipantMessageListener {
        void onUnshareScreen();
    }

    /**
     * Listener for WebRTC offers.
     *
     * Unlike the WebRtcMessageListener, which is bound to a specific peer connection, an OfferMessageListener listens
     * to all offer messages, no matter which peer connection they are bound to. This can be used, for example, to
     * create a new peer connection when a remote offer for which there is no previous connection is received.
     *
     * When an offer is received all OfferMessageListeners are notified before any WebRtcMessageListener is notified.
     */
    public interface OfferMessageListener {
        void onOffer(String sessionId, String roomType, String sdp, String nick);
    }

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

    private final CallParticipantMessageNotifier callParticipantMessageNotifier = new CallParticipantMessageNotifier();

    private final OfferMessageNotifier offerMessageNotifier = new OfferMessageNotifier();

    private final WebRtcMessageNotifier webRtcMessageNotifier = new WebRtcMessageNotifier();

    /**
     * Adds a listener for call participant messages.
     *
     * A listener is expected to be added only once. If the same listener is added again it will no longer be notified
     * for the messages from the previous session ID.
     *
     * @param listener the CallParticipantMessageListener
     * @param sessionId the ID of the session that messages come from
     */
    public void addListener(CallParticipantMessageListener listener, String sessionId) {
        callParticipantMessageNotifier.addListener(listener, sessionId);
    }

    public void removeListener(CallParticipantMessageListener listener) {
        callParticipantMessageNotifier.removeListener(listener);
    }

    /**
     * Adds a listener for all offer messages.
     *
     * A listener is expected to be added only once. If the same listener is added again it will be notified just once.
     *
     * @param listener the OfferMessageListener
     */
    public void addListener(OfferMessageListener listener) {
        offerMessageNotifier.addListener(listener);
    }

    public void removeListener(OfferMessageListener listener) {
        offerMessageNotifier.removeListener(listener);
    }

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

        // "unshareScreen" messages are directly sent to the screen peer connection when the internal signaling
        // server is used, and to the room when the external signaling server is used. However, the (relevant) data
        // of the received message ("from" and "type") is the same in both cases.
        if ("unshareScreen".equals(type)) {
            // Message schema (external signaling server):
            // {
            //     "type": "message",
            //     "message": {
            //         "sender": {
            //             ...
            //         },
            //         "data": {
            //             "roomType": "screen",
            //             "type": "unshareScreen",
            //             "from": #STRING#,
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
            //         "broadcaster": #STRING#,
            //         "roomType": "screen",
            //         "type": "unshareScreen",
            //         "from": #STRING#,
            //     },
            // }

            callParticipantMessageNotifier.notifyUnshareScreen(sessionId);

            return;
        }

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

            // If "processSignalingMessage" is called with two offers from two different threads it is possible,
            // although extremely unlikely, that the WebRtcMessageListeners for the second offer are notified before the
            // WebRtcMessageListeners for the first offer. This should not be a problem, though, so for simplicity
            // the statements are not synchronized.
            offerMessageNotifier.notifyOffer(sessionId, roomType, sdp, nick);
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
