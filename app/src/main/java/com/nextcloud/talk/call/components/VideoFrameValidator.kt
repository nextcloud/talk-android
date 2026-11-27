/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import android.util.Log
import org.webrtc.VideoFrame
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Filters out video frames that would crash the WebRTC renderer.
 *
 * org.webrtc.VideoFrameDrawer uploads non texture frames plane by plane. Planes whose stride differs from their width
 * (width / 2 for the chroma planes) are copied into a buffer that is only allocated if one of those planes has a non
 * zero size. For frames that are less than 2 pixels wide or high, like the 1x1 frames sent for dial-out (phone)
 * participants, the chroma planes are empty, so the buffer is not allocated but still copied into, which throws
 * "NullPointerException: dst should not be null" on the render thread and kills the app. The same happens for planes
 * with a stride smaller than their width. Such frames have nothing to show anyway, so they are dropped instead.
 */
class VideoFrameValidator(private val rendererName: String) {

    private val isInvalidFrameLogged = AtomicBoolean(false)

    fun isRenderable(frame: VideoFrame): Boolean {
        val buffer = frame.buffer
        if (buffer is VideoFrame.TextureBuffer) {
            return true
        }
        val isValid = buffer.width >= MIN_SIZE &&
            buffer.height >= MIN_SIZE &&
            (buffer !is VideoFrame.I420Buffer || hasValidStrides(buffer))
        if (!isValid && isInvalidFrameLogged.compareAndSet(false, true)) {
            val strides = (buffer as? VideoFrame.I420Buffer)
                ?.let { " strides=${it.strideY}/${it.strideU}/${it.strideV}" }
                .orEmpty()
            Log.w(
                TAG,
                "Dropping frame that can not be rendered in $rendererName: ${buffer.javaClass.name}" +
                    " ${buffer.width}x${buffer.height}$strides"
            )
        }
        return isValid
    }

    private fun hasValidStrides(buffer: VideoFrame.I420Buffer): Boolean {
        val chromaWidth = buffer.width / 2
        return buffer.strideY >= buffer.width &&
            buffer.strideU >= chromaWidth &&
            buffer.strideV >= chromaWidth
    }

    companion object {
        private const val TAG = "VideoFrameValidator"
        private const val MIN_SIZE = 2
    }
}
