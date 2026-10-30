/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.nextcloud.talk.BuildConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * The copies that ShareActivity makes of the files other apps share, one directory per share inside a cache directory
 * of their own, which [FileUtils.isTrustedShareUri] trusts like the shared attachments directory. That one isn't used,
 * as the attachments in it are named by their senders, and an attachment named like the directory of the copies would
 * take its place.
 *
 * An upload of a copy can wait for the network for days, so it is tagged with the directory of its share (see
 * [uploadTagOf]), and the directory is only removed once no upload with that tag is unfinished.
 */
object SharedFileCopies {

    private val TAG = SharedFileCopies::class.java.simpleName

    private const val UPLOAD_TAG_PREFIX = "shared_file_copy_"

    // so the preview of an upload that failed still shows the file for a while
    private const val KEPT_DAYS = 1L

    // the provider path of a copy: directory of the copies, directory of the share, directory of the item, file
    private const val SHARE_DIRECTORY_SEGMENT = 1

    /**
     * Creates the directory for the copies of a new share, after removing those of older shares that are done. Blocks
     * while it asks WorkManager for their uploads.
     */
    fun createDirectory(context: Context): File? {
        val sharesDirectory = File(context.cacheDir, FileUtils.SHARED_FILE_COPIES_DIRECTORY)
        val expiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(KEPT_DAYS)
        sharesDirectory.listFiles()
            ?.filter { it.lastModified() < expiry && !hasUnfinishedUpload(context, it.name) }
            ?.forEach { it.deleteRecursively() }
        return File(sharesDirectory, UUID.randomUUID().toString()).takeIf { it.mkdirs() }
    }

    /** The tag for the upload of [fileUri] if it is a copy of a share: it keeps the copy until the upload is done. */
    fun uploadTagOf(fileUri: String): String? {
        val uri = fileUri.toUri()
        val segments = uri.pathSegments
        val isCopy = uri.scheme == ContentResolver.SCHEME_CONTENT &&
            uri.authority == BuildConfig.APPLICATION_ID &&
            segments.size > SHARE_DIRECTORY_SEGMENT + 1 &&
            segments[0] == FileUtils.SHARED_FILE_COPIES_DIRECTORY
        return if (isCopy) UPLOAD_TAG_PREFIX + segments[SHARE_DIRECTORY_SEGMENT] else null
    }

    private fun hasUnfinishedUpload(context: Context, directoryName: String): Boolean =
        try {
            WorkManager.getInstance(context).getWorkInfosByTag(UPLOAD_TAG_PREFIX + directoryName).get()
                .any { !it.state.isFinished }
        } catch (e: ExecutionException) {
            // keep the copies when in doubt
            Log.w(TAG, "Failed to get the uploads of shared files", e)
            true
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            true
        }
}
