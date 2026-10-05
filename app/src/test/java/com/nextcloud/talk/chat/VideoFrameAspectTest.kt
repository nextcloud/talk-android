/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Krainov Gleb <krajnov.g@kontentplus.ru>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoFrameAspectTest {

    private val portrait = 9f / 16f
    private val landscape = 16f / 9f

    @Test
    fun frameAspectFromTheCameraSurface() {
        // a sensor delivers 1280x720; turned a quarter it is a portrait frame
        assertEquals(720f / 1280f, videoFrameAspect(1280, 720, 90)!!, 0.0001f)
        assertEquals(720f / 1280f, videoFrameAspect(1280, 720, 270)!!, 0.0001f)
        assertEquals(1280f / 720f, videoFrameAspect(1280, 720, 0)!!, 0.0001f)
        assertEquals(1280f / 720f, videoFrameAspect(1280, 720, 180)!!, 0.0001f)
    }

    @Test
    fun frameAspectIsUnknownWithoutASize() {
        assertNull(videoFrameAspect(0, 720, 90))
        assertNull(videoFrameAspect(1280, 0, 0))
    }

    @Test
    fun fallbackAspectFollowsTheRecordingRotation() {
        assertEquals(portrait, fallbackVideoFrameAspect(Surface.ROTATION_0), 0.0001f)
        assertEquals(portrait, fallbackVideoFrameAspect(Surface.ROTATION_180), 0.0001f)
        assertEquals(landscape, fallbackVideoFrameAspect(Surface.ROTATION_90), 0.0001f)
        assertEquals(landscape, fallbackVideoFrameAspect(Surface.ROTATION_270), 0.0001f)
    }
}
