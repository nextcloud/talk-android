/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Disk cache for locally-extracted video first frames, keyed by the message's referenceId.
 *
 * Used when a video's server-side preview is unavailable: the frame extracted from the local file
 * right after upload would otherwise only live in memory for the current chat session, disappearing
 * as soon as the chat is left and reopened. Persisting it here lets that fallback survive across
 * chat sessions (and app restarts) without needing the original local file anymore.
 */
object VideoThumbnailCache {
    private val TAG = VideoThumbnailCache::class.simpleName
    private const val CACHE_DIR_NAME = "video_thumbnails"
    private const val JPEG_QUALITY = 80

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, CACHE_DIR_NAME)
            .apply { mkdirs() }

    fun get(context: Context, referenceId: String): Bitmap? {
        val file = File(cacheDir(context), "$referenceId.jpg")
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    @Suppress("TooGenericExceptionCaught")
    fun put(context: Context, referenceId: String, bitmap: Bitmap) {
        try {
            FileOutputStream(File(cacheDir(context), "$referenceId.jpg")).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache video thumbnail for referenceId=$referenceId", e)
        }
    }
}
