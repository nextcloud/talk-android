/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.stream

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import okhttp3.ResponseBody
import retrofit2.Call

class VideoStreamSource(
    private val download: ProgressiveVideoFile,
    private val requestRange: (Long, String) -> Call<ResponseBody>
) {
    @OptIn(UnstableApi::class)
    fun mediaSource(item: MediaItem): ProgressiveMediaSource =
        ProgressiveMediaSource.Factory { ProgressiveVideoDataSource(download, requestRange) }.createMediaSource(item)
}
