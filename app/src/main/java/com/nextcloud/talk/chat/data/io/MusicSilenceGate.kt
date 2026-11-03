/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.io

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

private const val MUSIC_SILENCE_POLL_MS = 50L
private const val MUSIC_SILENCE_MAX_WAIT_MS = 1000L

/**
 * Whether the start of a capture still has to wait for the music of another app to stop. The wait is limited: a
 * player that does not react to the lost audio focus must not keep the recording from starting.
 */
fun shouldWaitForMusicToStop(isMusicActive: Boolean, waitedMs: Long, maxWaitMs: Long = MUSIC_SILENCE_MAX_WAIT_MS) =
    isMusicActive && waitedMs < maxWaitMs

/**
 * Delays an action until no music is playing any more. A player pauses some time after it lost the audio focus, and
 * a microphone that is opened in between records that music from the speaker. Without music the action runs at once.
 */
class MusicSilenceGate(
    private val isMusicActive: () -> Boolean,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) {
    private var pending: Runnable? = null

    fun whenSilent(onSilent: () -> Unit) {
        cancel()
        val startedAt = elapsedRealtime()
        object : Runnable {
            override fun run() {
                pending = null
                if (shouldWaitForMusicToStop(isMusicActive(), elapsedRealtime() - startedAt)) {
                    pending = this
                    handler.postDelayed(this, MUSIC_SILENCE_POLL_MS)
                } else {
                    onSilent()
                }
            }
        }.run()
    }

    /** Drops the action that still waits. */
    fun cancel() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }
}
