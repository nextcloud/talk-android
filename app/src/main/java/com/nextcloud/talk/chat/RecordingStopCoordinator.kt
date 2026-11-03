/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.os.Handler
import android.os.Looper

/**
 * Runs a delayed [Runnable] on the main thread; replaced in tests.
 */
internal interface DelayScheduler {
    fun postDelayed(runnable: Runnable, delayMs: Long)
    fun cancel(runnable: Runnable)
}

internal class MainThreadScheduler : DelayScheduler {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    override fun postDelayed(runnable: Runnable, delayMs: Long) {
        handler.postDelayed(runnable, delayMs)
    }

    override fun cancel(runnable: Runnable) {
        handler.removeCallbacks(runnable)
    }
}

/**
 * How a running recording is asked to end.
 */
internal enum class StopAction { SEND, DISCARD }

/**
 * Decides when a running recording may be stopped.
 *
 * After a camera switch the CameraX recorder sets up a new video surface for [SWITCH_SETTLE_MS]. If it is stopped
 * meanwhile, the setup ends in an AssertionError of CameraX (onConfigured() in a STOPPING state). So no stop is
 * issued inside that window: it is held back and issued when the window ends.
 *
 * The first request ends the recording and is final: a send after a cancel must not send the cancelled video, nor a
 * cancel after a send discard the video which is being sent.
 */
internal class RecordingStopCoordinator(private val scheduler: DelayScheduler, private val stopRecording: () -> Unit) {
    var action: StopAction? = null
        private set

    var settling = false
        private set

    private var stopPending = false
    private val windowEnded = Runnable {
        settling = false
        if (stopPending) {
            stopPending = false
            stopRecording()
        }
    }

    /**
     * Opens the window after a camera switch. Refused while another window is open or once a stop was requested.
     */
    fun beginSwitch(): Boolean {
        if (action != null || settling) return false
        settling = true
        scheduler.postDelayed(windowEnded, SWITCH_SETTLE_MS)
        return true
    }

    /**
     * @return false if the request is ignored because an earlier one decided already
     */
    fun requestStop(requested: StopAction): Boolean {
        if (action != null) return false
        action = requested
        if (settling) {
            stopPending = true
        } else {
            stopRecording()
        }
        return true
    }

    /**
     * Forgets everything, the recording is over.
     */
    fun reset() {
        scheduler.cancel(windowEnded)
        settling = false
        stopPending = false
        action = null
    }

    companion object {
        /**
         * How long after a camera switch the CameraX recorder may still be setting up its new video surface.
         */
        const val SWITCH_SETTLE_MS = 1_500L
    }
}
