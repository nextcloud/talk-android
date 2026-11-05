/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import com.nextcloud.talk.call.TelecomMuteSync.Companion.ECHO_WINDOW_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelecomMuteSyncTest {

    private val start = 10_000L

    @Test
    fun `first reported state is only the baseline`() {
        assertNull(TelecomMuteSync().onTelecomMuteReported(false, start))
        assertNull(TelecomMuteSync().onTelecomMuteReported(true, start))
    }

    @Test
    fun `later changes are applied`() {
        val sync = TelecomMuteSync()
        sync.onTelecomMuteReported(false, start)

        assertEquals(true, sync.onTelecomMuteReported(true, start + 1))
        assertEquals(false, sync.onTelecomMuteReported(false, start + 2))
    }

    @Test
    fun `repeated state is not applied again`() {
        val sync = TelecomMuteSync()
        sync.onTelecomMuteReported(false, start)
        sync.onTelecomMuteReported(true, start + 1)

        assertNull(sync.onTelecomMuteReported(true, start + 2))
    }

    @Test
    fun `unmute echo of the app lifting the system mute is not applied`() {
        val sync = TelecomMuteSync()
        sync.onTelecomMuteReported(false, start)
        sync.onTelecomMuteReported(true, start + 1)

        sync.onSystemMicrophoneUnmutedByApp(start + 100)
        assertNull(sync.onTelecomMuteReported(false, start + 200))

        // later changes are applied again
        assertEquals(true, sync.onTelecomMuteReported(true, start + 300))
        assertEquals(false, sync.onTelecomMuteReported(false, start + 400))
    }

    @Test
    fun `unmute after the echo window is a real unmute`() {
        // Telecom doesn't echo the app lifting the mute and keeps reporting muted
        val sync = TelecomMuteSync()
        sync.onTelecomMuteReported(false, start)
        sync.onTelecomMuteReported(true, start + 1)
        sync.onSystemMicrophoneUnmutedByApp(start + 100)

        val headsetUnmuteAt = start + 100 + ECHO_WINDOW_MILLIS + 1
        assertEquals(false, sync.onTelecomMuteReported(false, headsetUnmuteAt))
    }

    @Test
    fun `repeated muted report inside the echo window keeps waiting for the echo`() {
        val sync = TelecomMuteSync()
        sync.onTelecomMuteReported(false, start)
        sync.onTelecomMuteReported(true, start + 1)
        sync.onSystemMicrophoneUnmutedByApp(start + 100)

        assertNull(sync.onTelecomMuteReported(true, start + 150))
        assertNull(sync.onTelecomMuteReported(false, start + 200))
    }

    @Test
    fun `end of the Telecom call resets the baseline`() {
        val sync = TelecomMuteSync()
        sync.onTelecomMuteReported(false, start)
        sync.onTelecomMuteReported(null, start + 1)

        assertNull(sync.onTelecomMuteReported(true, start + 2))
    }
}
