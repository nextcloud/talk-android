/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import android.content.Context
import android.graphics.Outline
import android.graphics.SurfaceTexture
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

    init {
        surfaceTextureListener = this
    }

    fun init(sharedContext: EglBase.Context?) {
        eglRenderer.init(sharedContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
    }

    fun release() {
        eglRenderer.release()
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
        eglRenderer.onFrame(frame)
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
