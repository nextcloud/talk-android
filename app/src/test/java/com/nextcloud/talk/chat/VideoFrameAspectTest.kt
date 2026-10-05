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

    @Test
    fun screenAspectKeepsTheFrameWhenRotationsAgreeOrAreOpposite() {
        val r = listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)
        r.forEach { rotation ->
            assertEquals(portrait, screenFrameAspect(portrait, rotation, rotation), 0.0001f)
            assertEquals(landscape, screenFrameAspect(landscape, rotation, rotation), 0.0001f)
            val opposite = (rotation + 2) % 4
            assertEquals(portrait, screenFrameAspect(portrait, rotation, opposite), 0.0001f)
        }
    }

    @Test
    fun screenAspectTurnsTheFrameOnAQuarterTurnDifference() {
        // (video rotation, display rotation) pairs which differ by one or three quarters
        val pairs = listOf(
            Surface.ROTATION_90 to Surface.ROTATION_0, // auto-rotate off, phone held sideways
            Surface.ROTATION_270 to Surface.ROTATION_0,
            Surface.ROTATION_0 to Surface.ROTATION_90, // phone turned during the recording
            Surface.ROTATION_0 to Surface.ROTATION_270,
            Surface.ROTATION_180 to Surface.ROTATION_90,
            Surface.ROTATION_90 to Surface.ROTATION_180,
            Surface.ROTATION_270 to Surface.ROTATION_180,
            Surface.ROTATION_180 to Surface.ROTATION_270
        )
        pairs.forEach { (video, display) ->
            assertEquals("$video/$display", landscape, screenFrameAspect(portrait, video, display), 0.0001f)
            assertEquals("$video/$display", portrait, screenFrameAspect(landscape, video, display), 0.0001f)
        }
    }
}
