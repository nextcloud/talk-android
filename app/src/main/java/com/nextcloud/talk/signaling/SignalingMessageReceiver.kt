/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling

import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.converters.EnumActorTypeConverter
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage
import com.nextcloud.talk.models.json.websocket.CallWebSocketMessage
import org.json.JSONObject
import kotlin.Any
import kotlin.Int
import kotlin.Long
import kotlin.RuntimeException
import kotlin.String
import kotlin.toString

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
 *
 * Adding and removing listeners, as well as notifying them is internally synchronized. This should be kept in mind
 * if listeners are added or removed when handling an event to prevent deadlocks (nevertheless, just adding or
 * removing a listener in the same thread handling the event is fine, and in most cases it will be fine too if done
 * in a different thread, as long as the notifier thread is not forced to wait until the listener is added or removed).
 *
 * SignalingMessageReceiver does not fetch the signaling messages itself; subclasses must fetch them and then call
 * the appropriate protected methods to process the messages and notify the listeners.
 */
abstract class SignalingMessageReceiver {
    private val enumActorTypeConverter = EnumActorTypeConverter()

    private val participantListMessageNotifier = ParticipantListMessageNotifier()

    private val localParticipantMessageNotifier = LocalParticipantMessageNotifier()

    private val callParticipantMessageNotifier = CallParticipantMessageNotifier()

    private val conversationMessageNotifier = ConversationMessageNotifier()

    private val offerMessageNotifier = OfferMessageNotifier()

    private val webRtcMessageNotifier = WebRtcMessageNotifier()

    /**
     * Listener for participant list messages.
     *
     * The messages are implicitly bound to the room currently joined in the signaling server; listeners are expected
     * to know the current room.
     */
    interface ParticipantListMessageListener {
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
        fun onUsersInRoom(participants: MutableList<Participant?>?)

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
        fun onParticipantsUpdate(participants: MutableList<Participant?>?)

        /**
         * Update of the properties of all the participants in the room.
         *
         * This message is received only when the external signaling server is used.
         *
         * @param inCall the new value of the inCall property
         */
        fun onAllParticipantsUpdate(inCall: Long)
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
    fun interface LocalParticipantMessageListener {
        /**
         * Request for the client to switch to the given conversation.
         *
         * This message is received only when the external signaling server is used.
         *
         * @param token the token of the conversation to switch to.
         */
        fun onSwitchTo(token: String)
    }

    /**
     * Listener for call participant messages.
     *
     *
     * The messages are bound to a specific call participant (or, rather, session), so each listener is expected to
     * handle messages only for a single call participant.
     *
     *
     * Although "unshareScreen" is technically bound to a specific peer connection it is instead treated as a general
     * message on the call participant.
     */
    interface CallParticipantMessageListener {
        fun onRaiseHand(state: Boolean, timestamp: Long)
        fun onReaction(reaction: String)
        fun onUnshareScreen()
    }

    /**
     * Listener for conversation messages.
     */
    interface ConversationMessageListener {
        fun onStartTyping(userId: String?, session: String?)
        fun onStopTyping(userId: String?, session: String?)
        fun onChatMessageReceived(chatMessage: ChatMessageJson)
    }

    /**
     * Listener for WebRTC offers.
     *
     *
     * Unlike the WebRtcMessageListener, which is bound to a specific peer connection, an OfferMessageListener listens
     * to all offer messages, no matter which peer connection they are bound to. This can be used, for example, to
     * create a new peer connection when a remote offer for which there is no previous connection is received.
     *
     *
     * When an offer is received all OfferMessageListeners are notified before any WebRtcMessageListener is notified.
     */
    fun interface OfferMessageListener {
        fun onOffer(sessionId: String?, roomType: String, sdp: String?, nick: String?)
    }

    /**
     * Listener for WebRTC messages.
     *
     *
     * The messages are bound to a specific peer connection, so each listener is expected to handle messages only for
     * a single peer connection.
     */
    interface WebRtcMessageListener {
        fun onOffer(sdp: String, nick: String?)
        fun onAnswer(sdp: String, nick: String?)
        fun onCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String)
        fun onEndOfCandidates()
    }

