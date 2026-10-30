/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import android.os.CountDownTimer

class TypingParticipant(
    val userId: String,
    val name: String,
    session: String,
    val funToCallWhenTimeIsUp: (participant: TypingParticipant) -> Unit
) {
    // one timer per signaling session of the user (e.g. a device), the keys are the sessions which type
    private val timers = mutableMapOf<String, CountDownTimer>()

    init {
        addSession(session)
    }

    /**
     * A session of the user types in the room. Starts the 15 s timer of the session or restarts it when the session
     * types already. Each session expires on its own, the participant expires with its last session.
     */
    fun addSession(session: String) {
        timers.remove(session)?.cancel()
        timers[session] = object : CountDownTimer(
            TYPING_DURATION_TO_HIDE_TYPING_MESSAGE,
            TYPING_DURATION_TO_HIDE_TYPING_MESSAGE
        ) {
            override fun onTick(millisUntilFinished: Long) {
                // unused
            }

            override fun onFinish() {
                if (timers.remove(session, this) && timers.isEmpty()) {
                    funToCallWhenTimeIsUp(this@TypingParticipant)
                }
            }
        }.start()
    }

    /** Forgets the [left] sessions and cancels their timers. Returns whether the participant had any of them. */
    private fun forgetSessions(left: Collection<String>): Boolean {
        val known = left.filter { timers.containsKey(it) }
        known.forEach { timers.remove(it)?.cancel() }
        return known.isNotEmpty()
    }

    companion object {
        /**
         * A [session] of the participant [key] stopped typing: only this session is forgotten, the participant is
         * removed with its last session. Returns whether the participant was removed.
         */
        fun stopTyping(participants: MutableMap<String, TypingParticipant>, key: String, session: String): Boolean {
            val participant = participants[key]
            val removed = participant != null &&
                participant.forgetSessions(listOf(session)) &&
                participant.timers.isEmpty()
            if (removed) {
                participants.remove(key)
            }
            return removed
        }

        /**
         * Forgets the [sessions] which left the room and removes the typing participants which have no session
         * left (a user may type with several sessions). Returns whether any participant was removed.
         */
        fun removeSessions(participants: MutableMap<String, TypingParticipant>, sessions: Collection<String>): Boolean {
            var removed = false
            val iterator = participants.values.iterator()
            while (iterator.hasNext()) {
                val participant = iterator.next()
                if (participant.forgetSessions(sessions) && participant.timers.isEmpty()) {
                    iterator.remove()
                    removed = true
                }
            }
            return removed
        }

        private const val TYPING_DURATION_TO_HIDE_TYPING_MESSAGE = 15000L
    }
}
