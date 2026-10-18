/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.io

import androidx.media3.session.MediaSessionService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Media3 keeps a media service in the foreground, with a notification that cannot be swiped away, for
 * [MediaSessionService.DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS] (10 minutes) after the player is paused or ends. The
 * voice message service must leave the foreground at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceMessageMediaServiceForegroundTest {
    /** The timeout Media3 applies to the notification manager of [service], read by reflection (no getter). */
    private fun foregroundTimeoutMs(service: VoiceMessageMediaService): Long {
        val getManager = MediaSessionService::class.java.getDeclaredMethod("getMediaNotificationManager")
        getManager.isAccessible = true
        val manager = getManager.invoke(service)
        val field = manager.javaClass.getDeclaredField("userEngagedTimeoutMs")
        field.isAccessible = true
        return field.getLong(manager)
    }

    @Test
    fun `service does not stay in the foreground after the voice message is paused or ends`() {
        val service = Robolectric.buildService(VoiceMessageMediaService::class.java).create().get()

        assertEquals(0L, foregroundTimeoutMs(service))
        service.onDestroy()
    }
}