    /**
     * Adds a listener for participant list messages.
     *
     *
     * A listener is expected to be added only once. If the same listener is added again it will be notified just once.
     *
     * @param listener the ParticipantListMessageListener
     */
    fun addListener(listener: ParticipantListMessageListener?) {
        participantListMessageNotifier.addListener(listener)
    }

    fun removeListener(listener: ParticipantListMessageListener?) {
        participantListMessageNotifier.removeListener(listener)
    }

    /**
     * Adds a listener for local participant messages.
     *
     *
     * A listener is expected to be added only once. If the same listener is added again it will be notified just once.
     *
     * @param listener the LocalParticipantMessageListener
     */
    fun addListener(listener: LocalParticipantMessageListener?) {
        localParticipantMessageNotifier.addListener(listener)
    }

    fun removeListener(listener: LocalParticipantMessageListener?) {
        localParticipantMessageNotifier.removeListener(listener)
    }

    /**
     * Adds a listener for call participant messages.
     *
     *
     * A listener is expected to be added only once. If the same listener is added again it will no longer be notified
     * for the messages from the previous session ID.
     *
     * @param listener the CallParticipantMessageListener
     * @param sessionId the ID of the session that messages come from
     */
    fun addListener(listener: CallParticipantMessageListener?, sessionId: String?) {
        callParticipantMessageNotifier.addListener(listener, sessionId)
    }

    fun removeListener(listener: CallParticipantMessageListener?) {
        callParticipantMessageNotifier.removeListener(listener)
    }

    fun addListener(listener: ConversationMessageListener?) {
        conversationMessageNotifier.addListener(listener)
    }

    fun removeListener(listener: ConversationMessageListener) {
        conversationMessageNotifier.removeListener(listener)
    }

    /**
     * Adds a listener for all offer messages.
     *
     *
     * A listener is expected to be added only once. If the same listener is added again it will be notified just once.
     *
     * @param listener the OfferMessageListener
     */
    fun addListener(listener: OfferMessageListener?) {
        offerMessageNotifier.addListener(listener)
    }

    fun removeListener(listener: OfferMessageListener?) {
        offerMessageNotifier.removeListener(listener)
    }

    /**
     * Adds a listener for WebRTC messages from the given session ID and room type.
     *
     *
     * A listener is expected to be added only once. If the same listener is added again it will no longer be notified
     * for the messages from the previous session ID or room type.
     *
     * @param listener the WebRtcMessageListener
     * @param sessionId the ID of the session that messages come from
     * @param roomType the room type that messages come from
     */
    fun addListener(listener: WebRtcMessageListener?, sessionId: String?, roomType: String?) {
        webRtcMessageNotifier.addListener(listener, sessionId, roomType)
    }

    fun removeListener(listener: WebRtcMessageListener?) {
        webRtcMessageNotifier.removeListener(listener)
    }

    fun processEvent(eventMap: Map<String, Any>?) {
        if ("room" == eventMap?.get("target") && "switchto" == eventMap["type"]) {
            processSwitchToEvent(eventMap)

            return
        }

        if ("participants" == eventMap?.get("target") && "update" == eventMap["type"]) {
            processUpdateEvent(eventMap)

            return
        }
    }

    private fun processSwitchToEvent(eventMap: Map<String, Any>?) {
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

        val switchToMap: Map<String, Any>?
        try {
            switchToMap = eventMap?.get("switchto") as Map<String, Any>?
        } catch (e: RuntimeException) {
            // Broken message, this should not happen.
            return
        }

        if (switchToMap == null) {
            // Broken message, this should not happen.
            return
        }

        val token: String?
        try {
            token = switchToMap["roomid"].toString()
        } catch (e: RuntimeException) {
            // Broken message, this should not happen.
            return
        }

        localParticipantMessageNotifier.notifySwitchTo(token)
    }

