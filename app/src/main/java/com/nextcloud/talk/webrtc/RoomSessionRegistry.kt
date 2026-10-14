/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.models.json.participants.ParticipantDto.ActorType
import java.util.concurrent.ConcurrentHashMap

/**
 * The signaling sessions of the room the external signaling connection is currently in.
 *
 * One [WebSocketInstance] lives per account and is shared by all conversations opened with that account, but the
 * signaling server only reports the sessions of the room the connection joined. So the set must hold exactly the
 * sessions of the current room: sessions of a previous room (or of an expired connection) must not stay in it,
 * otherwise typing messages are sent to sessions of other rooms and typing messages of such sessions are accepted
 * (the server delivers messages addressed to a session no matter which room that session is in).
 *
 * Written from the OkHttp thread, read from the UI thread.
 */
class RoomSessionRegistry {
    private val sessions = ConcurrentHashMap<String, ParticipantDto>()

    @Volatile
    private var roomToken: String? = null

    /**
     * The server answers every room join/leave with a "room" message. It is sent before the list of the sessions
     * of the new room, so clearing here cannot drop sessions of the new room. The same room (re-sent for an
     * unchanged join) keeps its sessions. After [reset] the room is unknown, so the answer to a leave (empty room)
     * clears as well: it drops what late events of the left room added in between.
     */
    fun onRoomResponse(newRoomToken: String?) {
        val token = newRoomToken ?: ""
        if (token != roomToken) {
            sessions.clear()
            roomToken = token
        }
    }

    /**
     * The connection leaves the room, got a new signaling session (hello without resume) or lost it
     * (no_such_session, no_such_room). Nothing is known about the room any more.
     */
    fun reset() {
        sessions.clear()
        roomToken = null
    }

    /**
     * Processes the "join" list of a room event: maps with "sessionid", optional "userid" and optional "user"
     * (with "displayname"). A guest joins without "userid" and stays in the registry.
     *
     * Ignored while no room is known (after [reset] or the answer to a leave): the server sends the answer to a join
     * before the list of the sessions, so a join arriving without a room is a late event of a room which was left.
     */
    fun onJoin(joinEvents: List<Map<String, Any>>?) {
        if (roomToken.isNullOrEmpty()) return

        joinEvents?.forEach { event ->
            val sessionId = event["sessionid"] as? String ?: return@forEach
            val participant = ParticipantDto()
            val userId = event["userid"] as? String
            if (userId != null) {
                participant.actorType = ActorType.USERS
                participant.actorId = userId
            } else {
                participant.actorType = ActorType.GUESTS
            }
            // There is no "user" attribute for guest participants.
            val userMap = event["user"] as? Map<*, *>
            participant.displayName = userMap?.get("displayname") as? String
            sessions[sessionId] = participant
        }
    }

    fun onLeave(sessionIds: List<String>?) {
        sessionIds?.forEach { sessions.remove(it) }
    }

    fun contains(sessionId: String?): Boolean = sessionId != null && sessions.containsKey(sessionId)

    /** Display name of the session, or "" if the session is unknown or has no name (e.g. a guest). */
    fun displayNameFor(sessionId: String?): String = sessionId?.let { sessions[it]?.displayName } ?: ""

    /** Snapshot of the sessions of the current room, without the own session. */
    fun sessionIdsExcept(ownSessionId: String?): List<String> = sessions.keys.filter { it != ownSessionId }
}
