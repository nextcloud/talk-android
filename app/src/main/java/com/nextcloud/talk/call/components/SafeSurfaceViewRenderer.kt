/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import android.content.Context
import android.util.AttributeSet
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame

/**
 * [SurfaceViewRenderer] that drops frames which would crash the renderer, see [VideoFrameValidator].
 */
class SafeSurfaceViewRenderer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SurfaceViewRenderer(context, attrs) {

    private val frameValidator = VideoFrameValidator(TAG)

    override fun onFrame(frame: VideoFrame) {
        if (frameValidator.isRenderable(frame)) {
            super.onFrame(frame)
        }
    }

    companion object {
        private const val TAG = "SafeSurfaceViewRenderer"
    }
}
