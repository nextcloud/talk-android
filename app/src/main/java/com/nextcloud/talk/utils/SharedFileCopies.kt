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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.application.NextcloudTalkApplication
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * The copies that ShareActivity makes of the files other apps share, one directory per share inside a directory of
 * their own, which [FileUtils.isTrustedShareUri] trusts like the shared attachments directory. That one isn't used,
 * as the attachments in it are named by their senders, and an attachment named like the directory of the copies would
 * take its place.
 *
 * An upload of a copy can wait for the network for days, so it is tagged with the directory of its share (see
 * [uploadTagOf]), and the directory is only removed once no upload with that tag is unfinished. So the copies are in
 * the files directory, which the system, unlike the cache, doesn't clear when the storage runs low, and they are
 * removed by the app: when their share is left, and once they are done (see [removeDoneShares]).
 */
object SharedFileCopies {

    private val TAG = SharedFileCopies::class.java.simpleName

    private const val UPLOAD_TAG_PREFIX = "shared_file_copy_"

    // so the preview of an upload that failed still shows the file for a while
    private const val KEPT_DAYS = 1L

    // the provider path of a copy: directory of the copies, directory of the share, directory of the item, file
    private const val SHARE_DIRECTORY_SEGMENT = 1

    /**
     * Creates the directory for the copies of a new share, after removing those of older shares that are done (see
     * [removeDoneShares]). Blocks while it asks WorkManager for their uploads.
     */
    fun createDirectory(context: Context): File? {
        removeDoneShares(context)
        return File(sharesDirectory(context), UUID.randomUUID().toString()).takeIf { it.mkdirs() }
    }

    /**
     * Removes the copies of the shares that are done: older than [KEPT_DAYS] and without an unfinished upload, e.g.
     * those that were uploaded or left. Run when the app starts and when a share starts. Blocks while it asks
     * WorkManager for their uploads.
     */
    fun removeDoneShares(context: Context) {
        val expiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(KEPT_DAYS)
        val expired = sharesDirectory(context).listFiles()?.filter { it.lastModified() < expiry }.orEmpty()
        val uploading = sharesWithUnfinishedUpload(context, expired.map { it.name })
        expired.filterNot { it.name in uploading }.forEach { it.deleteRecursively() }
    }

    /**
     * Removes the copies of the shares that the [fileUris] are copies of, unless one of them has an unfinished upload:
     * for a share that was left before its files were sent, e.g. in the conversation list or the attachment preview.
     * Other uris are ignored. Blocks while it asks WorkManager for their uploads.
     */
    fun removeSharesOf(context: Context, fileUris: Collection<String>) {
        val sharesDirectory = sharesDirectory(context)
        val names = fileUris.mapNotNull(::shareDirectoryNameOf).distinct()
        val uploading = sharesWithUnfinishedUpload(context, names)
        names.filterNot { it in uploading }
            .mapNotNull { FileUtils.resolveFileInDirectory(sharesDirectory, it) }
            .forEach { it.deleteRecursively() }
    }

    /** [removeSharesOf] in the scope of the app, so also after the screen that left the share is gone. */
    fun removeSharesLater(fileUris: Collection<String>) {
        if (fileUris.isEmpty()) return
        val application = NextcloudTalkApplication.sharedApplication!!
        application.appScope.launch { removeSharesOf(application, fileUris) }
    }

    /** The tag for the upload of [fileUri] if it is a copy of a share: it keeps the copy until the upload is done. */
    fun uploadTagOf(fileUri: String): String? = shareDirectoryNameOf(fileUri)?.let { UPLOAD_TAG_PREFIX + it }

    /** The name of the directory of the share that [fileUri] is a copy of, else null. */
    private fun shareDirectoryNameOf(fileUri: String): String? {
        val uri = fileUri.toUri()
        val segments = uri.pathSegments
        val isCopy = uri.scheme == ContentResolver.SCHEME_CONTENT &&
            uri.authorityOfOwnUser == BuildConfig.APPLICATION_ID &&
            segments.size > SHARE_DIRECTORY_SEGMENT + 1 &&
            segments[0] == FileUtils.SHARED_FILE_COPIES_DIRECTORY
        return if (isCopy) segments[SHARE_DIRECTORY_SEGMENT] else null
    }

    private fun sharesDirectory(context: Context) = File(context.filesDir, FileUtils.SHARED_FILE_COPIES_DIRECTORY)

    /**
     * The ones of the share directories [directoryNames] with an unfinished upload, all of them when WorkManager
     * can't tell, so the copies are kept when in doubt. One query for all of them.
     */
    private fun sharesWithUnfinishedUpload(context: Context, directoryNames: Collection<String>): Set<String> {
        if (directoryNames.isEmpty()) return emptySet()
        val query = WorkQuery.Builder.fromTags(directoryNames.map { UPLOAD_TAG_PREFIX + it })
            .addStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED))
            .build()
        return try {
            WorkManager.getInstance(context).getWorkInfos(query).get()
                .flatMap { it.tags }
                .filter { it.startsWith(UPLOAD_TAG_PREFIX) }
                .map { it.removePrefix(UPLOAD_TAG_PREFIX) }
                .toSet()
        } catch (e: ExecutionException) {
            Log.w(TAG, "Failed to get the uploads of shared files", e)
            directoryNames.toSet()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            directoryNames.toSet()
        }
    }
}
