/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

object BitmapShrinker {

    private const val TAG = "BitmapShrinker"
    private const val DEGREES_90 = 90f
    private const val DEGREES_180 = 180f
    private const val DEGREES_270 = 270f

    /**
     * Decodes the image at [path], downscaled to fit within [reqWidth]x[reqHeight].
     *
     * On API 28+ this uses [ImageDecoder], which scales in a single pass and applies EXIF
     * orientation for JPEG/HEIC automatically. Older platform versions fall back to the
     * [BitmapFactory] two-pass sampling approach with manual EXIF rotation.
     *
     * [requireSoftwareBitmap] must be set when the result will be read or re-encoded (e.g. via
     * [Bitmap.compress]), since [Bitmap.Config.HARDWARE] bitmaps do not support that.
     */
    @JvmStatic
    @JvmOverloads
    fun shrinkBitmap(path: String, reqWidth: Int, reqHeight: Int, requireSoftwareBitmap: Boolean = false): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(path, reqWidth, reqHeight, requireSoftwareBitmap)
        } else {
            decodeWithBitmapFactory(path, reqWidth, reqHeight)
        }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(
        path: String,
        reqWidth: Int,
        reqHeight: Int,
        requireSoftwareBitmap: Boolean
    ): Bitmap? =
        try {
            val source = ImageDecoder.createSource(File(path))
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (width, height) = fitWithinBounds(info.size.width, info.size.height, reqWidth, reqHeight)
                decoder.setTargetSize(width, height)
                if (requireSoftwareBitmap) {
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to decode bitmap from path: $path", e)
            null
        }

    private fun fitWithinBounds(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Pair<Int, Int> {
        if (width <= reqWidth && height <= reqHeight) return width to height
        val scale = minOf(reqWidth.toDouble() / width, reqHeight.toDouble() / height)
        return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
    }

    private fun decodeWithBitmapFactory(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bitmap = decodeBitmap(path, reqWidth, reqHeight) ?: return null
        return rotateBitmap(path, bitmap)
    }

    private fun decodeBitmap(path: String, requestedWidth: Int, requestedHeight: Int): Bitmap? =
        BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, this)
            inSampleSize = getInSampleSize(this, requestedWidth, requestedHeight)
            inJustDecodeBounds = false
            val decodedBitmap = BitmapFactory.decodeFile(path, this)

            if (decodedBitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from path: $path")
                // This can occur when the file is empty or corrupted, bitmap is too large.
                // function does not throw an exception, but returns null
            }
            decodedBitmap
        }

    // solution inspired by https://developer.android.com/topic/performance/graphics/load-bitmap
    private fun getInSampleSize(options: BitmapFactory.Options, requestedWidth: Int, requestedHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > requestedHeight || width > requestedWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            // "||" was used instead of "&&". Otherwise it would still crash for wide panorama photos.
            while (halfHeight / inSampleSize >= requestedHeight || halfWidth / inSampleSize >= requestedWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // solution inspired by https://stackoverflow.com/a/15341203
    private fun rotateBitmap(path: String, bitmap: Bitmap): Bitmap {
        try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> {
                    matrix.postRotate(DEGREES_90)
                }
                ExifInterface.ORIENTATION_ROTATE_180 -> {
                    matrix.postRotate(DEGREES_180)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    matrix.postRotate(DEGREES_270)
                }
            }
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            return rotatedBitmap
        } catch (e: IOException) {
            Log.e(TAG, "error while rotating image", e)
        }
        return bitmap
    }
}
