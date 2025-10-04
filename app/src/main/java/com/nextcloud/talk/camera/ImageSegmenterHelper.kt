/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import java.nio.ByteBuffer

class ImageSegmenterHelper(
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    val context: Context,
    var imageSegmenterListener: SegmenterListener? = null
) {

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
        val baseOptionsBuilder = BaseOptions.builder()
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }

            DELEGATE_GPU -> {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            }
        }

        baseOptionsBuilder.setModelAssetPath(MODEL_SELFIE_SEGMENTER_PATH)

        if (imageSegmenterListener == null) {
            throw IllegalStateException("ImageSegmenterListener must be set.")
        }

        runCatching {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder = ImageSegmenter.ImageSegmenterOptions.builder()
                .setRunningMode(runningMode)
                .setBaseOptions(baseOptions)
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(false)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnSegmentationResult)
                    .setErrorListener(this::returnSegmentationHelperError)
            }

            val options = optionsBuilder.build()
            imageSegmenter = ImageSegmenter.createFromOptions(context, options)
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
    fun segmentLiveStreamFrame(bitmap: Bitmap, videoFrameTimeStamp: Long) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call segmentLiveStreamFrame while not using RunningMode.LIVE_STREAM"
            )
        }

        val mpImage = BitmapImageBuilder(bitmap).build()

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

        val mat = Mat(
            mpImage.height,
            mpImage.width,
            CvType.CV_8UC1 // 8 bit unsigned 1 channel
        )

        mat.put(0, 0, data)

        Core.bitwise_not(mat, mat)
        Core.multiply(mat, Scalar(RGB_MAX), mat)

        imageSegmenterListener?.onResults(
            ResultBundle(
                mat,
                result.timestampMs() // videoFrameTimeStamp
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
    data class ResultBundle(val mask: Mat, val inferenceTime: Long)

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1 // DO NOT USE THIS
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
