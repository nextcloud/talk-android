/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

/**
 * Decides when a mute state reported by Telecom has to be applied to the call's microphone.
 *
 * Telecom's mute is the system-wide microphone mute, toggled e.g. by the mute button of a Bluetooth headset, a car
 * head unit or a watch. Telecom reports the current state once when the call starts; that value only serves as the
 * baseline, otherwise a call started muted would be unmuted right away. Only later changes are applied.
 *
 * When the app turns its microphone on while Telecom has the microphone muted, it lifts the system mute itself (else
 * the microphone would stay silent). Telecom may report "unmuted" in response, which must not be applied: during push
 * to talk the app's microphone is only on temporarily, and applying the echo afterwards would unmute the call. Not
 * every Telecom implementation echoes it, so only an "unmuted" report shortly after lifting the mute counts as echo;
 * a later one is a real unmute, e.g. from the headset.
 *
 * Times are passed in (milliseconds, any monotonic clock) to keep this testable.
 */
class TelecomMuteSync {

    private var previous: Boolean? = null
    private var systemUnmutedByAppAt: Long? = null

    /**
     * Returns the mute state to apply to the microphone, or null if nothing has to change. A null [muted] means
     * there is no Telecom call (anymore) and resets the baseline.
     */
    fun onTelecomMuteReported(muted: Boolean?, nowMillis: Long): Boolean? {
        val last = previous
        previous = muted
        val unmutedByAppAt = systemUnmutedByAppAt
        val withinEchoWindow = unmutedByAppAt != null && nowMillis - unmutedByAppAt <= ECHO_WINDOW_MILLIS
        val isEchoOfAppUnmute = muted == false && withinEchoWindow
        // the echo is awaited until it arrives or the window is over; other reports meanwhile don't end the wait
        if (muted != true || !withinEchoWindow) {
            systemUnmutedByAppAt = null
        }
        return muted?.takeIf { last != null && it != last && !isEchoOfAppUnmute }
    }

    /** The app lifted the system microphone mute itself; Telecom's echo of that is not applied. */
    fun onSystemMicrophoneUnmutedByApp(nowMillis: Long) {
        systemUnmutedByAppAt = nowMillis
    }

    companion object {
        const val ECHO_WINDOW_MILLIS = 2000L
    }
}
