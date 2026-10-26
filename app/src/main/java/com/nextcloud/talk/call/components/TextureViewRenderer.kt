/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import android.content.Context
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.ThreadUtils
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Renders a WebRTC video track into a [TextureView].
 *
 * Unlike [org.webrtc.SurfaceViewRenderer] it is composed like a regular view, so it can be clipped
 * (e.g. rounded corners), moved and animated. The frame is cropped to fill the view.
 */
class TextureViewRenderer(context: Context) :
    TextureView(context),
    TextureView.SurfaceTextureListener,
    VideoSink {

    private val eglRenderer = EglRenderer(TAG)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isFirstFrameRendered = AtomicBoolean(false)
    private var onFirstFrameRendered: (() -> Unit)? = null
    private var onFrameAspectRatioChanged: ((Float) -> Unit)? = null
    private var frameAspectRatio = 0f

    private val nextFrameLock = Any()
    private var onNextFrameRendered: (() -> Unit)? = null
    private var isNextFrameReceived = false

    init {
        surfaceTextureListener = this
        eglRenderer.addRenderListener {
            if (isFirstFrameRendered.compareAndSet(false, true)) {
                mainHandler.post { onFirstFrameRendered?.invoke() }
            }
            val callback = synchronized(nextFrameLock) {
                if (isNextFrameReceived) onNextFrameRendered.also { onNextFrameRendered = null } else null
            }
            callback?.invoke()
        }
    }

    fun init(sharedContext: EglBase.Context?) {
        eglRenderer.init(sharedContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
    }

    fun release() {
        onFirstFrameRendered = null
        onFrameAspectRatioChanged = null
        mainHandler.removeCallbacksAndMessages(null)
        eglRenderer.release()
    }

    /**
     * Sets a listener that is called on the main thread once the first frame was rendered.
     */
    fun setOnFirstFrameRenderedListener(listener: () -> Unit) {
        onFirstFrameRendered = listener
    }

    /**
     * Sets a listener that is called on the main thread with the aspect ratio (width / height) of the received frames,
     * taking their rotation into account, whenever it changes.
     */
    fun setOnFrameAspectRatioChangedListener(listener: (Float) -> Unit) {
        onFrameAspectRatioChanged = listener
    }

    /**
     * Calls [callback] once the first frame received after this call was rendered. The callback is called on the
     * render thread.
     */
    fun notifyOnNextFrameRendered(callback: () -> Unit) {
        synchronized(nextFrameLock) {
            onNextFrameRendered = callback
            isNextFrameReceived = false
        }
    }

    fun setMirror(mirror: Boolean) {
        eglRenderer.setMirror(mirror)
    }

    fun setCornerRadius(radius: Float) {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        clipToOutline = radius > 0
    }

    fun clearImage() {
        eglRenderer.clearImage()
    }

    override fun onFrame(frame: VideoFrame) {
        if (frame.rotatedHeight > 0) {
            val aspectRatio = frame.rotatedWidth / frame.rotatedHeight.toFloat()
            if (aspectRatio != frameAspectRatio) {
                frameAspectRatio = aspectRatio
                mainHandler.post { onFrameAspectRatioChanged?.invoke(aspectRatio) }
            }
        }
        synchronized(nextFrameLock) {
            if (onNextFrameRendered != null) {
                isNextFrameReceived = true
            }
        }
        eglRenderer.onFrame(frame)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            eglRenderer.disableFpsReduction()
        } else {
            eglRenderer.pauseVideo()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val height = bottom - top
        if (height > 0) {
            eglRenderer.setLayoutAspectRatio((right - left) / height.toFloat())
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        eglRenderer.createEglSurface(surface)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        val latch = CountDownLatch(1)
        eglRenderer.releaseEglSurface { latch.countDown() }
        ThreadUtils.awaitUninterruptibly(latch)
        return true
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    companion object {
        private const val TAG = "TextureViewRenderer"
    }
}
