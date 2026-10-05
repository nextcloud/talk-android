/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import android.Manifest
import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanSelectMoreMediaTest {

    private val images = Manifest.permission.READ_MEDIA_IMAGES
    private val video = Manifest.permission.READ_MEDIA_VIDEO
    private val storage = Manifest.permission.READ_EXTERNAL_STORAGE
    private val selected = READ_MEDIA_VISUAL_USER_SELECTED

    @Test
    fun selectMoreIsOfferedForPartialAccess() {
        assertTrue(canSelectMoreMedia(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, setOf(selected)))
    }

    @Test
    fun selectMoreIsOfferedForPhotosWithoutVideosOnAndroid14() {
        assertTrue(canSelectMoreMedia(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, setOf(images, selected)))
    }

    @Test
    fun selectMoreIsOfferedForVideosWithoutPhotosOnAndroid14() {
        assertTrue(canSelectMoreMedia(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, setOf(video, selected)))
    }

    @Test
    fun selectMoreIsHiddenForFullAccessOnAndroid14() {
        assertFalse(canSelectMoreMedia(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, setOf(images, video, selected)))
        assertFalse(canSelectMoreMedia(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, setOf(images, video)))
    }

    @Test
    fun selectMoreIsHiddenWithoutUserSelectedGrantOnAndroid14() {
        assertFalse(canSelectMoreMedia(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, setOf(images)))
        assertFalse(canSelectMoreMedia(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, emptySet()))
    }

    @Test
    fun selectMoreIsHiddenBeforeAndroid14() {
        assertFalse(canSelectMoreMedia(Build.VERSION_CODES.TIRAMISU, setOf(images, selected)))
        assertFalse(canSelectMoreMedia(Build.VERSION_CODES.TIRAMISU, setOf(selected)))
        assertFalse(canSelectMoreMedia(Build.VERSION_CODES.S, setOf(storage)))
    }
}
