/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import android.app.Application
import android.graphics.Matrix
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drawing happens in upright coordinates but onto the raw, un-rotated pixels, so the inverse of
 * [uprightMatrix] must send an upright point to the raw pixel the EXIF orientation says it shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class UprightMatrixTest {

    private val rawWidth = 40
    private val rawHeight = 20

    /** (rotationDegrees, flipped) as androidx ExifInterface reports them, and upright->raw per EXIF spec. */
    private data class Orientation(
        val exifValue: Int,
        val degrees: Int,
        val flipped: Boolean,
        val uprightWidth: Int,
        val uprightToRaw: (Float, Float) -> Pair<Float, Float>
    )

    private val orientations = listOf(
        Orientation(1, 0, false, 40) { x, y -> x to y },
        Orientation(2, 0, true, 40) { x, y -> (40f - x) to y },
        Orientation(3, 180, false, 40) { x, y -> (40f - x) to (20f - y) },
        Orientation(4, 180, true, 40) { x, y -> x to (20f - y) },
        Orientation(5, 270, true, 20) { x, y -> y to x },
        Orientation(6, 90, false, 20) { x, y -> y to (20f - x) },
        Orientation(7, 90, true, 20) { x, y -> (40f - y) to (20f - x) },
        Orientation(8, 270, false, 20) { x, y -> (40f - y) to x }
    )

    private fun uprightToRaw(matrix: Matrix, x: Float, y: Float): Pair<Float, Float> {
        val inverse = Matrix().also { matrix.invert(it) }
        val point = floatArrayOf(x, y)
        inverse.mapPoints(point)
        return point[0] to point[1]
    }

    @Test
    fun uprightPointsLandOnTheExpectedRawPixelForAllEightOrientations() {
        orientations.forEach { o ->
            val matrix = uprightMatrix(o.degrees, o.flipped, rawWidth, rawHeight)
            val uprightHeight = if (o.uprightWidth == rawWidth) rawHeight else rawWidth
            listOf(
                0f to 0f,
                o.uprightWidth.toFloat() to uprightHeight.toFloat(),
                3f to 7f,
                o.uprightWidth / 2f to 1f
            ).forEach { (x, y) ->
                val (expectedX, expectedY) = o.uprightToRaw(x, y)
                val (actualX, actualY) = uprightToRaw(matrix, x, y)
                assertEquals("orientation ${o.exifValue} x for ($x,$y)", expectedX, actualX, 0.001f)
                assertEquals("orientation ${o.exifValue} y for ($x,$y)", expectedY, actualY, 0.001f)
            }
        }
    }

    @Test
    fun uprightSizeSwapsOnlyForQuarterTurns() {
        orientations.forEach { o ->
            val matrix = uprightMatrix(o.degrees, o.flipped, rawWidth, rawHeight)
            val bounds = android.graphics.RectF(0f, 0f, rawWidth.toFloat(), rawHeight.toFloat())
                .also { matrix.mapRect(it) }
            assertEquals("orientation ${o.exifValue}", o.uprightWidth.toFloat(), bounds.width(), 0.001f)
            assertEquals(0f, bounds.left, 0.001f)
            assertEquals(0f, bounds.top, 0.001f)
        }
    }
}
