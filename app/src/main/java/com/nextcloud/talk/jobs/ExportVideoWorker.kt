/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.FileUtils
import java.io.File
import java.io.IOException

class ExportVideoWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val destination = inputData.getString(DESTINATION)?.toUri() ?: return Result.failure()
        val resolver = applicationContext.contentResolver
        var success = false
        try {
            copyFile(destination)
            success = true
        } catch (exception: IOException) {
            Log.e(TAG, "Video export failed", exception)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Video export permission denied", exception)
        } finally {
            if (!success) removeIncompleteDocument(destination)
            try {
                resolver.releasePersistableUriPermission(destination, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (exception: SecurityException) {
                Log.w(TAG, "Video export permission already released", exception)
            }
        }
        val message = if (success) R.string.nc_save_success else R.string.nc_common_error_sorry
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
        return if (success) Result.success() else Result.failure()
    }

    private fun copyFile(destination: Uri) {
        sourceFile().inputStream().use { input ->
            val output = applicationContext.contentResolver.openOutputStream(destination)
                ?: throw IOException("Cannot open destination")
            output.use { CancellableStreamCopy.copy(input, it, { isStopped }) }
        }
    }

    private fun sourceFile(): File {
        val name = inputData.getString(FILE_NAME) ?: throw IOException("Missing source name")
        return FileUtils.resolveSharedAttachmentFile(
            applicationContext.cacheDir,
            inputData.getString(FILE_ID),
            name
        ) ?: throw IOException("Invalid source file")
    }

    private fun removeIncompleteDocument(destination: Uri) {
        try {
            DocumentsContract.deleteDocument(applicationContext.contentResolver, destination)
        } catch (exception: IOException) {
            Log.w(TAG, "Unable to remove incomplete export", exception)
        } catch (exception: SecurityException) {
            Log.w(TAG, "Unable to remove incomplete export", exception)
        }
    }

    companion object {
        private const val TAG = "ExportVideoWorker"
        const val FILE_ID = "fileId"
        const val FILE_NAME = "fileName"
        const val DESTINATION = "destination"
    }
}
