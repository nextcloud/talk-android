/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.camera

import android.content.Context

/**
 * Stub for the generic (F-Droid) flavor. MediaPipe is not available in this build because it
 * transitively pulls in com.google.firebase:firebase-encoders* via datatransport, which F-Droid
 * rejects. Background blur is disabled for this flavor via BuildConfig.BACKGROUND_BLUR_ENABLED.
 */
class ImageSegmenterHelper(val context: Context, var imageSegmenterListener: SegmenterListener? = null) {

    fun destroyImageSegmenter() {}

    fun setupImageSegmenter() {}

    fun segmentFrame(byteBuffer: java.nio.ByteBuffer, width: Int, height: Int, videoFrameTimeStamp: Long) {}

    data class ResultBundle(val mask: ByteArray, val inferenceTime: Long, val width: Int, val height: Int) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ResultBundle
            if (inferenceTime != other.inferenceTime) return false
            if (!mask.contentEquals(other.mask)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = inferenceTime.hashCode()
            result = 31 * result + mask.contentHashCode()
            return result
        }
    }

    companion object {
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val MODEL_SELFIE_SEGMENTER_PATH = "selfie_segmenter.tflite"
        const val RGB_MAX = 255.0
    }

    interface SegmenterListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
