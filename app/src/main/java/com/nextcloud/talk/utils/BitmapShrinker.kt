/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.IOException

object BitmapShrinker {

    private val TAG = "BitmapShrinker"
    private const val DEGREES_90 = 90f
    private const val DEGREES_180 = 180f
    private const val DEGREES_270 = 270f

    @JvmStatic
    fun shrinkBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap {
        val bitmap = decodeBitmap(path, reqWidth, reqHeight)
        return rotateBitmap(path, bitmap)
    }

    // solution inspired by https://developer.android.com/topic/performance/graphics/load-bitmap
    private fun decodeBitmap(path: String, requestedWidth: Int, requestedHeight: Int): Bitmap =
        BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, this)
            inSampleSize = getInSampleSize(this, requestedWidth, requestedHeight)
            inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, this)
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
                bitmap.getWidth(),
                bitmap.getHeight(),
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
