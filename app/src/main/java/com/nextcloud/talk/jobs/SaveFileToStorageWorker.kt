/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Fariba Khandani <khandani@winworker.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.utils.Mimetype.AUDIO_PREFIX
import com.nextcloud.talk.utils.Mimetype.IMAGE_PREFIX
import com.nextcloud.talk.utils.Mimetype.VIDEO_PREFIX
import java.io.File
import java.net.URLConnection

@AutoInjector(NextcloudTalkApplication::class)
class SaveFileToStorageWorker(val context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters) {

    @Suppress("Detekt.TooGenericExceptionCaught")
    override fun doWork(): Result =
        try {
            val cacheFile = File(inputData.getString(KEY_SOURCE_FILE_PATH)!!)
            saveToStorage(cacheFile)

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.nc_save_success, Toast.LENGTH_SHORT).show()
            }

            Result.success()
        } catch (e: Exception) {
            NextcloudTalkApplication.sharedApplication?.logger?.e(
                TAG,
                "Something went wrong when trying to save file to internal storage",
                e
            )
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_SHORT).show()
            }

            Result.failure()
        }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun saveToStorage(cacheFile: File) {
        check(cacheFile.isFile) { "Source file to save does not exist: ${cacheFile.name}" }

        val contentResolver = context.contentResolver
        val mimeType = URLConnection.guessContentTypeFromName(cacheFile.name) ?: "application/octet-stream"

        val values = ContentValues().apply {
            if (mimeType.startsWith(IMAGE_PREFIX) || mimeType.startsWith(VIDEO_PREFIX)) {
                val appName = applicationContext.resources!!.getString(R.string.nc_app_product_name)
                put(FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/" + appName)
            }
            put(FileColumns.DISPLAY_NAME, cacheFile.name)
            put(FileColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(FileColumns.IS_PENDING, 1)
            }
        }

        val fileUri = checkNotNull(contentResolver.insert(getUriByType(mimeType), values)) {
            "Failed to create MediaStore entry"
        }
        try {
            checkNotNull(contentResolver.openOutputStream(fileUri)) { "Failed to create output stream" }.use { output ->
                cacheFile.inputStream().use { input -> input.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishedValues = ContentValues().apply { put(FileColumns.IS_PENDING, 0) }
                contentResolver.update(fileUri, publishedValues, null, null)
            }
        } catch (e: Exception) {
            try {
                contentResolver.delete(fileUri, null, null)
            } catch (deleteException: Exception) {
                e.addSuppressed(deleteException)
            }
            throw e
        }
    }

    private fun getUriByType(mimeType: String): Uri =
        when {
            mimeType.startsWith(VIDEO_PREFIX) -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith(AUDIO_PREFIX) -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith(IMAGE_PREFIX) -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            } else {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
        }

    companion object {
        private val TAG = SaveFileToStorageWorker::class.java.simpleName
        const val KEY_FILE_NAME = "KEY_FILE_NAME"
        const val KEY_SOURCE_FILE_PATH = "KEY_SOURCE_FILE_PATH"
    }
}
