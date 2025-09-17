/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.camera

import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink

class DefaultFrameProcessor: VideoProcessor {
    private var sink: VideoSink? = null

    override fun setSink(sink: VideoSink?) {
        this.sink = sink
    }

    override fun onFrameCaptured(p0: VideoFrame?) {
        p0?.let { sink?.onFrame(it) }
    }

    override fun onCapturerStarted(p0: Boolean) {}
    override fun onCapturerStopped() {}
}
