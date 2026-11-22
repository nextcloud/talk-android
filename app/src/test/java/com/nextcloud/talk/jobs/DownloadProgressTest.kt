/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressTest {
    @Test
    fun `percentage uses bytes and reserves completion for a published file`() {
        assertEquals(0, DownloadProgress.percent(0, 1000))
        assertEquals(37, DownloadProgress.percent(375, 1000))
        assertEquals(99, DownloadProgress.percent(1000, 1000))
        assertEquals(99, DownloadProgress.percent(1100, 1000))
    }

    @Test
    fun `unknown length stays indeterminate and large sizes do not overflow`() {
        assertEquals(-1, DownloadProgress.percent(100, -1))
        assertEquals(-1, DownloadProgress.percent(100, 0))
        assertEquals(50, DownloadProgress.percent(Long.MAX_VALUE / 2, Long.MAX_VALUE))
    }
}
