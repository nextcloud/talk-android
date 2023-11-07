/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.jobs

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.URLConnection

@AutoInjector(NextcloudTalkApplication::class)
class SaveFileToStorageWorker(val context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters) {

    override fun doWork(): Result {
        try {
            val sourceFilePath = inputData.getString(KEY_SOURCE_FILE_PATH)
            val cacheFile = File(sourceFilePath!!)

            val contentResolver = context.contentResolver
            val mimeType = URLConnection.guessContentTypeFromName(cacheFile.name)

            val values = ContentValues().apply {
                put(FileColumns.DISPLAY_NAME, cacheFile.name)
                put(FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                if (mimeType != null) {
                    put(FileColumns.MIME_TYPE, URLConnection.guessContentTypeFromName(cacheFile.name))
                }
            }

            val collection = MediaStore.Files.getContentUri("external")
            val uri = contentResolver.insert(collection, values)

            uri?.let { fileUri ->
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

            return Result.success()
        } catch (e: IOException) {
            Log.e(TAG, "Something went wrong when trying to save file to internal storage", e)
            return Result.failure()
        } catch (e: NullPointerException) {
            Log.e(TAG, "Something went wrong when trying to save file to internal storage", e)
            return Result.failure()
        }
    }

    companion object {
        private val TAG = SaveFileToStorageWorker::class.java.simpleName
        const val KEY_FILE_NAME = "KEY_FILE_NAME"
        const val KEY_SOURCE_FILE_PATH = "KEY_SOURCE_FILE_PATH"
    }
}
