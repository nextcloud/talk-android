/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.camera

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import java.nio.ByteBuffer

class ImageSegmenterHelper(val context: Context, var imageSegmenterListener: SegmenterListener? = null) {

    private var imageSegmenter: ImageSegmenter? = null

    init {
        setupImageSegmenter()
    }

    // Segmenter must be closed when creating a new one to avoid returning results to a non-existent object
    fun destroyImageSegmenter() {
        imageSegmenter?.close()
        imageSegmenter = null
    }

    /**
     * Initialize the image segmenter using current settings on the
     * thread that is using it. CPU can be used with detectors
     * that are created on the main thread and used on a background thread, but
     * the GPU delegate needs to be used on the thread that initialized the
     * segmenter
     *
     * @throws IllegalStateException
     */
    fun setupImageSegmenter() {
        val baseOptionsBuilder = BaseOptions.builder().apply {
            setDelegate(Delegate.CPU)
            setModelAssetPath(MODEL_SELFIE_SEGMENTER_PATH)
        }

        if (imageSegmenterListener == null) {
            throw IllegalStateException("ImageSegmenterListener must be set.")
        }

        runCatching {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder = ImageSegmenter.ImageSegmenterOptions.builder()
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setBaseOptions(baseOptions)
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(false)
                .setResultListener(this::returnSegmentationResult)
                .setErrorListener(this::returnSegmentationHelperError)

            imageSegmenter = ImageSegmenter.createFromOptions(context, optionsBuilder.build())
        }.getOrElse { e ->
            when (e) {
                is IllegalStateException -> {
                    imageSegmenterListener?.onError(
                        "Image segmenter failed to initialize. See error logs for details"
                    )
                    Log.e(TAG, "Image segmenter failed to load model with error: ${e.message}")
                }

                is RuntimeException -> {
                    // This occurs if the model being used does not support GPU
                    imageSegmenterListener?.onError(
                        "Image segmenter failed to initialize. See error logs for details",
                        GPU_ERROR
                    )
                    Log.e(TAG, "Image segmenter failed to load model with error: ${e.message}")
                }
            }
        }
    }

    /**
     * Runs image segmentation on live streaming cameras frame-by-frame and
     * returns the results asynchronously to the given `imageSegmenterListener`
     *
     * @throws IllegalArgumentException
     */
    fun segmentFrame(byteBuffer: ByteBuffer, width: Int, height: Int, videoFrameTimeStamp: Long) {
        val mpImage = ByteBufferImageBuilder(byteBuffer, width, height, MPImage.IMAGE_FORMAT_RGBA).build()

        imageSegmenter?.segmentAsync(mpImage, videoFrameTimeStamp)
    }

    // MPImage isn't necessary, but the listener requires it
    private fun returnSegmentationResult(result: ImageSegmenterResult, image: MPImage) {
        // We only need the first mask for this sample because we are using
        // the OutputType CATEGORY_MASK, which only provides a single mask.
        val mpImage = result.categoryMask().get()

        val mask = ByteBufferExtractor.extract(mpImage)

        val data = ByteArray(mask.capacity())

        (mask.duplicate().rewind() as ByteBuffer).get(data)

        imageSegmenterListener?.onResults(
            ResultBundle(
                data,
                result.timestampMs(), // videoFrameTimeStamp
                mpImage.width,
                mpImage.height
            )
        )
    }

    // Return errors thrown during segmentation to this ImageSegmenterHelper's caller
    private fun returnSegmentationHelperError(error: RuntimeException) {
        imageSegmenterListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    // Wraps results from inference, the time it takes for inference to be performed.
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
        private const val TAG = "ImageSegmenterHelper"
    }

    interface SegmenterListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
