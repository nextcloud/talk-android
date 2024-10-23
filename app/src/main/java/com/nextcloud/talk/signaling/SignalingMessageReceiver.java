/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling;

import com.nextcloud.talk.models.json.converters.EnumActorTypeConverter;
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.signaling.NCIceCandidate;
import com.nextcloud.talk.models.json.signaling.NCMessagePayload;
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;
import com.nextcloud.talk.models.json.websocket.CallWebSocketMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hub to register listeners for signaling messages of different kinds.
 * <p>
 * In general, if a listener is added while an event is being handled the new listener will not receive that event.
 * An exception to that is adding a WebRtcMessageListener when handling an offer in an OfferMessageListener; in that
 * case the "onOffer()" method of the WebRtcMessageListener will be called for that same offer.
 * <p>
 * Similarly, if a listener is removed while an event is being handled the removed listener will still receive that
 * event. Again the exception is removing a WebRtcMessageListener when handling an offer in an OfferMessageListener; in
 * that case the "onOffer()" method of the WebRtcMessageListener will not be called for that offer.
 * <p>
 * Adding and removing listeners, as well as notifying them is internally synchronized. This should be kept in mind
 * if listeners are added or removed when handling an event to prevent deadlocks (nevertheless, just adding or
 * removing a listener in the same thread handling the event is fine, and in most cases it will be fine too if done
 * in a different thread, as long as the notifier thread is not forced to wait until the listener is added or removed).
 * <p>
 * SignalingMessageReceiver does not fetch the signaling messages itself; subclasses must fetch them and then call
 * the appropriate protected methods to process the messages and notify the listeners.
 */
public abstract class SignalingMessageReceiver {

    private final EnumActorTypeConverter enumActorTypeConverter = new EnumActorTypeConverter();

    private final ParticipantListMessageNotifier participantListMessageNotifier = new ParticipantListMessageNotifier();

    private final LocalParticipantMessageNotifier localParticipantMessageNotifier = new LocalParticipantMessageNotifier();

    private final CallParticipantMessageNotifier callParticipantMessageNotifier = new CallParticipantMessageNotifier();

    private final ConversationMessageNotifier conversationMessageNotifier = new ConversationMessageNotifier();

    private final OfferMessageNotifier offerMessageNotifier = new OfferMessageNotifier();

    private final WebRtcMessageNotifier webRtcMessageNotifier = new WebRtcMessageNotifier();

    /**
     * Listener for participant list messages.
     * <p>
     * The messages are implicitly bound to the room currently joined in the signaling server; listeners are expected
     * to know the current room.
     */
    public interface ParticipantListMessageListener {

        /**
         * List of all the participants in the room.
         * <p>
         * This message is received only when the internal signaling server is used.
         * <p>
         * The message is received periodically, and the participants may not have been modified since the last message.
         * <p>
         * Only the following participant properties are set:
         * - inCall
         * - lastPing
         * - sessionId
         * - userId (if the participant is not a guest)
         * <p>
         * "participantPermissions" is provided in the message (since Talk 13), but not currently set in the
         * participant. "publishingPermissions" was provided instead in Talk 12, but it was not used anywhere, so it is
         * ignored.
         *
         * @param participants all the participants (users and guests) in the room
         */
        void onUsersInRoom(List<Participant> participants);

        /**
         * List of all the participants in the call or the room (depending on what triggered the event).
         * <p>
         * This message is received only when the external signaling server is used.
         * <p>
         * The message is received when any participant changed, although what changed is not provided and should be
         * derived from the difference with previous messages. The list of participants may include only the
         * participants in the call (including those that just left it and thus triggered the event) or all the
         * participants currently in the room (participants in the room but not currently active, that is, without a
         * session, are not included).
         * <p>
         * Only the following participant properties are set:
         * - inCall
         * - lastPing
         * - sessionId
         * - type
         * - userId (if the participant is not a guest)
         * <p>
         * "nextcloudSessionId" is provided in the message (when the "inCall" property of any participant changed), but
         * not currently set in the participant.
         * <p>
         * "participantPermissions" is provided in the message (since Talk 13), but not currently set in the
         * participant. "publishingPermissions" was provided instead in Talk 12, but it was not used anywhere, so it is
         * ignored.
         *
         * @param participants all the participants (users and guests) in the room
         */
        void onParticipantsUpdate(List<Participant> participants);

        /**
         * Update of the properties of all the participants in the room.
         * <p>
         * This message is received only when the external signaling server is used.
         *
         * @param inCall the new value of the inCall property
         */
        void onAllParticipantsUpdate(long inCall);
    }

    /**
     * Listener for local participant messages.
     * <p>
     * The messages are implicitly bound to the local participant (or, rather, its session); listeners are expected
     * to know the local participant.
     * <p>
     * The messages are related to the conversation, so the local participant may or may not be in a call when they
     * are received.
     */
    public interface LocalParticipantMessageListener {
        /**
         * Request for the client to switch to the given conversation.
         * <p>
         * This message is received only when the external signaling server is used.
         *
         * @param token the token of the conversation to switch to.
         */
        void onSwitchTo(String token);
    }

