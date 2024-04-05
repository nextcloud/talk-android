/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import android.os.CountDownTimer

class TypingParticipant(val userId: String, val name: String, val funToCallWhenTimeIsUp: (userId: String) -> Unit) {
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
        private const val TYPING_DURATION_TO_HIDE_TYPING_MESSAGE = 15000L
    }
}
