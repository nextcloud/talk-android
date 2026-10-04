/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.camera

import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.nextcloud.talk.chat.oppositeLens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Date

class PhotoCaptureLogicTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun flashCyclesOffAutoOnAndBack() {
        assertEquals(FlashSetting.AUTO, FlashSetting.OFF.next())
        assertEquals(FlashSetting.ON, FlashSetting.AUTO.next())
        assertEquals(FlashSetting.OFF, FlashSetting.ON.next())
    }

    @Test
    fun flashSettingMapsToImageCaptureMode() {
        assertEquals(ImageCapture.FLASH_MODE_OFF, effectiveFlashMode(FlashSetting.OFF, true))
        assertEquals(ImageCapture.FLASH_MODE_AUTO, effectiveFlashMode(FlashSetting.AUTO, true))
        assertEquals(ImageCapture.FLASH_MODE_ON, effectiveFlashMode(FlashSetting.ON, true))
    }

    @Test
    fun cameraWithoutFlashUnitNeverFires() {
        FlashSetting.entries.forEach {
            assertEquals(ImageCapture.FLASH_MODE_OFF, effectiveFlashMode(it, false))
        }
    }

    @Test
    fun lensSwitchesBetweenFrontAndBack() {
        assertEquals(CameraSelector.LENS_FACING_BACK, oppositeLens(CameraSelector.LENS_FACING_FRONT))
        assertEquals(CameraSelector.LENS_FACING_FRONT, oppositeLens(CameraSelector.LENS_FACING_BACK))
    }

    @Test
    fun deviceOrientationMapsToSurfaceRotation() {
        assertEquals(Surface.ROTATION_0, rotationForDeviceOrientation(0))
        assertEquals(Surface.ROTATION_0, rotationForDeviceOrientation(44))
        assertEquals(Surface.ROTATION_0, rotationForDeviceOrientation(315))
        assertEquals(Surface.ROTATION_0, rotationForDeviceOrientation(359))
        assertEquals(Surface.ROTATION_270, rotationForDeviceOrientation(45))
        assertEquals(Surface.ROTATION_270, rotationForDeviceOrientation(90))
        assertEquals(Surface.ROTATION_180, rotationForDeviceOrientation(180))
        assertEquals(Surface.ROTATION_90, rotationForDeviceOrientation(270))
        assertEquals(Surface.ROTATION_90, rotationForDeviceOrientation(314))
    }

    @Test
    fun unknownDeviceOrientationKeepsRotation() {
        assertNull(rotationForDeviceOrientation(ORIENTATION_UNKNOWN))
    }

    @Test
    fun photoFileLandsInSharedAttachmentsDirectory() {
        val file = createPhotoFile(folder.root, "Picture from 2026-10-04 12-00-00")

        assertNotNull(file)
        assertEquals("Picture from 2026-10-04 12-00-00.jpg", file!!.name)
        assertEquals("shared_attachments", file.parentFile!!.name)
        assertTrue(file.parentFile!!.isDirectory)
    }

    @Test
    fun existingPhotoFileIsNotReused() {
        val first = createPhotoFile(folder.root, "photo")!!
        first.writeText("data")

        val second = createPhotoFile(folder.root, "photo")!!

        assertNotEquals(first, second)
        assertEquals("photo (1).jpg", second.name)
    }

    @Test
    fun unsafePhotoNameIsRefused() {
        assertNull(createPhotoFile(folder.root, "../escape"))
    }

    @Test
    fun timestampHasFileSafeFormat() {
        val text = formatCaptureTimestamp(Date(0))

        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}-\\d{2}-\\d{2}").matches(text))
    }

    @Test
    fun photoSelectorAsksForHighestResolutionAtFourToThree() {
        val selector = photoResolutionSelector()

        assertSame(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY, selector.aspectRatioStrategy)
        assertSame(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY, selector.resolutionStrategy)
    }

    @Test
    fun captureResolutionIsDescribedInDisplayOrientation() {
        assertEquals("4000x3000", describeCaptureResolution(4000, 3000, 0))
        assertEquals("4000x3000", describeCaptureResolution(4000, 3000, 180))
        assertEquals("3000x4000", describeCaptureResolution(4000, 3000, 90))
        assertEquals("3000x4000", describeCaptureResolution(4000, 3000, 270))
    }

    private companion object {
        const val ORIENTATION_UNKNOWN = -1
    }
}
