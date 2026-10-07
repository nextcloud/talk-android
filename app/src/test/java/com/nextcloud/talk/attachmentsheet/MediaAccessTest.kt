/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAccessTest {

    private val images = Manifest.permission.READ_MEDIA_IMAGES
    private val video = Manifest.permission.READ_MEDIA_VIDEO
    private val storage = Manifest.permission.READ_EXTERNAL_STORAGE
    private val selected = READ_MEDIA_VISUAL_USER_SELECTED

    @Test
    fun android14WithAllMediaIsFull() {
        assertEquals(
            MediaAccess.FULL,
            resolveMediaAccess(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, setOf(images, video, selected))
        )
    }

    @Test
    fun android14WithOnlyUserSelectedIsPartial() {
        assertEquals(
            MediaAccess.PARTIAL,
            resolveMediaAccess(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, setOf(selected))
        )
    }

    @Test
    fun android14WithNothingIsNone() {
        assertEquals(MediaAccess.NONE, resolveMediaAccess(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, emptySet()))
    }

    @Test
    fun android13WithOneMediaTypeIsFull() {
        assertEquals(MediaAccess.FULL, resolveMediaAccess(Build.VERSION_CODES.TIRAMISU, setOf(video)))
    }

    @Test
    fun android13IgnoresUserSelectedPermission() {
        assertEquals(MediaAccess.NONE, resolveMediaAccess(Build.VERSION_CODES.TIRAMISU, setOf(selected)))
    }

    @Test
    fun oldAndroidUsesStoragePermission() {
        assertEquals(MediaAccess.FULL, resolveMediaAccess(Build.VERSION_CODES.S, setOf(storage)))
        assertEquals(MediaAccess.NONE, resolveMediaAccess(Build.VERSION_CODES.S, setOf(images)))
    }

    @Test
    fun android14WithOneMediaTypeIsFull() {
        assertEquals(MediaAccess.FULL, resolveMediaAccess(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, setOf(images)))
    }

    @Test
    fun grantingEveryRequestedPermissionGivesFullAccess() {
        listOf(Build.VERSION_CODES.S, Build.VERSION_CODES.TIRAMISU, Build.VERSION_CODES.UPSIDE_DOWN_CAKE).forEach {
            val requested = mediaPermissionsToRequest(it).toSet()
            assertEquals("sdk $it", MediaAccess.FULL, resolveMediaAccess(it, requested))
        }
    }

    @Test
    fun grantingNothingGivesNoAccessOnEveryVersion() {
        listOf(Build.VERSION_CODES.S, Build.VERSION_CODES.TIRAMISU, Build.VERSION_CODES.UPSIDE_DOWN_CAKE).forEach {
            assertEquals("sdk $it", MediaAccess.NONE, resolveMediaAccess(it, emptySet()))
        }
    }

    @Test
    fun filesAccessCountsPartialGrantOnAndroid14() {
        assertTrue(hasMediaFilesAccess(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, setOf(selected)))
    }

    @Test
    fun filesAccessIgnoresPartialGrantBeforeAndroid14() {
        assertFalse(hasMediaFilesAccess(Build.VERSION_CODES.TIRAMISU, setOf(selected)))
    }

    @Test
    fun filesAccessCountsAudioOnAndroid13() {
        assertTrue(hasMediaFilesAccess(Build.VERSION_CODES.TIRAMISU, setOf(Manifest.permission.READ_MEDIA_AUDIO)))
    }

    @Test
    fun filesAccessIsFalseWithoutAnyMediaPermission() {
        assertFalse(hasMediaFilesAccess(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, emptySet()))
    }

    @Test
    fun partialGrantOnAndroid14IsAccessEvenThoughTheFirstRequestedPermissionIsDenied() {
        // User-selected photos are asked for from Android 14 on, not before.
        val sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        val requested = mediaPermissionsToRequest(sdk)
        assertEquals(setOf(images, video, selected), requested.toSet())
        assertEquals(setOf(images, video), mediaPermissionsToRequest(Build.VERSION_CODES.TIRAMISU).toSet())

        // The result handler must not look at grantResults[0] only: with "selected photos" the first permission of
        // the request (READ_MEDIA_IMAGES) is denied and only READ_MEDIA_VISUAL_USER_SELECTED is granted.
        val granted = setOf(selected)
        assertFalse(requested.first() in granted)
        assertTrue(hasMediaFilesAccess(sdk, granted))
    }
}
