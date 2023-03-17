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

import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.signaling.NCIceCandidate;
import com.nextcloud.talk.models.json.signaling.NCMessagePayload;
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     * Listener for participant list messages.
     *
     * The messages are implicitly bound to the room currently joined in the signaling server; listeners are expected
     * to know the current room.
     */
    public interface ParticipantListMessageListener {

        /**
         * List of all the participants in the room.
         *
         * This message is received only when the internal signaling server is used.
         *
         * The message is received periodically, and the participants may not have been modified since the last message.
         *
         * Only the following participant properties are set:
         * - inCall
         * - lastPing
         * - sessionId
         * - userId (if the participant is not a guest)
         *
         * "participantPermissions" is provided in the message (since Talk 13), but not currently set in the
         * participant. "publishingPermissions" was provided instead in Talk 12, but it was not used anywhere, so it is
         * ignored.
         *
         * @param participants all the participants (users and guests) in the room
         */
        void onUsersInRoom(List<Participant> participants);

        /**
         * List of all the participants in the call or the room (depending on what triggered the event).
         *
         * This message is received only when the external signaling server is used.
         *
         * The message is received when any participant changed, although what changed is not provided and should be
         * derived from the difference with previous messages. The list of participants may include only the
         * participants in the call (including those that just left it and thus triggered the event) or all the
         * participants currently in the room (participants in the room but not currently active, that is, without a
         * session, are not included).
         *
         * Only the following participant properties are set:
         * - inCall
         * - lastPing
         * - sessionId
         * - type
         * - userId (if the participant is not a guest)
         *
         * "nextcloudSessionId" is provided in the message (when the "inCall" property of any participant changed), but
         * not currently set in the participant.
         *
         * "participantPermissions" is provided in the message (since Talk 13), but not currently set in the
         * participant. "publishingPermissions" was provided instead in Talk 12, but it was not used anywhere, so it is
         * ignored.
         *
         * @param participants all the participants (users and guests) in the room
         */
        void onParticipantsUpdate(List<Participant> participants);

        /**
         * Update of the properties of all the participants in the room.
         *
         * This message is received only when the external signaling server is used.
         *
         * @param inCall the new value of the inCall property
         */
        void onAllParticipantsUpdate(long inCall);
    }

    /**
     * Listener for local participant messages.
     *
     * The messages are implicitly bound to the local participant (or, rather, its session); listeners are expected
     * to know the local participant.
     *
     * The messages are related to the conversation, so the local participant may or may not be in a call when they
     * are received.
     */
    public interface LocalParticipantMessageListener {
        /**
         * Request for the client to switch to the given conversation.
         *
         * This message is received only when the external signaling server is used.
         *
         * @param token the token of the conversation to switch to.
         */
        void onSwitchTo(String token);
    }

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
        void onRaiseHand(boolean state, long timestamp);
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

    private final ParticipantListMessageNotifier participantListMessageNotifier = new ParticipantListMessageNotifier();

    private final LocalParticipantMessageNotifier localParticipantMessageNotifier = new LocalParticipantMessageNotifier();

    private final CallParticipantMessageNotifier callParticipantMessageNotifier = new CallParticipantMessageNotifier();

    private final OfferMessageNotifier offerMessageNotifier = new OfferMessageNotifier();

    private final WebRtcMessageNotifier webRtcMessageNotifier = new WebRtcMessageNotifier();

    /**
     * Adds a listener for participant list messages.
     *
     * A listener is expected to be added only once. If the same listener is added again it will be notified just once.
     *
     * @param listener the ParticipantListMessageListener
     */
    public void addListener(ParticipantListMessageListener listener) {
        participantListMessageNotifier.addListener(listener);
    }

    public void removeListener(ParticipantListMessageListener listener) {
        participantListMessageNotifier.removeListener(listener);
    }

    /**
     * Adds a listener for local participant messages.
     *
     * A listener is expected to be added only once. If the same listener is added again it will be notified just once.
     *
     * @param listener the LocalParticipantMessageListener
     */
    public void addListener(LocalParticipantMessageListener listener) {
        localParticipantMessageNotifier.addListener(listener);
    }

    public void removeListener(LocalParticipantMessageListener listener) {
        localParticipantMessageNotifier.removeListener(listener);
    }

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

    protected void processEvent(Map<String, Object> eventMap) {
        if ("room".equals(eventMap.get("target")) && "switchto".equals(eventMap.get("type"))) {
            processSwitchToEvent(eventMap);

            return;
        }

        if ("participants".equals(eventMap.get("target")) && "update".equals(eventMap.get("type"))) {
            processUpdateEvent(eventMap);

            return;
        }
    }

    private void processSwitchToEvent(Map<String, Object> eventMap) {
        // Message schema:
        // {
        //     "type": "event",
        //     "event": {
        //         "target": "room",
        //         "type": "switchto",
        //         "switchto": {
        //             "roomid": #STRING#,
        //         },
        //     },
        // }

        Map<String, Object> switchToMap;
        try {
            switchToMap = (Map<String, Object>) eventMap.get("switchto");
        } catch (RuntimeException e) {
            // Broken message, this should not happen.
            return;
        }

        if (switchToMap == null) {
            // Broken message, this should not happen.
            return;
        }

        String token;
        try {
            token = switchToMap.get("roomid").toString();
        } catch (RuntimeException e) {
            // Broken message, this should not happen.
            return;
        }

        localParticipantMessageNotifier.notifySwitchTo(token);
    }

    private void processUpdateEvent(Map<String, Object> eventMap) {
        Map<String, Object> updateMap;
        try {
            updateMap = (Map<String, Object>) eventMap.get("update");
        } catch (RuntimeException e) {
            // Broken message, this should not happen.
            return;
        }

        if (updateMap == null) {
            // Broken message, this should not happen.
            return;
        }

        if (updateMap.get("all") != null && Boolean.parseBoolean(updateMap.get("all").toString())) {
            processAllParticipantsUpdate(updateMap);

            return;
        }

        if (updateMap.get("users") != null) {
            processParticipantsUpdate(updateMap);

            return;
        }
    }

    private void processAllParticipantsUpdate(Map<String, Object> updateMap) {
        // Message schema:
        // {
        //     "type": "event",
        //     "event": {
        //         "target": "participants",
        //         "type": "update",
        //         "update": {
        //             "roomid": #STRING#,
        //             "incall": 0,
        //             "all": true,
        //         },
        //     },
        // }

        long inCall;
        try {
            inCall = Long.parseLong(updateMap.get("inCall").toString());
        } catch (RuntimeException e) {
            // Broken message, this should not happen.
            return;
        }

        participantListMessageNotifier.notifyAllParticipantsUpdate(inCall);
    }

    private void processParticipantsUpdate(Map<String, Object> updateMap) {
        // Message schema:
        // {
        //     "type": "event",
        //     "event": {
        //         "target": "participants",
        //         "type": "update",
        //         "update": {
        //             "roomid": #INTEGER#,
        //             "users": [
        //                 {
        //                     "inCall": #INTEGER#,
        //                     "lastPing": #INTEGER#,
        //                     "sessionId": #STRING#,
        //                     "participantType": #INTEGER#,
        //                     "userId": #STRING#, // Optional
        //                     "nextcloudSessionId": #STRING#, // Optional
        //                     "internal": #BOOLEAN#, // Optional
        //                     "participantPermissions": #INTEGER#, // Talk >= 13
        //                 },
        //                 ...
        //             ],
        //         },
        //     },
        // }
        //
        // Note that "userId" in participants->update comes from the Nextcloud server, so it is "userId"; in other
        // messages, like room->join, it comes directly from the external signaling server, so it is "userid" instead.

        List<Map<String, Object>> users;
        try {
            users = (List<Map<String, Object>>) updateMap.get("users");
        } catch (RuntimeException e) {
            // Broken message, this should not happen.
            return;
        }

        if (users == null) {
            // Broken message, this should not happen.
            return;
        }

        List<Participant> participants = new ArrayList<>(users.size());

        for (Map<String, Object> user: users) {
            try {
                participants.add(getParticipantFromMessageMap(user));
            } catch (RuntimeException e) {
                // Broken message, this should not happen.
                return;
            }
        }

        participantListMessageNotifier.notifyParticipantsUpdate(participants);
    }

    protected void processUsersInRoom(List<Map<String, Object>> users) {
        // Message schema:
        // {
        //     "type": "usersInRoom",
        //     "data": [
        //         {
        //             "inCall": #INTEGER#,
        //             "lastPing": #INTEGER#,
        //             "roomId": #INTEGER#,
        //             "sessionId": #STRING#,
        //             "userId": #STRING#, // Always included, although it can be empty
        //             "participantPermissions": #INTEGER#, // Talk >= 13
        //         },
        //         ...
        //     ],
        // }

        List<Participant> participants = new ArrayList<>(users.size());

        for (Map<String, Object> user: users) {
            try {
                participants.add(getParticipantFromMessageMap(user));
            } catch (RuntimeException e) {
                // Broken message, this should not happen.
                return;
            }
        }

        participantListMessageNotifier.notifyUsersInRoom(participants);
    }

    /**
     * Creates and initializes a Participant from the data in the given map.
     *
     * Maps from internal and external signaling server messages can be used. Nevertheless, besides the differences
     * between the messages and the optional properties, it is expected that the message is correct and the given data
     * is parseable. Broken messages (for example, a string instead of an integer for "inCall" or a missing
     * "sessionId") may cause a RuntimeException to be thrown.
     *
     * @param participantMap the map with the participant data
     * @return the Participant
     */
    private Participant getParticipantFromMessageMap(Map<String, Object> participantMap) {
        Participant participant = new Participant();

        participant.setInCall(Long.parseLong(participantMap.get("inCall").toString()));
        participant.setLastPing(Long.parseLong(participantMap.get("lastPing").toString()));
        participant.setSessionId(participantMap.get("sessionId").toString());

        if (participantMap.get("userId") != null && !participantMap.get("userId").toString().isEmpty()) {
            participant.setUserId(participantMap.get("userId").toString());
        }

        if (participantMap.get("internal") != null && Boolean.parseBoolean(participantMap.get("internal").toString())) {
            participant.setInternal(Boolean.TRUE);
        }

        // Only in external signaling messages
        if (participantMap.get("participantType") != null) {
            int participantTypeInt = Integer.parseInt(participantMap.get("participantType").toString());

            EnumParticipantTypeConverter converter = new EnumParticipantTypeConverter();
            participant.setType(converter.getFromInt(participantTypeInt));
        }

        return participant;
    }

    protected void processSignalingMessage(NCSignalingMessage signalingMessage) {
        // Note that in the internal signaling server message "data" is the String representation of a JSON
        // object, although it is already decoded when used here.

        String type = signalingMessage.getType();

        String sessionId = signalingMessage.getFrom();
        String roomType = signalingMessage.getRoomType();

        if ("raiseHand".equals(type)) {
            // Message schema (external signaling server):
            // {
            //     "type": "message",
            //     "message": {
            //         "sender": {
            //             ...
            //         },
            //         "data": {
            //             "to": #STRING#,
            //             "sid": #STRING#,
            //             "roomType": "video",
            //             "type": "raiseHand",
            //             "payload": {
            //                 "state": #BOOLEAN#,
            //                 "timestamp": #LONG#,
            //             },
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
            //         "roomType": "video",
            //         "type": "raiseHand",
            //         "payload": {
            //             "state": #BOOLEAN#,
            //             "timestamp": #LONG#,
            //         },
            //         "from": #STRING#,
            //     },
            // }

            NCMessagePayload payload = signalingMessage.getPayload();
            if (payload == null) {
                // Broken message, this should not happen.
                return;
            }

            Boolean state = payload.getState();
            Long timestamp = payload.getTimestamp();

            if (state == null || timestamp == null) {
                // Broken message, this should not happen.
                return;
            }

            callParticipantMessageNotifier.notifyRaiseHand(sessionId, state, timestamp);

            return;
        }

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