    protected fun processChatMessageWebSocketMessage(jsonString: String) {
        fun parseChatMessage(jsonString: String): ChatMessageJson? {
            return try {
                val root = JSONObject(jsonString)
                val eventObj = root.optJSONObject("event") ?: return null
                val messageObj = eventObj.optJSONObject("message") ?: return null
                val dataObj = messageObj.optJSONObject("data") ?: return null
                val chatObj = dataObj.optJSONObject("chat") ?: return null
                val commentObj = chatObj.optJSONObject("comment") ?: return null

                LoganSquare.parse(commentObj.toString(), ChatMessageJson::class.java)
            } catch (e: Exception) {
                null
            }
        }

        val chatMessage = parseChatMessage(jsonString)

        chatMessage?.let {
            conversationMessageNotifier.notifyMessageReceived(it)
        }
    }

    private fun processUpdateEvent(eventMap: Map<String, Any>?) {
        val updateMap: Map<String, Any>?
        try {
            updateMap = eventMap?.get("update") as Map<String, Any>?
        } catch (e: RuntimeException) {
            // Broken message, this should not happen.
            return
        }

        if (updateMap == null) {
            // Broken message, this should not happen.
            return
        }

        if (updateMap["all"] != null && updateMap["all"].toString().toBoolean()) {
            processAllParticipantsUpdate(updateMap)

            return
        }

        if (updateMap["users"] != null) {
            processParticipantsUpdate(updateMap)

            return
        }
    }

    private fun processAllParticipantsUpdate(updateMap: Map<String, Any>) {
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

        val inCall: Long
        try {
            inCall = updateMap["incall"].toString().toLong()
        } catch (e: RuntimeException) {
            // Broken message, this should not happen.
            return
        }

        participantListMessageNotifier.notifyAllParticipantsUpdate(inCall)
    }

    private fun processParticipantsUpdate(updateMap: Map<String, Any>) {
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

        val users: List<Map<String, Any>>?
        try {
            users = updateMap["users"] as List<Map<String, Any>>?
        } catch (e: RuntimeException) {
            // Broken message, this should not happen.
            return
        }

        if (users == null) {
            // Broken message, this should not happen.
            return
        }

        val participants: MutableList<Participant?> = ArrayList(users.size)

        for (user in users) {
            try {
                participants.add(getParticipantFromMessageMap(user))
            } catch (e: RuntimeException) {
                // Broken message, this should not happen.
                return
            }
        }

        participantListMessageNotifier.notifyParticipantsUpdate(participants)
    }

    fun processUsersInRoom(users: List<Map<String?, Any?>>) {
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

        val participants: MutableList<Participant?> = ArrayList(users.size)

        for (user in users) {
            val nullSafeUserMap = user as? Map<String, Any> ?: return
            try {
                participants.add(getParticipantFromMessageMap(nullSafeUserMap))
            } catch (e: RuntimeException) {
                // Broken message, this should not happen.
                return
            }
        }

        participantListMessageNotifier.notifyUsersInRoom(participants)
    }

    /**
     * Creates and initializes a Participant from the data in the given map.
     *
     *
     * Maps from internal and external signaling server messages can be used. Nevertheless, besides the differences
     * between the messages and the optional properties, it is expected that the message is correct and the given data
     * is parseable. Broken messages (for example, a string instead of an integer for "inCall" or a missing
     * "sessionId") may cause a RuntimeException to be thrown.
     *
     * @param participantMap the map with the participant data
     * @return the Participant
     */
    private fun getParticipantFromMessageMap(participantMap: Map<String, Any>): Participant {
        val participant = Participant()

        participant.inCall = participantMap["inCall"].toString().toLong()
        participant.lastPing = participantMap["lastPing"].toString().toLong()
        participant.sessionId = participantMap["sessionId"].toString()

        if (participantMap["userId"] != null && !participantMap["userId"].toString().isEmpty()) {
            participant.userId = participantMap["userId"].toString()
        }

        if (participantMap["internal"] != null && participantMap["internal"].toString().toBoolean()) {
            participant.internal = true
        }

        if (participantMap["actorType"] != null && !participantMap["actorType"].toString().isEmpty()) {
            participant.actorType = enumActorTypeConverter.getFromString(participantMap["actorType"].toString())
        }

        if (participantMap["actorId"] != null && !participantMap["actorId"].toString().isEmpty()) {
            participant.actorId = participantMap["actorId"].toString()
        }

        // Only in external signaling messages
        if (participantMap["participantType"] != null) {
            val participantTypeInt = participantMap["participantType"].toString().toInt()

            val converter = EnumParticipantTypeConverter()
            participant.type = converter.getFromInt(participantTypeInt)
        }

        return participant
    }

