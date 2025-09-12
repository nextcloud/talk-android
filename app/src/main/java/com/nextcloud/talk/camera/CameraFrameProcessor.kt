/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.createBitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink
import java.nio.Buffer
import java.nio.ByteBuffer

class CameraFrameProcessor(
    val context: Context,
    val isFrontFacing: Boolean
): VideoProcessor, ImageSegmenterHelper.SegmenterListener {

    companion object {
        val TAG: String = this::class.java.simpleName
    }

    private var sink: VideoSink? = null
    private var segmenterHelper: ImageSegmenterHelper? = null
    private val frameQueue = ArrayDeque<Bitmap>()

    // SegmentationListener Interface

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "Error $errorCode: $error")
    }

    override fun onResults(resultBundle: ImageSegmenterHelper.ResultBundle) {
        // NOTE- selfie segmentation model returns a binary "matrix" of 0 for background, 1 for foreground.

        val maskBitmap: Bitmap = resultBundle.mask
        val frameBitmap: Bitmap = frameQueue.removeFirst()

        val frameMat = Mat()
        val blurredMat = Mat()
        val maskMat = Mat()
        val grayMaskMat = Mat()

        try {
            Utils.bitmapToMat(frameBitmap, frameMat)
            Utils.bitmapToMat(maskBitmap, maskMat)

            Imgproc.cvtColor(
                maskMat,
                grayMaskMat,
                Imgproc.COLOR_RGBA2GRAY  // single-channel (grayscale) for OpenCV functions.
            )

            val blurredMat = frameMat.clone()
            Imgproc.GaussianBlur(
                blurredMat,
                blurredMat,
                Size(7.0, 7.0),
                0.0,
                0.0
            )

            // Copies pixels from `frameMat` to `blurredMat` ONLY where `grayMaskMat` is non-zero.
            // This keeps the background blurred, while leaving the foreground clear
            frameMat.copyTo(blurredMat, grayMaskMat)

            val finalFrameBitmap = createBitmap(
                frameBitmap.width,
                frameBitmap.height,
                Bitmap.Config.ARGB_8888
            )

            Utils.matToBitmap(blurredMat, finalFrameBitmap)

            val finalFrameBuffer = ByteBuffer.allocate(finalFrameBitmap.allocationByteCount)
            finalFrameBitmap.copyPixelsToBuffer(finalFrameBuffer)

            val timeStamp = SystemClock.elapsedRealtimeNanos()

            // FIXME - I'm worried about the rotation aspect. I don't get it, and I don't want it to mess up processing
            val finalFrame = VideoFrame(finalFrameBuffer as VideoFrame.Buffer, 0, timeStamp)

            sink?.onFrame(finalFrame)

            finalFrame.release()

        } finally {
            frameMat.release()
            blurredMat.release()
            maskMat.release()
            grayMaskMat.release()
        }
    }

    // Video Processor Interface

    override fun onCapturerStarted(success: Boolean) {
        segmenterHelper = ImageSegmenterHelper(context = context, imageSegmenterListener = this)
    }

    override fun onCapturerStopped() {
        segmenterHelper?.destroyImageSegmenter()
    }

    override fun onFrameCaptured(videoFrame: VideoFrame) {
        val bitmapBuffer = createBitmap(videoFrame.buffer.width, videoFrame.buffer.height)
        bitmapBuffer.copyPixelsFromBuffer(videoFrame.buffer as Buffer)

        val matrix = Matrix().apply {
            postRotate(videoFrame.rotation.toFloat())

            if(isFrontFacing) {
                postScale(
                    -1f,
                    1f,
                    bitmapBuffer.width.toFloat(),
                    bitmapBuffer.height.toFloat()
                )
            }
        }

        // TODO - should I convert to RBG before submitting?
        // Imgproc.cvtColor(matrix, matrix, Imgproc.COLOR_YUV2RGB_I420)

        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer,
            0,
            0,
            bitmapBuffer.width,
            bitmapBuffer.height,
            matrix,
            true
        )

        frameQueue.add(rotatedBitmap)
        segmenterHelper?.segmentLiveStreamFrame(rotatedBitmap)

        videoFrame.release()
    }

    override fun setSink(sink: VideoSink?) {
        this.sink = sink
    }
}
