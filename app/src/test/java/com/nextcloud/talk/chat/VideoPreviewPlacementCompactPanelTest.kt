/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Krainov Gleb <krajnov.g@kontentplus.ru>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/** Preview size for the one-line video recording panel on the screens of the owner. The values are pixels. */
class VideoPreviewPlacementCompactPanelTest {

    private val portrait = 9f / 16f
    private val landscape = 16f / 9f
    private val limits = VideoPreviewLimits(margin = 16, maxWidthFraction = 0.75f, maxSide = 480)

    private fun place(areaWidth: Int, areaHeight: Int, aspect: Float, limits: VideoPreviewLimits = this.limits) =
        videoPreviewPlacement(areaWidth, areaHeight, aspect, limits)

    // Owner screenshots, density 3.1, so the values below are pixels. Limits as ChatActivity builds them: margin 16dp,
    // short area below 200dp keeps 8dp, longest side 480dp. The scrim begins at y = 321 px in both orientations.
    private val density = 3.1f
    private val phoneLimits = VideoPreviewLimits(
        margin = (16 * density).toInt(),
        maxWidthFraction = 0.75f,
        maxSide = (480 * density).toInt(),
        shortAreaHeight = (200 * density).toInt(),
        shortMargin = (8 * density).toInt()
    )

    @Test
    fun foldedHuaweiLandscapePreviewGrowsWithTheCompactPanel() {
        // 1080 px high screen: scrim 321 px from the top, old panel 411 px, compact panel 64 dp = 198 px
        val old = place(2444, 1080 - 321 - 411, landscape, phoneLimits.copy(shortAreaHeight = 0))
        assertEquals(VideoPreviewPlacement(width = 466, height = 262), old) // 150 x 84 dp
        val compact = place(2444, 1080 - 321 - 198, landscape, phoneLimits)
        assertEquals(VideoPreviewPlacement(width = 912, height = 513), compact) // 294 x 165 dp
        assertEquals(landscape.toDouble(), compact.width.toDouble() / compact.height, 0.02)
    }

    @Test
    fun portraitOnOwnerPhoneIsBoundByTheWidthSoTheCompactPanelDoesNotChangeIt() {
        // 348 dp = 1080 px wide, 2444 px high screen: the area grows from 1712 to 1925 px, the width stays the limit
        val old = place(1080, 2444 - 321 - 411, portrait, phoneLimits)
        val compact = place(1080, 2444 - 321 - 198, portrait, phoneLimits)
        assertEquals(VideoPreviewPlacement(width = 810, height = 1440), old) // 261 x 464 dp
        assertEquals(old, compact)
    }

    @Test
    fun shortAreaKeepsTheSmallerMarginAndATallAreaKeepsTheFullOne() {
        val tall = place(1000, 400, portrait, limits.copy(shortAreaHeight = 200, shortMargin = 8))
        assertEquals(368, tall.height) // 400 is not below 200: full margin 16
        val low = place(1000, 190, portrait, limits.copy(shortAreaHeight = 200, shortMargin = 8))
        assertEquals(174, low.height) // 190 - 2 * 8
    }
}
