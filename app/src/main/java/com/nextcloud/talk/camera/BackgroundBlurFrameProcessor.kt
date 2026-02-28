/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.camera

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.LruCache
import io.github.crow_misia.libyuv.AbgrBuffer
import io.github.crow_misia.libyuv.I420Buffer
import io.github.crow_misia.libyuv.PlanePrimitive
import org.webrtc.JavaI420Buffer
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink
import org.webrtc.YuvHelper
import java.nio.ByteBuffer

@Suppress("TooGenericExceptionCaught")
class BackgroundBlurFrameProcessor(val context: Context, val surfaceTextureHelper: SurfaceTextureHelper) :
    VideoProcessor,
    ImageSegmenterHelper.SegmenterListener {

    companion object {
        val TAG: String = this::class.java.simpleName
        const val GPU_THREAD: String = "BackgroundBlur"
        const val FLOAT_ROTATION = 180.0f
        const val INT_4 = 4
        const val MAX_NUM_FRAMES = 10
    }

    private var sink: VideoSink? = null
    private var segmenterHelper: ImageSegmenterHelper? = null
    private var backgroundBlurGPUProcessor: BackgroundBlurGPUProcessor? = null

    /* This is to hold meta information between MediaPipe and GPU Render threads, in a thread safe way
    A LRU (least recently used) cache, holds up to MAX_NUM_FRAMES, before evicting the least recently used
    if full. This is used because in case an error occurs with MediaPipe, and a frame is dropped, the frame might stay
    in the map indefinitely, unable to be cleaned up by the garbage collector, therefore causing a memory leak. With the
    LRU Cache, the frame would end up being cleaned eventually as the program runs */
    private var rotationMap = LruCache<Long, Float>(MAX_NUM_FRAMES)
    private val frameBufferMap = LruCache<Long, ByteBuffer>(MAX_NUM_FRAMES)

    // Dedicated Thread for OpenGL Operations
    private var glThread: HandlerThread? = null
    private var glHandler: Handler? = null

    // SegmentationListener Interface

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "Error $errorCode: $error")
    }

    override fun onResults(resultBundle: ImageSegmenterHelper.ResultBundle) {
        val rotation = rotationMap[resultBundle.inferenceTime] ?: 0f
        val frameBuffer = frameBufferMap[resultBundle.inferenceTime]

        // Remove once used to prevent mem leaks
        rotationMap.synchronizedRemove(resultBundle.inferenceTime)
        frameBufferMap.synchronizedRemove(resultBundle.inferenceTime)

        if (frameBuffer == null) {
            Log.e(TAG, "Critical Error in onResults: FrameBufferMap[${resultBundle.inferenceTime}] was null")
            return
        }

        glHandler?.post {
            // This block runs safely on gpu thread
            backgroundBlurGPUProcessor?.let { scaler ->
                try {
                    val drawArray = scaler.process(
                        resultBundle.mask,
                        frameBuffer,
                        resultBundle.width,
                        resultBundle.height,
                        rotation
                    )

                    val webRTCBuffer = drawArray.convertToWebRTCBuffer(resultBundle.width, resultBundle.height)
                    val videoFrame = VideoFrame(webRTCBuffer, 0, resultBundle.inferenceTime)

                    // This should run on the CaptureThread
                    surfaceTextureHelper.handler.post {
                        Log.d(TAG, "Sent VideoFrame to sink on :${Thread.currentThread().name}")
                        sink?.onFrame(videoFrame)

                        // webRTCBuffer usually needs release() if it's not a JavaI420Buffer wrapper that auto-GCs,
                        // but JavaI420Buffer.wrap() relies on GC.
                        videoFrame.release()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing frame on GL Thread", e)
                }
            }
        }
    }

    // Video Processor Interface

    override fun onCapturerStarted(success: Boolean) {
        segmenterHelper = ImageSegmenterHelper(context = context, imageSegmenterListener = this)

        glThread = HandlerThread(GPU_THREAD).apply { start() }
        glHandler = Handler(glThread!!.looper)
        glHandler?.post {
            backgroundBlurGPUProcessor = BackgroundBlurGPUProcessor(context)
            backgroundBlurGPUProcessor?.init()
        }
    }

    override fun onCapturerStopped() {
        segmenterHelper?.destroyImageSegmenter()
        glHandler?.post {
            backgroundBlurGPUProcessor?.release()
            backgroundBlurGPUProcessor = null

            // Quit thread after cleanup
            glThread?.quitSafely()
            glThread = null
            glHandler = null
        }
    }

    override fun onFrameCaptured(videoFrame: VideoFrame) {
        val i420WebRTCBuffer = videoFrame.buffer.toI420()
        val width = videoFrame.buffer.width
        val height = videoFrame.buffer.height
        val rotation = FLOAT_ROTATION - videoFrame.rotation
        val videoFrameBuffer = i420WebRTCBuffer?.convertToABGR()

        i420WebRTCBuffer?.release()

        videoFrameBuffer?.let {
            rotationMap.synchronizedPut(videoFrame.timestampNs, rotation)
            frameBufferMap.synchronizedPut(videoFrame.timestampNs, it)
            segmenterHelper?.segmentFrame(it, width, height, videoFrame.timestampNs)
        } ?: {
            Log.e(TAG, "onFrameCaptured:: Video Frame was null!")
            sink?.onFrame(videoFrame)
        }
    }

    override fun setSink(sink: VideoSink?) {
        this.sink = sink
    }

    fun VideoFrame.I420Buffer.convertToABGR(): ByteBuffer {
        val dataYSize = dataY.limit() - dataY.position()
        val dataUSize = dataU.limit() - dataU.position()
        val dataVSize = dataV.limit() - dataV.position()

        val planeY = PlanePrimitive.create(strideY, dataY, dataYSize)
        val planeU = PlanePrimitive.create(strideU, dataU, dataUSize)
        val planeV = PlanePrimitive.create(strideV, dataV, dataVSize)

        val libYuvI420Buffer = I420Buffer.wrap(planeY, planeU, planeV, width, height)
        val libYuvABGRBuffer = AbgrBuffer.allocate(width, height)
        libYuvI420Buffer.convertTo(libYuvABGRBuffer)

        return libYuvABGRBuffer.asBuffer()
    }

    inline fun <reified K, V> LruCache<K, V>.synchronizedPut(key: K, value: V) {
        synchronized(this) {
            this.put(key, value)
        }
    }

    inline fun <reified K, V> LruCache<K, V>.synchronizedRemove(key: K) {
        synchronized(this) {
            this.remove(key)
        }
    }

    fun ByteArray.convertToWebRTCBuffer(width: Int, height: Int): JavaI420Buffer {
        val src = ByteBuffer.allocateDirect(this.size)
        src.put(this)

        val srcStride = width * INT_4
        val yPlaneSize = width * height
        val uvPlaneSize = (width / 2) * (height / 2)

        val dstYStride = width
        val dstUStride = width / 2
        val dstVStride = width / 2

        val dstYBuffer = ByteBuffer.allocateDirect(yPlaneSize)
        val dstUBuffer = ByteBuffer.allocateDirect(uvPlaneSize)
        val dstVBuffer = ByteBuffer.allocateDirect(uvPlaneSize)

        YuvHelper.ABGRToI420(
            src,
            srcStride,
            dstYBuffer,
            dstYStride,
            dstUBuffer,
            dstUStride,
            dstVBuffer,
            dstVStride,
            width,
            height
        )

        return JavaI420Buffer.wrap(
            width,
            height,
            dstYBuffer,
            dstYStride,
            dstUBuffer,
            dstUStride,
            dstVBuffer,
            dstVStride,
            null
        )
    }
}
