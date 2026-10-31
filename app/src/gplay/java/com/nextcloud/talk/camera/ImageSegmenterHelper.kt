/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.camera

import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

class ImageSegmenterHelper(val context: Context, var imageSegmenterListener: SegmenterListener? = null) {

    @Volatile
    private var interpreter: Interpreter? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)

    private val inputBuffer = ByteBuffer.allocateDirect(MODEL_SIZE * MODEL_SIZE * RGB_CHANNELS * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
    private val outputBuffer = ByteBuffer.allocateDirect(MODEL_SIZE * MODEL_SIZE * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
    private val confidence = FloatArray(MODEL_SIZE * MODEL_SIZE)

    init {
        setupImageSegmenter()
    }

    // Closed on the worker thread, so a running inference finishes first
    fun destroyImageSegmenter() {
        if (executor.isShutdown) return
        executor.execute {
            interpreter?.close()
            interpreter = null
        }
        executor.shutdown()
    }

    /**
     * Loads the selfie segmentation model and creates a CPU interpreter for it.
     *
     * @throws IllegalStateException
     */
    fun setupImageSegmenter() {
        if (imageSegmenterListener == null) {
            throw IllegalStateException("ImageSegmenterListener must be set.")
        }

        runCatching {
            val model = context.assets.openFd(MODEL_SELFIE_SEGMENTER_PATH).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use {
                    it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
            val newInterpreter = Interpreter(model, Interpreter.Options().setNumThreads(NUM_THREADS))
            val input = newInterpreter.getInputTensor(0)
            val output = newInterpreter.getOutputTensor(0)
            val supported = input.dataType() == DataType.FLOAT32 &&
                input.shape().contentEquals(intArrayOf(1, MODEL_SIZE, MODEL_SIZE, RGB_CHANNELS)) &&
                output.dataType() == DataType.FLOAT32 &&
                output.shape().contentEquals(intArrayOf(1, MODEL_SIZE, MODEL_SIZE, 1))
            if (!supported) {
                newInterpreter.close()
                error(
                    "Unexpected model tensors: input ${input.shape().contentToString()}, " +
                        "output ${output.shape().contentToString()}"
                )
            }
            interpreter = newInterpreter
        }.onFailure { e ->
            imageSegmenterListener?.onError("Image segmenter failed to initialize. See error logs for details")
            Log.e(TAG, "Image segmenter failed to load model with error: ${e.message}")
        }
    }

    /**
     * Runs image segmentation on an RGBA camera frame and returns the result asynchronously to the given
     * `imageSegmenterListener`. Frames arriving while the previous one is still processed are dropped.
     */
    fun segmentFrame(byteBuffer: ByteBuffer, width: Int, height: Int, videoFrameTimeStamp: Long) {
        if (interpreter == null || !busy.compareAndSet(false, true)) {
            return
        }

        try {
            executor.execute {
                try {
                    runSegmentation(byteBuffer, width, height, videoFrameTimeStamp)
                } finally {
                    busy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
            Log.d(TAG, "Frame dropped, segmenter is shut down: ${e.message}")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runSegmentation(byteBuffer: ByteBuffer, width: Int, height: Int, videoFrameTimeStamp: Long) {
        val interpreter = interpreter ?: return
        try {
            writeModelInput(byteBuffer, width, height, inputBuffer)
            outputBuffer.rewind()
            interpreter.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()
            outputBuffer.asFloatBuffer().get(confidence)

            imageSegmenterListener?.onResults(
                ResultBundle(
                    confidenceToMask(confidence, width, height),
                    videoFrameTimeStamp,
                    width,
                    height
                )
            )
        } catch (e: RuntimeException) {
            imageSegmenterListener?.onError(e.message ?: "An unknown error has occurred")
        }
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
        fun isAvailable() = true
        const val OTHER_ERROR = 0
        const val MODEL_SELFIE_SEGMENTER_PATH = "selfie_segmenter.tflite"
        const val RGB_MAX = 255.0f
        const val MODEL_SIZE = 256
        const val RGB_CHANNELS = 3
        const val RGBA_CHANNELS = 4
        const val PERSON_THRESHOLD = 0.5f
        const val MASK_BACKGROUND: Byte = 1
        const val MASK_PERSON: Byte = 0
        private const val NUM_THREADS = 2
        private const val TAG = "ImageSegmenterHelper"

        /**
         * Scales an RGBA frame (nearest neighbour) to the model input size and writes it as RGB floats in [0, 1].
         * Uses absolute reads, so the frame buffer's position is left untouched for the GPU thread.
         */
        internal fun writeModelInput(frame: ByteBuffer, width: Int, height: Int, out: ByteBuffer) {
            out.rewind()
            for (y in 0 until MODEL_SIZE) {
                val rowOffset = (y * height / MODEL_SIZE) * width
                for (x in 0 until MODEL_SIZE) {
                    val index = (rowOffset + x * width / MODEL_SIZE) * RGBA_CHANNELS
                    for (channel in 0 until RGB_CHANNELS) {
                        out.putFloat(frame.get(index + channel).toUByte().toFloat() / RGB_MAX)
                    }
                }
            }
            out.rewind()
        }

        /**
         * Turns the model's person confidence into a frame sized mask in the format
         * BackgroundBlurGPUProcessor expects: [MASK_BACKGROUND] (blurred) or [MASK_PERSON] (kept sharp).
         */
        internal fun confidenceToMask(confidence: FloatArray, width: Int, height: Int): ByteArray {
            val mask = ByteArray(width * height)
            for (y in 0 until height) {
                val rowOffset = (y * MODEL_SIZE / height) * MODEL_SIZE
                for (x in 0 until width) {
                    val isPerson = confidence[rowOffset + x * MODEL_SIZE / width] > PERSON_THRESHOLD
                    mask[y * width + x] = if (isPerson) MASK_PERSON else MASK_BACKGROUND
                }
            }
            return mask
        }
    }

    interface SegmenterListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
