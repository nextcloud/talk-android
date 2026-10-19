/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

/**
 * Reads the newest photos and videos from MediaStore. Without read access (or with partial access) the system
 * returns only what the app may see, so no permission check happens here.
 */
object RecentMediaLoader {
    private val TAG = RecentMediaLoader::class.java.simpleName
    private const val LIMIT = 300
    private const val THUMBNAIL_PX = 320

    suspend fun load(resolver: ContentResolver, limit: Int = LIMIT): List<RecentMedia> {
        val images = query(resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false, limit)
        val videos = query(resolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true, limit)
        return mergeRecentMedia(images, videos, limit)
    }

    fun uriOf(media: RecentMedia): Uri {
        val base = if (media.isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return ContentUris.withAppendedId(base, media.id)
    }

    /**
     * Newest first with a row limit. Before Android 11 the provider ignores a limit passed in the query bundle, so
     * it is appended to the sort order there.
     */
    private fun openCursor(resolver: ContentResolver, uri: Uri, projection: Array<String>, limit: Int): Cursor? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val args = Bundle().apply {
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_ADDED))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            }
            resolver.query(uri, projection, args, null)
        } else {
            resolver.query(uri, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC LIMIT $limit")
        }

    /**
     * A small preview frame of a video, or null when the system has none.
     */
    @Suppress("DEPRECATION")
    fun videoThumbnail(resolver: ContentResolver, media: RecentMedia): Bitmap? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.loadThumbnail(uriOf(media), Size(THUMBNAIL_PX, THUMBNAIL_PX), null)
            } else {
                MediaStore.Video.Thumbnails.getThumbnail(
                    resolver,
                    media.id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            }
        } catch (e: IOException) {
            Log.w(TAG, "no video thumbnail for ${media.id}", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "video thumbnail not readable for ${media.id}", e)
            null
        }

    private suspend fun query(resolver: ContentResolver, uri: Uri, isVideo: Boolean, limit: Int): List<RecentMedia> {
        val projection = if (isVideo) {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.Video.VideoColumns.DURATION
            )
        } else {
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
        }
        val result = mutableListOf<RecentMedia>()
        openCursor(resolver, uri, projection, limit)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val durationColumn = if (isVideo) {
                cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
            } else {
                -1
            }
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                result += RecentMedia(
                    id = cursor.getLong(idColumn),
                    isVideo = isVideo,
                    dateAddedSeconds = cursor.getLong(dateColumn),
                    durationMs = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
                )
            }
        }
        return result
    }
}
