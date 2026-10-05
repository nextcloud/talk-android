/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

/**
 * Classifies one touch on the record button: a tap only switches the record mode, a hold starts the recording.
 *
 * The recording starts only once [holdElapsed] reports that the hold threshold has passed. A release or a cancel
 * before that is never a recording. Has no Android dependencies, the caller owns the timer and the touch events.
 */
class RecordButtonGesture(private val cancelX: Float) {

    enum class State { IDLE, PENDING, HELD }

    enum class Release { TOGGLE_MODE, NONE }

    var state: State = State.IDLE
        private set

    val isPending: Boolean get() = state == State.PENDING

    fun down() {
        state = State.PENDING
    }

    /** The hold threshold passed. Returns true if the recording may start now. */
    fun holdElapsed(): Boolean {
        if (state != State.PENDING) return false
        state = State.HELD
        return true
    }

    /** Finger moved to [x] (button coordinates). Returns true if the swipe cancelled a not yet started recording. */
    fun move(x: Float): Boolean {
        if (state != State.PENDING || x >= cancelX) return false
        state = State.IDLE
        return true
    }

    /** Finger released. A release before the hold threshold is a tap. */
    fun up(): Release {
        val wasPending = state == State.PENDING
        state = State.IDLE
        return if (wasPending) Release.TOGGLE_MODE else Release.NONE
    }

    /** The system cancelled the touch. Never switches the mode. */
    fun cancel() {
        state = State.IDLE
    }

    companion object {
        const val MIN_HOLD_MS = 400L
        const val MAX_HOLD_MS = 600L

        /**
         * Hold threshold from the system long-press timeout (400 ms by default, longer when the user raised it for
         * accessibility). A touch shorter than the system long press is a click for every other control, so the same
         * touch must not start a recording. The upper bound keeps the start delay bearable.
         */
        fun holdThresholdMs(systemLongPressMs: Int): Long =
            systemLongPressMs.toLong().coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)

        /**
         * Whether a recording which started at [startedAtMs] and ends at [endedAtMs] is shorter than [minMs]. Both
         * times are taken when the recording actually starts and stops, not at the touch of the button, which
         * precedes the start by the hold threshold.
         */
        fun isTooShort(startedAtMs: Long, endedAtMs: Long, minMs: Int): Boolean = endedAtMs - startedAtMs < minMs
    }
}
