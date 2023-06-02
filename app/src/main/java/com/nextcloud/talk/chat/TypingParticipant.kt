/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2021-2022 Marcel Hibbe <dev@mhibbe.de>
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
