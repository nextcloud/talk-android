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
import java.util.concurrent.TimeUnit

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

    /**
     * Data needed to finish a captured frame once its segmentation mask is ready.
     *
     * Deliberately does NOT hold the original camera VideoFrame. That frame is texture-backed
     * and owned by the capturer/SurfaceTextureHelper; retaining it across the async segmentation
     * boundary and releasing it later races with capturer teardown (toggling blur off), causing
     * "release() called on an object with refcount < 1". We only keep [abgr] - a copy we own -
     * and emit a brand-new frame from it.
     */
    private class PendingFrame(
        val abgr: ByteBuffer,
        val rotation: Float,
        val timestampNs: Long
    )

    /*
     Correlates MediaPipe segmentation results back to the frame they belong to.

     Keyed by the frame timestamp in MILLISECONDS - the unit MediaPipe expects and echoes back via
     ImageSegmenterResult.timestampMs(), which onResults() looks up by. An LruCache bounds memory:
     if a frame's result never arrives (dropped/errored by MediaPipe) its entry is simply evicted.
     Values are plain owned buffers, so eviction needs no special cleanup.
     */
    private val pendingFrames = LruCache<Long, PendingFrame>(MAX_NUM_FRAMES)

    // Dedicated Thread for OpenGL Operations
    private var glThread: HandlerThread? = null
    private var glHandler: Handler? = null

    // SegmentationListener Interface

    override fun onError(error: String, errorCode: Int) {
        // A per-frame segmentation error just means no mask will arrive for that frame; its entry
        // is evicted by the LruCache and the frame is skipped. Subsequent frames keep flowing.
        Log.e(TAG, "Segmentation error $errorCode: $error")
    }

    override fun onResults(resultBundle: ImageSegmenterHelper.ResultBundle) {
        val pending = synchronized(pendingFrames) { pendingFrames.remove(resultBundle.inferenceTime) }
        if (pending == null) {
            Log.w(TAG, "onResults:: no pending frame for timestamp ${resultBundle.inferenceTime}")
            return
        }

        val gpu = backgroundBlurGPUProcessor
        val handler = glHandler
        if (gpu == null || handler == null) {
            // GL pipeline already torn down (blur toggled off); skip this frame.
            return
        }

        handler.post {
            // This block runs safely on the gpu thread
            try {
                val drawArray = gpu.process(
                    resultBundle.mask,
                    pending.abgr,
                    resultBundle.width,
                    resultBundle.height,
                    pending.rotation
                )

                val webRTCBuffer = drawArray.convertToWebRTCBuffer(resultBundle.width, resultBundle.height)
                // A new frame we own; released after it is delivered. The original camera frame is
                // never retained or released here.
                val videoFrame = VideoFrame(webRTCBuffer, 0, pending.timestampNs)

                // This should run on the CaptureThread
                surfaceTextureHelper.handler.post {
                    sink?.onFrame(videoFrame)
                    videoFrame.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame on GL Thread", e)
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
        segmenterHelper = null

        // Drop any pending ABGR buffers; nothing to release, they are GC-managed.
        synchronized(pendingFrames) { pendingFrames.evictAll() }

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
        val helper = segmenterHelper
        if (helper == null || !helper.isReady()) {
            // Segmenter unavailable or failed to initialize: pass the frame straight through within
            // this callback (no retain) so enabling blur never freezes the outgoing video.
            sink?.onFrame(videoFrame)
            return
        }

        val i420WebRTCBuffer = videoFrame.buffer.toI420()
        val width = videoFrame.buffer.width
        val height = videoFrame.buffer.height
        val rotation = FLOAT_ROTATION - videoFrame.rotation
        val videoFrameBuffer = i420WebRTCBuffer?.convertToABGR()

        i420WebRTCBuffer?.release()

        if (videoFrameBuffer == null) {
            Log.e(TAG, "onFrameCaptured:: could not convert frame; forwarding original")
            sink?.onFrame(videoFrame)
            return
        }

        // MediaPipe expects monotonically increasing timestamps in MILLISECONDS and echoes the same
        // value back on the result; we key by it to pair the mask with this frame.
        val timestampMs = TimeUnit.NANOSECONDS.toMillis(videoFrame.timestampNs)
        synchronized(pendingFrames) {
            pendingFrames.put(timestampMs, PendingFrame(videoFrameBuffer, rotation, videoFrame.timestampNs))
        }

        try {
            // Hand MediaPipe its own cursor so reading the buffer for segmentation can't move the
            // parked buffer's position before the GL thread uploads it as a texture.
            helper.segmentFrame(videoFrameBuffer.duplicate(), width, height, timestampMs)
        } catch (e: Exception) {
            // segmentFrame is documented to throw (e.g. non-monotonic timestamps). Don't let it
            // crash the capture thread; just drop the frame we parked.
            Log.e(TAG, "segmentFrame failed", e)
            synchronized(pendingFrames) { pendingFrames.remove(timestampMs) }
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

        // asBuffer() is only a view into native memory owned by libYuvABGRBuffer. Parking that view
        // while the AbgrBuffer goes out of scope risks the GL thread reading freed memory, and never
        // closing the AbgrBuffer leaks native memory every frame. So copy into a buffer we own and
        // close the native one immediately. libYuvI420Buffer only wraps the WebRTC planes (released
        // by the caller), so it is not closed here to avoid double-freeing them.
        val view = libYuvABGRBuffer.asBuffer()
        val owned = ByteBuffer.allocateDirect(view.remaining())
        owned.put(view)
        owned.rewind()
        libYuvABGRBuffer.close()

        return owned
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
