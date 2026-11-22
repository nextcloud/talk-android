/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.activities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackPolicyTest {
    @Test
    fun `normal video retains aspect ratio`() {
        assertEquals(16f / 9, VideoPlaybackPolicy.pictureInPictureRatio(1920, 1080, 1f), 0.001f)
        assertEquals(9f / 16, VideoPlaybackPolicy.pictureInPictureRatio(1080, 1920, 1f), 0.001f)
    }

    @Test
    fun `extreme dimensions and pixel shapes stay within Android PiP limits after rounding`() {
        for ((width, height, pixel) in listOf(Triple(1, 10000, 1f), Triple(10000, 1, 1f), Triple(1920, 1080, 2f))) {
            val ratio = VideoPlaybackPolicy.pictureInPictureRatio(width, height, pixel)
            val rounded = (ratio * 1000).toInt() / 1000f
            assertTrue(rounded >= 1f / 2.39f && rounded <= 2.39f)
        }
    }

    @Test
    fun `unknown and invalid video dimensions use safe default`() {
        for ((width, height, pixel) in listOf(Triple(0, 0, 1f), Triple(10, 0, 1f), Triple(10, 10, Float.NaN))) {
            assertEquals(16f / 9, VideoPlaybackPolicy.pictureInPictureRatio(width, height, pixel), 0.001f)
        }
    }
}
