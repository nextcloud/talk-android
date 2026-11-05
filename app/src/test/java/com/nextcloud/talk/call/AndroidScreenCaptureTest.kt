/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidScreenCaptureTest {

    @Test
    fun testSmallDisplayIsNotScaled() {
        assertEquals(Pair(1080, 1920), AndroidScreenCapture.scaleCaptureSize(1080, 1920))
        assertEquals(Pair(720, 1280), AndroidScreenCapture.scaleCaptureSize(720, 1280))
    }

    @Test
    fun testLargeDisplayIsScaledInBothRotations() {
        assertEquals(Pair(1920, 864), AndroidScreenCapture.scaleCaptureSize(3840, 1728))
        assertEquals(Pair(864, 1920), AndroidScreenCapture.scaleCaptureSize(1728, 3840))
    }

    @Test
    fun testDimensionsAreEven() {
        assertEquals(Pair(1920, 886), AndroidScreenCapture.scaleCaptureSize(2400, 1108))
        assertEquals(Pair(720, 1280), AndroidScreenCapture.scaleCaptureSize(721, 1281))
    }
}
