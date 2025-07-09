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
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.util.Log
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
import java.io.IOException
import java.io.OutputStream
import java.net.URLConnection

@AutoInjector(NextcloudTalkApplication::class)
class SaveFileToStorageWorker(val context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters) {

    @Suppress("Detekt.TooGenericExceptionCaught")
    override fun doWork(): Result {
        try {
            val cacheFile = File(inputData.getString(KEY_SOURCE_FILE_PATH)!!)
            val contentResolver = context.contentResolver
            val mimeType = URLConnection.guessContentTypeFromName(cacheFile.name)

            val values = ContentValues().apply {
                if (mimeType.startsWith(IMAGE_PREFIX) || mimeType.startsWith(VIDEO_PREFIX)) {
                    val appName = applicationContext.resources!!.getString(R.string.nc_app_product_name)
                    put(FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/" + appName)
                }
                put(FileColumns.DISPLAY_NAME, cacheFile.name)

                if (mimeType != null) {
                    put(FileColumns.MIME_TYPE, mimeType)
                }
            }

            contentResolver.insert(getUriByType(mimeType), values)?.let { fileUri ->
                try {
                    val outputStream: OutputStream? = contentResolver.openOutputStream(fileUri)
                    outputStream.use { output ->
                        val inputStream = cacheFile.inputStream()
                        if (output != null) {
                            inputStream.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create output stream")
                    return Result.failure()
                }
            }

            // Notify the media scanner about the new file
            MediaScannerConnection.scanFile(context, arrayOf(cacheFile.absolutePath), null, null)

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.nc_save_success, Toast.LENGTH_SHORT).show()
            }

            return Result.success()
        } catch (e: IOException) {
            Log.e(TAG, "Something went wrong when trying to save file to internal storage", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_SHORT).show()
            }

            return Result.failure()
        } catch (e: NullPointerException) {
            Log.e(TAG, "Something went wrong when trying to save file to internal storage", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_SHORT).show()
            }

            return Result.failure()
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
