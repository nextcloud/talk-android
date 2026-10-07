/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawingGeometryTest {

    @Test
    fun fitRectLetterboxesWideImageInTallView() {
        val rect = fitRect(1000f, 2000f, 2f)
        assertEquals(FitRect(0f, 750f, 1000f, 500f), rect)
    }

    @Test
    fun fitRectPillarboxesTallImageInWideView() {
        val rect = fitRect(2000f, 1000f, 0.5f)
        assertEquals(FitRect(750f, 0f, 500f, 1000f), rect)
    }

    @Test
    fun toNormalizedMapsRectCornersAndClampsOutside() {
        val rect = FitRect(0f, 750f, 1000f, 500f)
        assertEquals(NormalizedPoint(0f, 0f), toNormalized(0f, 750f, rect))
        assertEquals(NormalizedPoint(1f, 1f), toNormalized(1000f, 1250f, rect))
        assertEquals(NormalizedPoint(0.5f, 0.5f), toNormalized(500f, 1000f, rect))
        assertEquals(NormalizedPoint(0f, 1f), toNormalized(-50f, 5000f, rect))
    }

    @Test
    fun viewTouchLandsOnSamePixelRegardlessOfViewSize() {
        // A touch at the same relative spot of the picture must give the same pixel on two screens.
        val small = toNormalized(150f, 100f, fitRect(300f, 200f, 1.5f))
        val large = toNormalized(450f, 300f, fitRect(900f, 600f, 1.5f))
        assertEquals(small, large)
        assertEquals(1224f to 1632f, toPixels(NormalizedPoint(0.5f, 0.5f), 2448, 3264))
    }

    @Test
    fun strokeWidthScalesWithBitmapAndHasOnePixelFloor() {
        val stroke = DrawStroke(0, 0.01f, emptyList())
        assertEquals(24.48f, strokeWidthPixels(stroke, 2448), 0.001f)
        assertEquals(1f, strokeWidthPixels(stroke, 10), 0.001f)
    }

    @Test
    fun undoLastDropsOnlyNewestStroke() {
        val first = DrawStroke(1, 0.01f, listOf(NormalizedPoint(0f, 0f)))
        val second = DrawStroke(2, 0.01f, listOf(NormalizedPoint(1f, 1f)))
        assertEquals(listOf(first), undoLast(listOf(first, second)))
        assertEquals(emptyList<DrawStroke>(), undoLast(emptyList()))
    }

    @Test
    fun decodeSampleSizeKeepsFullResolutionBelowLimit() {
        assertEquals(1, decodeSampleSize(4000, 3000))
        assertEquals(1, decodeSampleSize(5000, 4000))
    }

    @Test
    fun decodeSampleSizeDownsamplesHugeImagesByPowersOfTwo() {
        // 48 MP -> 12 MP, 108 MP -> 6.75 MP, 400 MP -> 6.25 MP
        assertEquals(2, decodeSampleSize(8000, 6000))
        assertEquals(4, decodeSampleSize(12000, 9000))
        assertEquals(8, decodeSampleSize(20000, 20000))
    }

    @Test
    fun drawingSessionKeepsStrokesAndUndoesThem() {
        val session = DrawingSession()
        val first = DrawStroke(1, 0.01f, listOf(NormalizedPoint(0f, 0f)))
        val second = DrawStroke(2, 0.01f, listOf(NormalizedPoint(1f, 1f)))
        session.add(first)
        session.add(second)
        session.undo()
        assertEquals(listOf(first), session.strokes)
        session.clear()
        assertEquals(emptyList<DrawStroke>(), session.strokes)
    }
}
