/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Krainov Gleb <krajnov.g@kontentplus.ru>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The values are dp: the function does not care for the unit. */
class VideoPreviewPlacementTest {

    private val portrait = 9f / 16f
    private val landscape = 16f / 9f
    private val limits = VideoPreviewLimits(margin = 16, maxWidthFraction = 0.75f, maxSide = 480)

    private fun place(areaWidth: Int, areaHeight: Int, aspect: Float, limits: VideoPreviewLimits = this.limits) =
        videoPreviewPlacement(areaWidth, areaHeight, aspect, limits)

    private fun assertInside(p: VideoPreviewPlacement, areaWidth: Int, areaHeight: Int) {
        assertTrue("left ${p.left}", p.left >= 0)
        assertTrue("top ${p.top}", p.top >= 0)
        assertTrue("right", p.left + p.width <= areaWidth)
        assertTrue("bottom", p.top + p.height <= areaHeight)
    }

    private fun assertAspect(p: VideoPreviewPlacement, aspect: Float) {
        assertEquals(aspect.toDouble(), p.width.toDouble() / p.height, 0.02)
    }

    @Test
    fun portraitOnOwnerPhoneTakesThreeQuartersOfTheWidthAndKeepsTheAspect() {
        val p = place(348, 560, portrait)
        assertEquals(261, p.width)
        assertEquals(464, p.height)
        assertAspect(p, portrait)
        assertInside(p, 348, 560)
        // centered: the gap on both sides is the same, within a pixel
        assertTrue(Math.abs(p.left - (348 - p.left - p.width)) <= 1)
        assertTrue(Math.abs(p.top - (560 - p.top - p.height)) <= 1)
    }

    @Test
    fun portraitOnAShortAreaIsFittedByTheHeightWithMargins() {
        val p = place(348, 400, portrait)
        // the height minus the margins: 400 - 2 * 16
        assertEquals(368, p.height)
        assertEquals(207, p.width)
        assertAspect(p, portrait)
        assertTrue(p.top >= 16)
    }

    @Test
    fun landscapeInALandscapeAreaIsFittedByTheHeightAndNotSquashed() {
        val p = place(700, 200, landscape)
        assertEquals(168, p.height)
        assertEquals(299, p.width)
        assertAspect(p, landscape)
        assertTrue(p.top >= 16)
        assertTrue(p.left >= 16)
        assertInside(p, 700, 200)
    }

    @Test
    fun landscapeFrameInAPortraitAreaIsFittedByTheWidth() {
        val p = place(348, 560, landscape)
        assertEquals(261, p.width)
        assertEquals(147, p.height)
        assertAspect(p, landscape)
        assertEquals((560 - 147) / 2, p.top)
    }

    @Test
    fun narrowPanelOfTwoPanesKeepsTheMarginAndTheAspect() {
        val p = place(240, 520, portrait)
        // 75 % of 240; 240 - 2 * 16 = 208 would be wider
        assertEquals(180, p.width)
        assertEquals(320, p.height)
        assertAspect(p, portrait)
        assertTrue(p.left >= 16)
        assertInside(p, 240, 520)
    }

    @Test
    fun narrowPanelWithALandscapeFrameIsLimitedByTheMarginedWidth() {
        val p = place(200, 520, landscape, limits.copy(maxWidthFraction = 0.95f))
        // 200 - 2 * 16 = 168 is less than 95 % of 200
        assertEquals(168, p.width)
        assertAspect(p, landscape)
        assertTrue(p.left >= 16)
    }

    @Test
    fun veryLowAreaStillGivesAPositivePreviewInsideTheArea() {
        val p = place(348, 40, portrait)
        assertTrue(p.height in 1..40)
        assertTrue(p.width > 0)
        assertAspect(p, portrait)
        assertInside(p, 348, 40)
    }

    @Test
    fun veryLowAreaWithALandscapeFrameStaysInside() {
        val p = place(700, 24, landscape)
        assertTrue(p.height > 0)
        assertAspect(p, landscape)
        assertInside(p, 700, 24)
    }

    @Test
    fun emptyAreaGivesAnEmptyPreview() {
        val p = place(0, 0, portrait)
        assertEquals(VideoPreviewPlacement(0, 0, 0, 0), p)
        assertEquals(0, place(-5, 100, portrait).width)
    }

    @Test
    fun hugeAreaIsCappedByTheLongestSide() {
        val p = place(1200, 1000, portrait)
        assertEquals(480, p.height)
        assertEquals(270, p.width)
        assertAspect(p, portrait)
        assertInside(p, 1200, 1000)
        val l = place(2000, 1000, landscape)
        assertEquals(480, l.width)
        assertEquals(270, l.height)
    }

    @Test
    fun invalidAspectFallsBackToPortrait() {
        assertEquals(place(348, 560, portrait), place(348, 560, Float.NaN))
        assertEquals(place(348, 560, portrait), place(348, 560, 0f))
        assertEquals(place(348, 560, portrait), place(348, 560, -1f))
    }
}
