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
import androidx.core.graphics.createBitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink
import java.nio.ByteBuffer

class BackgroundBlurFrameProcessor(val context: Context, val isFrontFacing: Boolean) :
    VideoProcessor,
    ImageSegmenterHelper.SegmenterListener {

    companion object {
        val TAG: String = this::class.java.simpleName
        const val ROT_360 = 360
        const val KERNEL_SIZE = 25.0 // must be odd
        const val NV21_HEIGHT_MULTI = 1.5
    }

    init {
        if (OpenCVLoader.initLocal()) {
            Log.i("OpenCV", "OpenCV library loaded successfully.")
        } else {
            Log.e("OpenCV", "OpenCV library not found.")
        }
    }

    private var sink: VideoSink? = null
    private var segmenterHelper: ImageSegmenterHelper? = null
    private val frameQueue = ArrayDeque<Bitmap>()

    // SegmentationListener Interface

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "Error $errorCode: $error")
    }

    override fun onResults(resultBundle: ImageSegmenterHelper.ResultBundle) {
        if (frameQueue.isEmpty()) {
            return
        }

        val maskMat = resultBundle.mask

        val frameBitmap: Bitmap = frameQueue.removeFirst()

        val frameMat = Mat()
        val blurredMat = Mat()

        try {
            Utils.bitmapToMat(frameBitmap, frameMat)

            val blurredMat = frameMat.clone()
            Imgproc.GaussianBlur(
                blurredMat,
                blurredMat,
                Size(KERNEL_SIZE, KERNEL_SIZE),
                0.0,
                0.0
            )

            Imgproc.resize(
                maskMat,
                maskMat,
                frameMat.size(),
                .0,
                .0,
                Imgproc.INTER_LINEAR
            )

            // Copies pixels from `frameMat` to `blurredMat` ONLY where `grayMaskMat` is non-zero.
            // This keeps the background blurred, while leaving the foreground clear
            frameMat.copyTo(blurredMat, maskMat)

            val finalFrame = blurredMat.toVideoFrame(resultBundle.inferenceTime)

            sink?.onFrame(finalFrame)

            finalFrame.release()
        } finally {
            frameMat.release()
            blurredMat.release()
            maskMat.release()
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
        val buffer = videoFrame.buffer.toI420()

        if (buffer == null) {
            videoFrame.release()
            return
        }

        val frameMatrix = buffer.toMat()
        if (frameMatrix == null) {
            videoFrame.release()
            return
        }

        val center = Point(frameMatrix.width() / 2.0, frameMatrix.height() / 2.0)

        var rotationMat = Mat()

        try {
            // weirdly rotation is 270 degree in portrait and 180 degree in landscape, no idea why
            val angle = ROT_360 - videoFrame.rotation.toDouble()
            rotationMat = Imgproc.getRotationMatrix2D(
                center,
                angle,
                1.0
            )

            Imgproc.warpAffine(frameMatrix, frameMatrix, rotationMat, frameMatrix.size())

            val preProcessedBitmap = createBitmap(
                frameMatrix.width(),
                frameMatrix.height(),
                Bitmap.Config.ARGB_8888
            )

            Utils.matToBitmap(frameMatrix, preProcessedBitmap)

            if (frameQueue.isEmpty()) {
                frameQueue.add(preProcessedBitmap)
                segmenterHelper?.segmentLiveStreamFrame(preProcessedBitmap, videoFrame.timestampNs)
            } else {
                // A frame is already being processed, drop this one
                preProcessedBitmap.recycle()
            }
        } finally {
            frameMatrix.release()
            rotationMat.release()
        }
    }

    override fun setSink(sink: VideoSink?) {
        this.sink = sink
    }

    private fun Mat.toVideoFrame(time: Long): VideoFrame {
        val i420Mat = Mat()
        Imgproc.cvtColor(this, i420Mat, Imgproc.COLOR_RGBA2YUV_I420)

        // Get the raw bytes from the new I420 Mat
        val i420ByteArray = ByteArray((i420Mat.total() * i420Mat.elemSize()).toInt())
        i420Mat.get(0, 0, i420ByteArray)

        val width = this.width()
        val height = this.height()

        val yPlaneSize = width * height
        val uvPlaneSize = (width / 2) * (height / 2)

        val yBuffer = ByteBuffer.allocateDirect(yPlaneSize)
        val uBuffer = ByteBuffer.allocateDirect(uvPlaneSize)
        val vBuffer = ByteBuffer.allocateDirect(uvPlaneSize)

        yBuffer.put(i420ByteArray, 0, yPlaneSize)
        uBuffer.put(i420ByteArray, yPlaneSize, uvPlaneSize)
        vBuffer.put(i420ByteArray, yPlaneSize + uvPlaneSize, uvPlaneSize)

        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        // Create the I420Buffer using the separate planes
        val finalFrameBuffer = JavaI420Buffer.wrap(
            width,
            height,
            yBuffer,
            width,
            uBuffer,
            width / 2,
            vBuffer,
            width / 2,
            null
        )

        i420Mat.release()

        return VideoFrame(finalFrameBuffer, 0, time)
    }

    private fun VideoFrame.I420Buffer.toMat(): Mat? =
        kotlin.runCatching {
            val i420Buffer = this

            val width = i420Buffer.width
            val height = i420Buffer.height
            val yPlaneSize = width * height

            val nv21Height = (height * NV21_HEIGHT_MULTI).toInt()
            val nv21Width = width
            val nv21Size = nv21Height * nv21Width
            val nv21Data = ByteArray(nv21Size)

            val dataY = i420Buffer.dataY
            val dataU = i420Buffer.dataU
            val dataV = i420Buffer.dataV

            val strideY = i420Buffer.strideY // Likely equal to the width, but not always, depending on mem alignment
            val strideU = i420Buffer.strideU // U and V have identical dimens and strides
            val strideV = i420Buffer.strideV

            if (strideY == width) {
                // Fast path: contiguous data
                dataY.get(nv21Data, 0, yPlaneSize)
            } else {
                // Slow path: row-by-row copy
                for (row in 0 until height) {
                    dataY.position(row * strideY)
                    dataY.get(nv21Data, row * width, width)
                }
            }

            val vuPlaneOffset = width * height
            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    // Get U and V values from their respective planes using row/col/stride
                    val v = dataV[row * strideV + col]
                    val u = dataU[row * strideU + col]

                    // Put them into the NV21 buffer (V, then U)
                    val nv21Index = vuPlaneOffset + (row * width) + (col * 2)
                    nv21Data[nv21Index] = v
                    nv21Data[nv21Index + 1] = u
                }
            }

            val mat = Mat(
                nv21Height,
                nv21Width,
                CvType.CV_8UC1 // 8 bit unsigned 1 channel
            )

            mat.put(0, 0, nv21Data)
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_YUV2RGBA_NV21)

            i420Buffer.release()

            mat
        }.getOrNull()
}
