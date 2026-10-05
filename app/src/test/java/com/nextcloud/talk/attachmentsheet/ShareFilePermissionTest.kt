/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareFilePermissionTest {

    private val granted = PackageManager.PERMISSION_GRANTED
    private val denied = PackageManager.PERMISSION_DENIED

    @Test
    fun requestIsTheSheetSetPlusAudioFromAndroid13() {
        val audio = Manifest.permission.READ_MEDIA_AUDIO
        listOf(Build.VERSION_CODES.TIRAMISU, Build.VERSION_CODES.UPSIDE_DOWN_CAKE).forEach {
            assertEquals(mediaPermissionsToRequest(it).toSet() + audio, shareFilePermissionsToRequest(it).toSet())
        }
        assertEquals(
            setOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            shareFilePermissionsToRequest(Build.VERSION_CODES.Q).toSet()
        )
    }

    @Test
    fun anyGrantedPermissionAllowsThePicker() {
        // Android 14, "selected photos": the first result is denied, a later one is granted
        assertTrue(isShareFileRequestGranted(intArrayOf(denied, denied, granted), effectivelyGranted = false))
        // Android 8-10: READ is granted, the effective check looks at WRITE
        assertTrue(isShareFileRequestGranted(intArrayOf(granted), effectivelyGranted = false))
    }

    @Test
    fun effectiveAccessAllowsThePickerEvenWithoutGrantedResult() {
        assertTrue(isShareFileRequestGranted(intArrayOf(denied), effectivelyGranted = true))
        assertTrue(isShareFileRequestGranted(intArrayOf(), effectivelyGranted = true))
    }

    @Test
    fun nothingGrantedIsRefused() {
        assertFalse(isShareFileRequestGranted(intArrayOf(denied, denied), effectivelyGranted = false))
        assertFalse(isShareFileRequestGranted(intArrayOf(), effectivelyGranted = false))
    }
}
