/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.camera

import androidx.camera.core.ImageCapture
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureModeSettingTest {

    @Test
    fun captureModeDefaultsToFast() {
        assertEquals(CaptureModeSetting.FAST, CaptureModeSetting.DEFAULT)
        assertEquals(CaptureModeSetting.FAST, CaptureModeSetting.fromStorage(null))
        assertEquals(CaptureModeSetting.FAST, CaptureModeSetting.fromStorage(""))
    }

    @Test
    fun captureModeMapsToImageCaptureMode() {
        assertEquals(
            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY,
            CaptureModeSetting.fromStorage("fast").imageCaptureMode
        )
        assertEquals(
            ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY,
            CaptureModeSetting.fromStorage("quality").imageCaptureMode
        )
    }

    @Test
    fun captureModeUnknownStoredValueGivesDefault() {
        assertEquals(CaptureModeSetting.DEFAULT, CaptureModeSetting.fromStorage("maximum"))
        assertEquals(CaptureModeSetting.DEFAULT, CaptureModeSetting.fromStorage("QUALITY"))
    }

    @Test
    fun captureModeTogglesThereAndBack() {
        assertEquals(CaptureModeSetting.QUALITY, CaptureModeSetting.FAST.toggled())
        assertEquals(CaptureModeSetting.FAST, CaptureModeSetting.QUALITY.toggled())
        CaptureModeSetting.entries.forEach {
            assertEquals(it, CaptureModeSetting.fromStorage(it.storageValue))
            assertEquals(it, it.toggled().toggled())
        }
    }
}
