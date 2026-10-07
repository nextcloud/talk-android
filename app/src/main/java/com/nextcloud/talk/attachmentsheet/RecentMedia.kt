/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import java.util.Locale

/**
 * One photo or video of the device library. [id] is the MediaStore row id within its own table (images and videos).
 */
data class RecentMedia(val id: Long, val isVideo: Boolean, val dateAddedSeconds: Long, val durationMs: Long = 0L) {

    /**
     * Images and videos have separate id spaces, so the key combines both.
     */
    val key: Long
        get() = if (isVideo) -id - 1 else id
}

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val MILLIS_PER_SECOND = 1000

/**
 * Newest first, at most [limit] entries. Equal dates keep images before videos so the order is stable.
 */
fun mergeRecentMedia(images: List<RecentMedia>, videos: List<RecentMedia>, limit: Int): List<RecentMedia> =
    (images + videos).sortedByDescending { it.dateAddedSeconds }.take(limit)

/**
 * "m:ss" below an hour, "h:mm:ss" from an hour on.
 */
fun formatVideoDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / MILLIS_PER_SECOND).toInt()
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
