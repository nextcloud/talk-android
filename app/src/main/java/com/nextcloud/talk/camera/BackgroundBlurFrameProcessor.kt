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

    // Set once the processor is being torn down so evicted frames are released outright
    // instead of being forwarded to a sink/handler that may already be gone.
    @Volatile
    private var released = false

    /**
     * Everything needed to finish a captured frame once its segmentation mask is ready.
     *
     * The original [frame] is retained so that, if the mask never arrives (the frame was
     * dropped or errored by MediaPipe) or GPU processing fails, we can still forward the
     * unprocessed frame to the sink instead of freezing the outgoing video.
     *
     * [abgr] is a buffer we own (a copy, not a view into libyuv-owned native memory), so it
     * stays valid until the GL thread consumes it and is reclaimed by GC afterwards.
     */
    private class PendingFrame(
        val frame: VideoFrame,
        val abgr: ByteBuffer,
        val rotation: Float,
        val timestampNs: Long
    )

    /*
     Correlates MediaPipe segmentation results back to the frame they belong to.

     Keyed by the frame timestamp in MILLISECONDS - the unit MediaPipe expects and echoes
     back via ImageSegmenterResult.timestampMs(), which is what onResults() looks up by.
     (The capture timestamp is nanoseconds, so it must be converted before it is used as a
     key, otherwise the stored key never matches the echoed result and every frame is
     dropped - a frozen stream.)

     An LruCache bounds memory: if a frame's result never arrives, its entry is eventually
     evicted. On eviction we forward the original frame unprocessed so the stream keeps
     flowing, and release it so neither the frame nor its buffers leak.
     */
    private val pendingFrames = object : LruCache<Long, PendingFrame>(MAX_NUM_FRAMES) {
        override fun entryRemoved(
            evicted: Boolean,
            key: Long,
            oldValue: PendingFrame,
            newValue: PendingFrame?
        ) {
            if (released) {
                // Teardown: just drop the retained frame, don't touch the sink/handler.
                oldValue.frame.release()
                return
            }
            // evicted == true   -> pushed out by newer frames (its result never came)
            // newValue != null  -> replaced by a frame with the same ms timestamp
            // Either way this frame will never be processed, so forward it unprocessed.
            // An explicit remove() in onResults passes evicted == false / newValue == null
            // and owns the frame's lifecycle itself, so it must not be forwarded here.
            if (evicted || newValue != null) {
                forwardOriginal(oldValue)
            }
        }
    }

    // Dedicated Thread for OpenGL Operations
    private var glThread: HandlerThread? = null
    private var glHandler: Handler? = null

    // SegmentationListener Interface

    override fun onError(error: String, errorCode: Int) {
        // A per-frame segmentation error just means no mask will arrive for that frame; it
        // stays parked until the LruCache evicts it and forwards it unprocessed, so a burst
        // of errors degrades to passthrough rather than a frozen stream.
        Log.e(TAG, "Segmentation error $errorCode: $error")
    }

    override fun onResults(resultBundle: ImageSegmenterHelper.ResultBundle) {
        // Reclaim the frame this result belongs to. remove() returns null if the frame was
        // already evicted (and therefore already forwarded unprocessed), leaving nothing to do.
        val pending = synchronized(pendingFrames) { pendingFrames.remove(resultBundle.inferenceTime) }
        if (pending == null) {
            Log.w(TAG, "onResults:: no pending frame for timestamp ${resultBundle.inferenceTime}")
            return
        }

        val gpu = backgroundBlurGPUProcessor
        val handler = glHandler
        if (gpu == null || handler == null) {
            // GL pipeline already torn down; forward the frame rather than drop it.
            forwardOriginal(pending)
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
                val videoFrame = VideoFrame(webRTCBuffer, 0, pending.timestampNs)

                // This should run on the CaptureThread
                surfaceTextureHelper.handler.post {
                    sink?.onFrame(videoFrame)
                    videoFrame.release()
                    pending.frame.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame on GL Thread, forwarding original", e)
                forwardOriginal(pending)
            }
        }
    }

    // Forwards the unprocessed original frame to the sink, then releases it. Delivered on the
    // capture thread to match the normal frame-delivery path.
    private fun forwardOriginal(pending: PendingFrame) {
        surfaceTextureHelper.handler.post {
            sink?.onFrame(pending.frame)
            pending.frame.release()
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
        released = true
        segmenterHelper?.destroyImageSegmenter()
        segmenterHelper = null

        // Release any frames still waiting on a segmentation result.
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
            // Segmenter unavailable or failed to initialize (e.g. the model could not load): pass
            // the frame straight through. Parking/retaining frames here would hold the camera's
            // texture buffers waiting for results that never come, starving the capture pool and
            // freezing the camera - so this path must never park a frame.
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

        // MediaPipe expects monotonically increasing timestamps in MILLISECONDS and echoes
        // the same value back on the result; we key by it to pair the mask with this frame.
        val timestampMs = TimeUnit.NANOSECONDS.toMillis(videoFrame.timestampNs)

        // Retain so the frame survives until we either emit a blurred copy or forward it.
        videoFrame.retain()
        synchronized(pendingFrames) {
            pendingFrames.put(
                timestampMs,
                PendingFrame(videoFrame, videoFrameBuffer, rotation, videoFrame.timestampNs)
            )
        }

        try {
            // Hand MediaPipe its own cursor so reading the buffer for segmentation can't move
            // the parked buffer's position before the GL thread uploads it as a texture.
            helper.segmentFrame(videoFrameBuffer.duplicate(), width, height, timestampMs)
        } catch (e: Exception) {
            // segmentFrame is documented to throw (e.g. non-monotonic timestamps). Don't let it
            // crash the capture thread: reclaim the frame we just parked and forward it unprocessed.
            Log.e(TAG, "segmentFrame failed, forwarding original", e)
            val orphan = synchronized(pendingFrames) { pendingFrames.remove(timestampMs) }
            orphan?.let { forwardOriginal(it) }
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

        // asBuffer() is only a view into native memory owned by libYuvABGRBuffer. Parking that
        // view while the AbgrBuffer goes out of scope risks the GL thread reading freed memory,
        // and never closing the AbgrBuffer leaks native memory every frame. So copy into a
        // buffer we own and close the native one immediately.
        // libYuvI420Buffer only wraps the WebRTC planes (released by the caller), so it is not
        // closed here to avoid double-freeing them.
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
