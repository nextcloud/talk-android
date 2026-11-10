/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecentMediaTest {

    private fun image(id: Long, date: Long) = RecentMedia(id, isVideo = false, dateAddedSeconds = date)

    private fun video(id: Long, date: Long) =
        RecentMedia(id, isVideo = true, dateAddedSeconds = date, durationMs = 1000)

    @Test
    fun mergeOrdersNewestFirstAcrossTypes() {
        val merged = mergeRecentMedia(listOf(image(1, 50), image(2, 10)), listOf(video(1, 30)), limit = 10)
        assertEquals(listOf(50L, 30L, 10L), merged.map { it.dateAddedSeconds })
    }

    @Test
    fun mergeCutsToLimit() {
        val merged = mergeRecentMedia(listOf(image(1, 5), image(2, 4)), listOf(video(1, 6), video(2, 3)), limit = 3)
        assertEquals(listOf(6L, 5L, 4L), merged.map { it.dateAddedSeconds })
    }

    @Test
    fun equalDatesKeepImagesBeforeVideos() {
        val merged = mergeRecentMedia(listOf(image(1, 5)), listOf(video(2, 5)), limit = 10)
        assertEquals(listOf(false, true), merged.map { it.isVideo })
    }

    @Test
    fun mergeOfNothingIsEmpty() {
        assertEquals(emptyList<RecentMedia>(), mergeRecentMedia(emptyList(), emptyList(), limit = 5))
    }

    @Test
    fun imageAndVideoWithSameRowIdHaveDifferentKeys() {
        assertNotEquals(image(4, 1).key, video(4, 1).key)
    }

    @Test
    fun durationBelowHourShowsMinutesAndSeconds() {
        assertEquals("0:00", formatVideoDuration(0))
        assertEquals("0:07", formatVideoDuration(7_400))
        assertEquals("2:05", formatVideoDuration(125_000))
        assertEquals("59:59", formatVideoDuration(3_599_999))
    }

    @Test
    fun durationFromHourShowsHours() {
        assertEquals("1:00:00", formatVideoDuration(3_600_000))
        assertEquals("2:03:04", formatVideoDuration(7_384_000))
    }

    @Test
    fun negativeDurationIsZero() {
        assertEquals("0:00", formatVideoDuration(-5))
    }
}
