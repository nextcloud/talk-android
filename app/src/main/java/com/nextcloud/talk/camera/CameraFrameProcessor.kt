/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.createBitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class CameraFrameProcessor(
    val context: Context,
    val isFrontFacing: Boolean
): VideoProcessor, ImageSegmenterHelper.SegmenterListener {

    companion object {
        val TAG: String = this::class.java.simpleName
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
        // NOTE- selfie segmentation model returns a binary "matrix" of 0 for background, 1 for foreground.
        if (frameQueue.isEmpty()) {
            return
        }

        resultBundle.mask
        val frameBitmap: Bitmap = frameQueue.removeFirst()

        val frameMat = Mat()
        val blurredMat = Mat()
        val maskMat = Mat()
        val grayMaskMat = Mat()

        try {
            Utils.bitmapToMat(frameBitmap, frameMat)
            // Utils.bitmapToMat(maskBitmap, maskMat)
            //
            // Imgproc.cvtColor(
            //     maskMat,
            //     grayMaskMat,
            //     Imgproc.COLOR_RGBA2GRAY  // single-channel (grayscale) for OpenCV functions.
            // )
            //
            // val blurredMat = frameMat.clone()
            // Imgproc.GaussianBlur(
            //     blurredMat,
            //     blurredMat,
            //     Size(25.0, 25.0),
            //     0.0,
            //     0.0
            // )

            // FIXME - I'm tested this out with a custom circle mask, to confirm that the problem is
            //  indeed that my segmentation mask from MediaPipe, is not being formatted properly against the
            //  the frame matrix, leading to improper applications of blurring

            val grayMaskMat = Mat(frameMat.size(), CvType.CV_8UC1, Scalar(0.0))

            // 2. Define the center and radius for your circle
            val center = Point(frameMat.width() / 2.0, frameMat.height() / 2.0)
            val radius = (frameMat.width() / 4) // You can adjust this value

            // 3. Draw a filled, white (255.0) circle onto the black mask
            Imgproc.circle(
                grayMaskMat,       // The Mat to draw on
                center,            // The center point
                radius,            // The radius
                Scalar(255.0),     // Color (white)
                Imgproc.FILLED     // Thickness (filled)
            )

            val blurredMat = frameMat.clone()
            Imgproc.GaussianBlur(
                blurredMat,
                blurredMat,
                Size(25.0, 25.0),
                0.0,
                0.0
            )

            // Copies pixels from `frameMat` to `blurredMat` ONLY where `grayMaskMat` is non-zero.
            // This keeps the background blurred, while leaving the foreground clear
            frameMat.copyTo(blurredMat, grayMaskMat)

            val i420Mat = Mat()
            Imgproc.cvtColor(blurredMat, i420Mat, Imgproc.COLOR_RGBA2YUV_I420)

            // Get the raw bytes from the new I420 Mat
            val i420ByteArray = ByteArray((i420Mat.total() * i420Mat.elemSize()).toInt())
            i420Mat.get(0, 0, i420ByteArray)

            // Get the dimensions
            val width = blurredMat.width()
            val height = blurredMat.height()

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

            val timeStamp = SystemClock.elapsedRealtimeNanos()
            val finalFrame = VideoFrame(finalFrameBuffer, 0, timeStamp)

            sink?.onFrame(finalFrame)

            finalFrame.release()
            i420Mat.release() // Release the Mat we created
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
        val bitmapFrame = videoFrame.buffer.toI420()?.toBitmap()

        if (bitmapFrame == null) {
            videoFrame.release()
            return
        }

        val frameMatrix = Mat()
        var rotationMatrix = Mat()

        try {
            Utils.bitmapToMat(bitmapFrame, frameMatrix)

            val center = Point(frameMatrix.width() / 2.0, frameMatrix.height() / 2.0)

            rotationMatrix = Imgproc.getRotationMatrix2D(center, videoFrame.rotation.toDouble(), 1.0)

            Imgproc.warpAffine(frameMatrix, frameMatrix, rotationMatrix, frameMatrix.size())

            if (isFrontFacing) {
                Core.flip(frameMatrix, frameMatrix, 0) // TODO flip this horizontal
            }

            val preProcessedBitmap = createBitmap(
                frameMatrix.width(),
                frameMatrix.height(),
                Bitmap.Config.ARGB_8888
            )

            Utils.matToBitmap(frameMatrix, preProcessedBitmap)

            if (frameQueue.isEmpty()) {
                frameQueue.add(preProcessedBitmap)
                segmenterHelper?.segmentLiveStreamFrame(preProcessedBitmap)
            } else {
                // A frame is already being processed, drop this one
                preProcessedBitmap.recycle()
            }
        } finally {
            frameMatrix.release()
            rotationMatrix.release()
        }
    }

    override fun setSink(sink: VideoSink?) {
        this.sink = sink
    }

    fun VideoFrame.I420Buffer.toBitmap(): Bitmap? {
        return kotlin.runCatching {
            val i420Buffer = this

            val width = i420Buffer.width
            val height = i420Buffer.height
            val nv21Data = ByteArray(width * height * 3 / 2)

            val dataY = i420Buffer.dataY
            val dataU = i420Buffer.dataU
            val dataV = i420Buffer.dataV

            val strideY = i420Buffer.strideY
            val strideU = i420Buffer.strideU
            val strideV = i420Buffer.strideV


            // 1. Copy Y Plane (respecting stride)
            val yPlaneSize = width * height
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

            // 2. Copy VU Planes (interleaved, respecting strides)
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

            // 3. Now use YuvImage with the correctly formatted NV21 data
            val yuvImage = YuvImage(
                nv21Data,
                ImageFormat.NV21,
                width,
                height,
                null
            )

            // 4. Compress to JPEG and decode
            val outputStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, width, height),
                100,
                outputStream
            )
            val jpegData = outputStream.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)

            i420Buffer.release()

            bitmap
        }.getOrNull()
    }
}
