/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class VideoRangeResponseTest {
    @Test
    fun `valid tail request has the exact remaining length`() {
        assertEquals(10L, VideoRangeResponse.validate("bytes 90-99/100", 90, 100))
    }

    @Test
    fun `changed file or wrong position cannot be combined with downloaded bytes`() {
        for (header in listOf("bytes 0-99/100", "bytes 90-100/101", "bytes 90-98/100", "bytes 90-99/*", null)) {
            assertThrows(IOException::class.java) { VideoRangeResponse.validate(header, 90, 100) }
        }
    }
}
