/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.nextcloud.talk.application.NextcloudTalkApplication
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ExecutionException

/**
 * The read access to the files picked for an upload, e.g. in the photo picker or the file picker. The pickers grant it
 * only to the chat that picked them, until it is closed or the process is killed, while the upload can wait for the
 * network for days. So the access is persisted when the upload is queued, and released once no upload needs it.
 *
 * The app persists the access to files only for uploads: every persisted one without an unfinished upload is released.
 */
object UploadSourceGrants {

    private val TAG = UploadSourceGrants::class.java.simpleName

    private const val UPLOAD_TAG_PREFIX = "upload_source_"

    /** The tag for the upload of [fileUri] if it is a content uri, whose read access is kept until it is done. */
    fun uploadTagOf(fileUri: String): String? =
        fileUri.takeIf { it.toUri().scheme == ContentResolver.SCHEME_CONTENT }?.let { UPLOAD_TAG_PREFIX + it }

    /** Keeps the read access to [fileUri] for its upload, if its provider allows it. */
    fun keep(context: Context, fileUri: String) {
        if (uploadTagOf(fileUri) == null) return
        try {
            context.contentResolver.takePersistableUriPermission(fileUri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // e.g. a file of the app itself, or of a provider that doesn't allow it
            Log.d(TAG, "The read access to $fileUri can't be kept", e)
        }
    }

    /**
     * Releases the persisted read access to [fileUri] unless an upload other than [exceptWorkId], the one that is just
     * ending, still needs it. Blocks while it asks WorkManager for its uploads.
     */
    fun release(context: Context, fileUri: String, exceptWorkId: UUID) {
        val uri = fileUri.toUri()
        val isKept = context.contentResolver.persistedUriPermissions.any { it.isReadPermission && it.uri == uri }
        if (isKept) releaseUnless(context, listOf(uri), exceptWorkId)
    }

    /** [release] in the scope of the app, e.g. for an upload that was cancelled before it started. */
    fun releaseLater(fileUri: String, exceptWorkId: UUID) {
        val application = NextcloudTalkApplication.sharedApplication!!
        application.appScope.launch { release(application, fileUri, exceptWorkId) }
    }

    /**
     * Releases the persisted read access to all files without an unfinished upload, e.g. of uploads that were removed
     * before they ran. Run when the app starts, before a file can be picked. Blocks while it asks WorkManager for their
     * uploads.
     */
    fun releaseUnused(context: Context) {
        val kept = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }
        releaseUnless(context, kept, exceptWorkId = null)
    }

    /** Releases the read access to those of [uris] without an unfinished upload other than [exceptWorkId]. */
    private fun releaseUnless(context: Context, uris: List<Uri>, exceptWorkId: UUID?) {
        val uploading = urisWithUnfinishedUpload(context, uris, exceptWorkId)
        uris.filterNot { it in uploading }.forEach { uri ->
            try {
                context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to release the read access to $uri", e)
            }
        }
    }

    /**
     * The ones of [uris] with an unfinished upload other than [exceptWorkId], all of them when WorkManager can't tell,
     * so the access is kept when in doubt. One query for all of them.
     */
    private fun urisWithUnfinishedUpload(context: Context, uris: List<Uri>, exceptWorkId: UUID?): Set<Uri> {
        if (uris.isEmpty()) return emptySet()
        val query = WorkQuery.Builder.fromTags(uris.mapNotNull { uploadTagOf(it.toString()) })
            .addStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED))
            .build()
        return try {
            WorkManager.getInstance(context).getWorkInfos(query).get()
                .filter { it.id != exceptWorkId }
                .flatMap { it.tags }
                .filter { it.startsWith(UPLOAD_TAG_PREFIX) }
                .map { it.removePrefix(UPLOAD_TAG_PREFIX).toUri() }
                .toSet()
        } catch (e: ExecutionException) {
            Log.w(TAG, "Failed to get the uploads of picked files", e)
            uris.toSet()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            uris.toSet()
        }
    }
}
