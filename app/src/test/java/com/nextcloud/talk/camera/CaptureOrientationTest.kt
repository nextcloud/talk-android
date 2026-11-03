/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.camera

import android.view.OrientationEventListener.ORIENTATION_UNKNOWN
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureOrientationTest {

    @Test
    fun sensorAngleMapsToTargetRotationAtTheBoundaries() {
        assertEquals(Surface.ROTATION_0, rotationForDeviceOrientation(0))
        assertEquals(Surface.ROTATION_0, rotationForDeviceOrientation(44))
        assertEquals(Surface.ROTATION_270, rotationForDeviceOrientation(45))
        assertEquals(Surface.ROTATION_0, rotationForDeviceOrientation(315))
        assertEquals(Surface.ROTATION_90, rotationForDeviceOrientation(314))
        assertEquals(Surface.ROTATION_180, rotationForDeviceOrientation(180))
        assertNull(rotationForDeviceOrientation(-1))
        assertNull(rotationForDeviceOrientation(ORIENTATION_UNKNOWN))
    }

    @Test
    fun iconStaysUprightForTheHoldThatMatchesTheWindow() {
        // Window in the natural orientation, phone held upright.
        assertEquals(0, iconRotationDegrees(0, Surface.ROTATION_0))
        // Window turned counter-clockwise (ROTATION_90) and the phone really held that way (sensor 270).
        assertEquals(0, iconRotationDegrees(270, Surface.ROTATION_90))
        assertEquals(0, iconRotationDegrees(90, Surface.ROTATION_270))
        assertEquals(0, iconRotationDegrees(180, Surface.ROTATION_180))
    }

    @Test
    fun iconCompensatesAFixedWindow() {
        // Phone turned clockwise by 90 in a window that stayed natural: the icon turns back by 90.
        assertEquals(270, iconRotationDegrees(90, Surface.ROTATION_0))
        assertEquals(180, iconRotationDegrees(180, Surface.ROTATION_0))
        assertEquals(90, iconRotationDegrees(270, Surface.ROTATION_0))
    }

    @Test
    fun iconCompensatesAWindowTurnedAgainstTheHold() {
        // Phone upright but the window turned (large screen ignoring the orientation lock).
        assertEquals(270, iconRotationDegrees(0, Surface.ROTATION_90))
        assertEquals(180, iconRotationDegrees(0, Surface.ROTATION_180))
        assertEquals(90, iconRotationDegrees(0, Surface.ROTATION_270))
    }

    @Test
    fun iconAngleIsUnknownWithoutSensorData() {
        assertNull(iconRotationDegrees(ORIENTATION_UNKNOWN, Surface.ROTATION_0))
    }

    @Test
    fun animationTakesTheShortestWay() {
        assertEquals(-90f, shortestRotationTarget(0f, 270), 0f)
        assertEquals(90f, shortestRotationTarget(0f, 90), 0f)
        assertEquals(-90f, shortestRotationTarget(-90f, 270), 0f)
        assertEquals(-180f, shortestRotationTarget(-90f, 180), 0f)
        assertEquals(360f, shortestRotationTarget(270f, 0), 0f)
        assertEquals(0f, shortestRotationTarget(0f, 0), 0f)
        assertEquals(-180f, shortestRotationTarget(0f, 180), 0f)
    }

    @Test
    fun shutterStaysAtTheNaturalBottomEdgeWhateverTheWindowRotation() {
        val shutter = BodyPoint(0, 1)
        assertEquals(BodyPoint(0, 1), shutter.inWindow(Surface.ROTATION_0))
        assertEquals(BodyPoint(1, 0), shutter.inWindow(Surface.ROTATION_90))
        assertEquals(BodyPoint(0, -1), shutter.inWindow(Surface.ROTATION_180))
        assertEquals(BodyPoint(-1, 0), shutter.inWindow(Surface.ROTATION_270))
    }

    @Test
    fun closeAndFlashStayAtTheNaturalTopEdge() {
        val close = BodyPoint(-1, -1)
        val flash = BodyPoint(1, -1)
        assertEquals(BodyPoint(-1, 1), close.inWindow(Surface.ROTATION_90))
        assertEquals(BodyPoint(-1, -1), flash.inWindow(Surface.ROTATION_90))
        assertEquals(BodyPoint(1, -1), close.inWindow(Surface.ROTATION_270))
        assertEquals(BodyPoint(1, 1), flash.inWindow(Surface.ROTATION_270))
        assertEquals(BodyPoint(1, 1), close.inWindow(Surface.ROTATION_180))
    }

    @Test
    fun lensSwitchDirectionFollowsTheBody() {
        val right = BodyPoint(1, 0)
        assertEquals(BodyPoint(1, 0), right.inWindow(Surface.ROTATION_0))
        assertEquals(BodyPoint(0, -1), right.inWindow(Surface.ROTATION_90))
        assertEquals(BodyPoint(-1, 0), right.inWindow(Surface.ROTATION_180))
        assertEquals(BodyPoint(0, 1), right.inWindow(Surface.ROTATION_270))
    }
}
