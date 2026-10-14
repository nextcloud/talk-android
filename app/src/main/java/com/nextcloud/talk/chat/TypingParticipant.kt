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
    val session: String?,
    val funToCallWhenTimeIsUp: (userId: String) -> Unit
) {
    var timer: CountDownTimer? = null

    init {
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

    fun restartTimer() {
        timer?.cancel()
        timer?.start()
    }

    fun cancelTimer() {
        timer?.cancel()
    }

    companion object {
        /**
         * Removes the typing participants which type with one of the [sessions] (which left the room).
         * Returns whether any was removed.
         */
        fun removeSessions(participants: MutableMap<String, TypingParticipant>, sessions: Collection<String>): Boolean {
            val left = participants.filterValues { it.session in sessions }
            left.forEach { (key, participant) ->
                participant.cancelTimer()
                participants.remove(key)
            }
            return left.isNotEmpty()
        }

        private const val TYPING_DURATION_TO_HIDE_TYPING_MESSAGE = 15000L
    }
}
