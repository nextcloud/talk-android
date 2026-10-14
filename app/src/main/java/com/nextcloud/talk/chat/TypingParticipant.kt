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
    session: String?,
    val funToCallWhenTimeIsUp: (userId: String) -> Unit
) {
    private val sessions = mutableSetOf<String>()
    var timer: CountDownTimer? = null

    init {
        addSession(session)
        startTimer()
    }

    private fun startTimer() {
        timer = object : CountDownTimer(
            TYPING_DURATION_TO_HIDE_TYPING_MESSAGE,
            TYPING_DURATION_TO_HIDE_TYPING_MESSAGE
        ) {
            override fun onTick(millisUntilFinished: Long) {
                // unused
            }

            override fun onFinish() {
                funToCallWhenTimeIsUp(userId)
            }
        }.start()
    }

    /** Another signaling session of the same user, e.g. a second device, types in the room. */
    fun addSession(session: String?) {
        session?.let { sessions.add(it) }
    }

    fun restartTimer() {
        timer?.cancel()
        timer?.start()
    }

    fun cancelTimer() {
        timer?.cancel()
    }

    companion object {
        /**
         * Forgets the [sessions] which left the room and removes the typing participants which have no session
         * left (a user may type with several sessions). Returns whether any participant was removed.
         */
        fun removeSessions(participants: MutableMap<String, TypingParticipant>, sessions: Collection<String>): Boolean {
            val left = participants.filterValues { participant ->
                participant.sessions.removeAll(sessions.toSet()) && participant.sessions.isEmpty()
            }
            left.forEach { (key, participant) ->
                participant.cancelTimer()
                participants.remove(key)
            }
            return left.isNotEmpty()
        }

        private const val TYPING_DURATION_TO_HIDE_TYPING_MESSAGE = 15000L
    }
}
