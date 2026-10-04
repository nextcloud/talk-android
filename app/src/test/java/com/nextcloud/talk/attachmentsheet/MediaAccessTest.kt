/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Krainov Gleb <krajnov.g@kontentplus.ru>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import android.Manifest
import android.os.Build
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    fun requestedPermissionsFollowTheVersion() {
        assertArrayEquals(
            arrayOf(images, video, selected),
            mediaPermissionsToRequest(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )
        assertArrayEquals(arrayOf(images, video), mediaPermissionsToRequest(Build.VERSION_CODES.TIRAMISU))
        assertArrayEquals(arrayOf(storage), mediaPermissionsToRequest(Build.VERSION_CODES.S))
    }
}
