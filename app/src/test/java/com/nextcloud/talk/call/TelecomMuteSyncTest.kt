/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelecomMuteSyncTest {

    @Test
    fun `first reported state is only the baseline`() {
        val sync = TelecomMuteSync()
        assertNull(sync.onTelecomMuteReported(false))
        assertNull(TelecomMuteSync().onTelecomMuteReported(true))
    }

    @Test
    fun `later changes are applied`() {
        val sync = TelecomMuteSync()
        sync.onTelecomMuteReported(false)

        assertEquals(true, sync.onTelecomMuteReported(true))
        assertEquals(false, sync.onTelecomMuteReported(false))
    }

    @Test
    fun `repeated state is not applied again`() {
        val sync = TelecomMuteSync()
        sync.onTelecomMuteReported(false)
        sync.onTelecomMuteReported(true)

        assertNull(sync.onTelecomMuteReported(true))
    }

    @Test
    fun `unmute echo of the app lifting the system mute is ignored once`() {
        val sync = TelecomMuteSync()
        sync.onTelecomMuteReported(false)
        sync.onTelecomMuteReported(true)

        sync.onSystemMicrophoneUnmutedByApp()
        assertNull(sync.onTelecomMuteReported(false))

        // later changes are applied again
        assertEquals(true, sync.onTelecomMuteReported(true))
        assertEquals(false, sync.onTelecomMuteReported(false))
    }

    @Test
    fun `end of the Telecom call resets the baseline`() {
        val sync = TelecomMuteSync()
        sync.onTelecomMuteReported(false)
        sync.onTelecomMuteReported(null)

        assertNull(sync.onTelecomMuteReported(true))
    }
}
