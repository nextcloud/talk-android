package com.nextcloud.talk.utils

/*
 * Nextcloud Talk application
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * @author Stefan Niedermann
 * @author David A. Velasco
 * @author Chris Narkiewicz
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Stefan Niedermann <info@niedermann.it>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2016 ownCloud GmbH.
 * Copyright (C) 2020 Chris Narkiewicz <hello@ezaquarii.com>
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest

object FileUtils {
    private val TAG = FileUtils::class.java.simpleName
    private const val RADIX: Int = 16
    private const val MD5_LENGTH: Int = 32

    /**
     * Creates a new [File]
     */
    @Suppress("ThrowsCount")
    @JvmStatic
    fun getTempCacheFile(context: Context, fileName: String): File {
        val cacheFile = File(context.applicationContext.filesDir.absolutePath + "/" + fileName)
        Log.v(TAG, "Full path for new cache file:" + cacheFile.absolutePath)
        val tempDir = cacheFile.parentFile ?: throw FileNotFoundException("could not cacheFile.getParentFile()")
        if (!tempDir.exists()) {
            Log.v(
                TAG,
                "The folder in which the new file should be created does not exist yet. Trying to create it…"
            )
            if (tempDir.mkdirs()) {
                Log.v(TAG, "Creation successful")
            } else {
                throw IOException("Directory for temporary file does not exist and could not be created.")
            }
        }
        Log.v(TAG, "- Try to create actual cache file")
        if (cacheFile.createNewFile()) {
            Log.v(TAG, "Successfully created cache file")
        } else {
            throw IOException("Failed to create cacheFile")
        }
        return cacheFile
    }

    /**
     * Creates a new [File]
     */
    fun removeTempCacheFile(context: Context, fileName: String) {
        val cacheFile = File(context.applicationContext.filesDir.absolutePath + "/" + fileName)
        Log.v(TAG, "Full path for new cache file:" + cacheFile.absolutePath)
        if (cacheFile.exists()) {
            if (cacheFile.delete()) {
                Log.v(TAG, "Deletion successful")
            } else {
                throw IOException("Directory for temporary file does not exist and could not be created.")
            }
        }
    }

    @Suppress("ThrowsCount")
    fun getFileFromUri(context: Context, sourceFileUri: Uri): File? {
        val fileName = getFileName(sourceFileUri, context)
        val scheme = sourceFileUri.scheme

        val file = if (scheme == null) {
            Log.d(TAG, "relative uri: " + sourceFileUri.path)
            throw IllegalArgumentException("relative paths are not supported")
        } else if (ContentResolver.SCHEME_CONTENT == scheme) {
            copyFileToCache(context, sourceFileUri, fileName)
        } else if (ContentResolver.SCHEME_FILE == scheme) {
            if (sourceFileUri.path != null) {
                sourceFileUri.path?.let { File(it) }
            } else {
                throw IllegalArgumentException("uri does not contain path")
            }
        } else {
            throw IllegalArgumentException("unsupported scheme: " + sourceFileUri.path)
        }
        return file
    }

    @Suppress("NestedBlockDepth")
    fun copyFileToCache(context: Context, sourceFileUri: Uri, filename: String): File {
        val cachedFile = File(context.cacheDir, filename)

        if (cachedFile.exists()) {
            Log.d(TAG, "file is already in cache")
        } else {
            val outputStream = FileOutputStream(cachedFile)
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(sourceFileUri)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                outputStream.flush()
            } catch (e: FileNotFoundException) {
                Log.w(TAG, "failed to copy file to cache", e)
            }
        }
        return cachedFile
    }

    fun getFileName(uri: Uri, context: Context?): String {
        var filename: String? = null
        if (uri.scheme == "content" && context != null) {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    filename = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            } finally {
                cursor?.close()
            }
        }
        // if it was no content uri, read filename from path
        if (filename == null) {
            filename = uri.path
            val lastIndexOfSlash = filename!!.lastIndexOf('/')
            if (lastIndexOfSlash != -1) {
                filename = filename.substring(lastIndexOfSlash + 1)
            }
        }
        return filename
    }

    @JvmStatic
    fun md5Sum(file: File): String {
        val temp = file.name + file.lastModified() + file.length()
        val messageDigest = MessageDigest.getInstance("MD5")
        messageDigest.update(temp.toByteArray())
        val digest = messageDigest.digest()
        val md5String = StringBuilder(BigInteger(1, digest).toString(RADIX))
        while (md5String.length < MD5_LENGTH) {
            md5String.insert(0, "0")
        }
        return md5String.toString()
    }
}