    /**
     * Listener for call participant messages.
     * <p>
     * The messages are bound to a specific call participant (or, rather, session), so each listener is expected to
     * handle messages only for a single call participant.
     * <p>
     * Although "unshareScreen" is technically bound to a specific peer connection it is instead treated as a general
     * message on the call participant.
     */
    public interface CallParticipantMessageListener {
        void onRaiseHand(boolean state, long timestamp);
        void onReaction(String reaction);
        void onUnshareScreen();
    }

    /**
     * Listener for conversation messages.
     */
    public interface ConversationMessageListener {
        void onStartTyping(String userId, String session);
        void onStopTyping(String userId,String session);
    }

    /**
     * Listener for WebRTC offers.
     * <p>
     * Unlike the WebRtcMessageListener, which is bound to a specific peer connection, an OfferMessageListener listens
     * to all offer messages, no matter which peer connection they are bound to. This can be used, for example, to
     * create a new peer connection when a remote offer for which there is no previous connection is received.
     * <p>
     * When an offer is received all OfferMessageListeners are notified before any WebRtcMessageListener is notified.
     */
    public interface OfferMessageListener {
        void onOffer(String sessionId, String roomType, String sdp, String nick);
    }

    /**
     * Listener for WebRTC messages.
     * <p>
     * The messages are bound to a specific peer connection, so each listener is expected to handle messages only for
     * a single peer connection.
     */
    public interface WebRtcMessageListener {
        void onOffer(String sdp, String nick);
        void onAnswer(String sdp, String nick);
        void onCandidate(String sdpMid, int sdpMLineIndex, String sdp);
        void onEndOfCandidates();
    }

    /**
     * Adds a listener for participant list messages.
     * <p>
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
     * <p>
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
     * <p>
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

    public void addListener(ConversationMessageListener listener) {
        conversationMessageNotifier.addListener(listener);
    }

    public void removeListener(ConversationMessageListener listener) {
        conversationMessageNotifier.removeListener(listener);
    }

    /**
     * Adds a listener for all offer messages.
     * <p>
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
     * <p>
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
        //
        // Note that "incall" in participants->update is all in lower case when the message applies to all participants,
        // even if it is "inCall" when the message provides separate properties for each participant.

        long inCall;
        try {
            inCall = Long.parseLong(updateMap.get("incall").toString());
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
        //                     "actorType": #STRING#, // Talk >= 20
        //                     "actorId": #STRING#, // Talk >= 20
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
        //             "actorType": #STRING#, // Talk >= 20
        //             "actorId": #STRING#, // Talk >= 20
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
     * <p>
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

        if (participantMap.get("actorType") != null && !participantMap.get("actorType").toString().isEmpty()) {
            participant.setActorType(enumActorTypeConverter.getFromString(participantMap.get("actorType").toString()));
        }

        if (participantMap.get("actorId") != null && !participantMap.get("actorId").toString().isEmpty()) {
            participant.setActorId(participantMap.get("actorId").toString());
        }

        // Only in external signaling messages
        if (participantMap.get("participantType") != null) {
            int participantTypeInt = Integer.parseInt(participantMap.get("participantType").toString());

            EnumParticipantTypeConverter converter = new EnumParticipantTypeConverter();
            participant.setType(converter.getFromInt(participantTypeInt));
        }

        return participant;
    }

    protected void processCallWebSocketMessage(CallWebSocketMessage callWebSocketMessage) {

        NCSignalingMessage signalingMessage = callWebSocketMessage.getNcSignalingMessage();

        if (callWebSocketMessage.getSenderWebSocketMessage() != null && signalingMessage != null) {
            String type = signalingMessage.getType();

            String userId = callWebSocketMessage.getSenderWebSocketMessage().getUserid();
            String sessionId = signalingMessage.getFrom();

            if ("startedTyping".equals(type)) {
                conversationMessageNotifier.notifyStartTyping(userId, sessionId);
            }

            if ("stoppedTyping".equals(type)) {
                conversationMessageNotifier.notifyStopTyping(userId, sessionId);
            }
        }
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

        if ("reaction".equals(type)) {
            // Message schema (external signaling server):
            // {
            //     "type": "message",
            //     "message": {
            //         "sender": {
            //             ...
            //         },
            //         "data": {
            //             "to": #STRING#,
            //             "roomType": "video",
            //             "type": "reaction",
            //             "payload": {
            //                 "reaction": #STRING#,
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
            //         "roomType": "video",
            //         "type": "reaction",
            //         "payload": {
            //             "reaction": #STRING#,
            //         },
            //         "from": #STRING#,
            //     },
            // }

            NCMessagePayload payload = signalingMessage.getPayload();
            if (payload == null) {
                // Broken message, this should not happen.
                return;
            }

            String reaction = payload.getReaction();
            if (reaction == null) {
                // Broken message, this should not happen.
                return;
            }

            callParticipantMessageNotifier.notifyReaction(sessionId, reaction);

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