    protected fun processCallWebSocketMessage(callWebSocketMessage: CallWebSocketMessage) {
        val signalingMessage = callWebSocketMessage.ncSignalingMessage

        if (callWebSocketMessage.senderWebSocketMessage != null && signalingMessage != null) {
            val type = signalingMessage.type

            val userId = callWebSocketMessage.senderWebSocketMessage!!.userid
            val sessionId = signalingMessage.from

            if ("startedTyping" == type) {
                conversationMessageNotifier.notifyStartTyping(userId, sessionId)
            }

            if ("stoppedTyping" == type) {
                conversationMessageNotifier.notifyStopTyping(userId, sessionId)
            }
        }
    }

    fun processSignalingMessage(signalingMessage: NCSignalingMessage?) {
        if (signalingMessage == null) {
            return
        }

        // Note that in the internal signaling server message "data" is the String representation of a JSON
        // object, although it is already decoded when used here.

        val type = signalingMessage.type

        val sessionId = signalingMessage.from
        val roomType = signalingMessage.roomType

        if ("raiseHand" == type) {
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

            val payload = signalingMessage.payload ?: return
            val state = payload.state ?: return
            val timestamp = payload.timestamp ?: return

            callParticipantMessageNotifier.notifyRaiseHand(sessionId, state, timestamp)

            return
        }

        if ("reaction" == type) {
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

            val payload = signalingMessage.payload ?: return

            val reaction = payload.reaction ?: return

            callParticipantMessageNotifier.notifyReaction(sessionId, reaction)

            return
        }

        // "unshareScreen" messages are directly sent to the screen peer connection when the internal signaling
        // server is used, and to the room when the external signaling server is used. However, the (relevant) data
        // of the received message ("from" and "type") is the same in both cases.
        if ("unshareScreen" == type) {
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

            callParticipantMessageNotifier.notifyUnshareScreen(sessionId)

            return
        }

        if ("offer" == type) {
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

            val payload = signalingMessage.payload ?: return

            val sdp = payload.sdp
            val nick = payload.nick

            // If "processSignalingMessage" is called with two offers from two different threads it is possible,
            // although extremely unlikely, that the WebRtcMessageListeners for the second offer are notified before the
            // WebRtcMessageListeners for the first offer. This should not be a problem, though, so for simplicity
            // the statements are not synchronized.
            offerMessageNotifier.notifyOffer(sessionId, roomType, sdp, nick)
            webRtcMessageNotifier.notifyOffer(sessionId, roomType, sdp, nick)

            return
        }

        if ("answer" == type) {
            // Message schema: same as offers, but with type "answer".

            val payload = signalingMessage.payload ?: return

            val sdp = payload.sdp
            val nick = payload.nick

            webRtcMessageNotifier.notifyAnswer(sessionId, roomType, sdp, nick)

            return
        }

        if ("candidate" == type) {
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

            val payload = signalingMessage.payload ?: return

            val ncIceCandidate = payload.iceCandidate ?: return

            webRtcMessageNotifier.notifyCandidate(
                sessionId,
                roomType,
                ncIceCandidate.sdpMid,
                ncIceCandidate.sdpMLineIndex,
                ncIceCandidate.candidate
            )

            return
        }

        if ("endOfCandidates" == type) {
            webRtcMessageNotifier.notifyEndOfCandidates(sessionId, roomType)

            return
        }
    }
}
