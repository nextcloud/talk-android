/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import org.webrtc.EglBase
import org.webrtc.MediaStream
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import kotlin.math.max

/**
 * Capture of the local screen, provided as a stream with a single video track.
 */
interface ScreenCapture {
    val stream: MediaStream

    /**
     * Returns false if the capture could not be started.
     */
    fun start(): Boolean

    /**
     * Stops the capture and releases it, including the stream. Can be called from any thread.
     */
    fun release()
}

/**
 * Captures the whole display through a media projection, independently of the size of the call window (for example,
 * in picture in picture or split screen) and of whether the call is visible.
 *
 * @param permissionResultData the result of the media projection permission dialog
 * @param onStop called when the media projection is stopped by the system or the user
 */
class AndroidScreenCapture(
    private val context: Context,
    eglBase: EglBase,
    factory: PeerConnectionFactory,
    permissionResultData: Intent,
    onStop: () -> Unit
) : ScreenCapture {

    private val capturer = ScreenCapturerAndroid(
        permissionResultData,
        object : MediaProjection.Callback() {
            override fun onStop() {
                onStop()
            }
        }
    )
    private val surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBase.eglBaseContext)
    private val videoSource = factory.createVideoSource(true)

    override val stream: MediaStream = factory.createLocalMediaStream("NCSS").apply {
        addTrack(factory.createVideoTrack("NCs0", videoSource))
    }

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            // unused atm
        }

        override fun onDisplayRemoved(displayId: Int) {
            // unused atm
        }

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                updateCaptureSize()
            }
        }
    }

    // Guards the capturer, as the size is changed from the main thread while it may be released from any thread.
    private val lock = Any()
    private var captureSize: Pair<Int, Int>? = null
    private var isReleased = false

    override fun start(): Boolean {
        val size = getCaptureSize()
        val isStarted = synchronized(lock) {
            !isReleased && startCapture(size)
        }
        if (isStarted) {
            displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        }
        return isStarted
    }

    /**
     * Must be called while holding the lock.
     */
    private fun startCapture(size: Pair<Int, Int>): Boolean {
        captureSize = size
        capturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
        // The media projection may fail to start, for example, if the permission expired or was revoked.
        return try {
            capturer.startCapture(size.first, size.second, FRAME_RATE)
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to start screen capture", e)
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to start screen capture", e)
            false
        }
    }

    override fun release() {
        displayManager.unregisterDisplayListener(displayListener)

        synchronized(lock) {
            if (isReleased) {
                return
            }
            isReleased = true
            if (captureSize != null) {
                try {
                    capturer.stopCapture()
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Failed to stop screen capture", e)
                }
            }
            capturer.dispose()
        }
        videoSource.dispose()
        surfaceTextureHelper.dispose()
        stream.dispose()
    }

    private fun updateCaptureSize() {
        val size = getCaptureSize()
        synchronized(lock) {
            if (isReleased || captureSize == null || size == captureSize) {
                return
            }
            captureSize = size
            capturer.changeCaptureFormat(size.first, size.second, FRAME_RATE)
        }
    }

    private fun getCaptureSize(): Pair<Int, Int> {
        val displayMetrics = DisplayMetrics()
        // The configuration of the activity is not updated while it is stopped, so the display is queried directly.
        @Suppress("DEPRECATION")
        displayManager.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(displayMetrics)
        return scaleCaptureSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    companion object {
        private val TAG = AndroidScreenCapture::class.java.simpleName

        private const val FRAME_RATE = 15
        private const val MAX_DIMENSION = 1920

        /**
         * Returns the given display size in its current rotation scaled down to [MAX_DIMENSION].
         */
        fun scaleCaptureSize(displayWidth: Int, displayHeight: Int): Pair<Int, Int> {
            val scale = max(1f, max(displayWidth, displayHeight).toFloat() / MAX_DIMENSION)
            // Video encoders expect even dimensions.
            val width = (displayWidth / scale).toInt() and 1.inv()
            val height = (displayHeight / scale).toInt() and 1.inv()
            return Pair(width, height)
        }
    }
}
