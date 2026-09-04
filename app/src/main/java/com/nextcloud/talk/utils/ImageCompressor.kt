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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Downscales and re-encodes images as JPEG to reduce their upload size.
 */
object ImageCompressor {

    private val TAG = ImageCompressor::class.java.simpleName
    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 80
    private const val COMPRESSED_FILE_SUFFIX = "_compressed.jpg"

    data class ImageInfo(val width: Int, val height: Int, val sizeBytes: Long)

    fun isCompressible(mimeType: String?): Boolean =
        mimeType != null && mimeType.startsWith(Mimetype.IMAGE_PREFIX) && mimeType != Mimetype.IMAGE_GIF

    /**
     * Reads the on-disk size and pixel dimensions of [sourceFile] without fully decoding it.
     */
    fun readImageInfo(sourceFile: File): ImageInfo? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return ImageInfo(options.outWidth, options.outHeight, sourceFile.length())
    }

    /**
     * Computes the dimensions and size [sourceFile] would have after [compress], without writing anything to disk.
     */
    fun estimateCompression(sourceFile: File): ImageInfo? {
        val (bitmap, bytes) = encode(sourceFile) ?: return null
        return try {
            ImageInfo(bitmap.width, bitmap.height, bytes.size.toLong())
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Returns a downscaled, JPEG re-encoded copy of [sourceFile] in the app cache directory,
     * or null if the image could not be decoded or written.
     */
    fun compress(context: Context, sourceFile: File): File? {
        val (bitmap, bytes) = encode(sourceFile) ?: return null
        return try {
            val compressedFile = File(context.cacheDir, sourceFile.nameWithoutExtension + COMPRESSED_FILE_SUFFIX)
            compressedFile.writeBytes(bytes)
            compressedFile
        } catch (e: IOException) {
            Log.e(TAG, "failed to write compressed image for ${sourceFile.name}", e)
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun encode(sourceFile: File): Pair<Bitmap, ByteArray>? {
        val bitmap = BitmapShrinker.shrinkBitmap(
            sourceFile.absolutePath,
            MAX_DIMENSION,
            MAX_DIMENSION,
            requireSoftwareBitmap = true
        )
        if (bitmap == null) {
            Log.w(TAG, "could not decode ${sourceFile.name} for compression")
            return null
        }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        return bitmap to output.toByteArray()
    }
}
