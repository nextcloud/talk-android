/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.camera

import android.content.Context
import android.util.Log
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink

class CameraFrameProcessor(
    val context: Context,
    val isFrontFacing: Boolean
): VideoProcessor, ImageSegmenterHelper.SegmenterListener {

    companion object {
        val TAG: String = this::class.java.simpleName
    }

    private var sink: VideoSink? = null
    private var segmenterHelper: ImageSegmenterHelper? = null

    // SegmentationListener Interface

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "Error $errorCode: $error")
    }

    override fun onResults(resultBundle: ImageSegmenterHelper.ResultBundle) {
        // NOTE- selfie segmentation model returns 0 for background, 1 for foreground, useful for optimizing blur
        //  A single frame can have millions of pixels, I want this to be ran on the GPU
        //  ideally If I can find a built in android native blurring api, else I might have to use openCV which is a
        //  bit overkill.

        // TODO - either renderscript for openGL fragment shader could work here
        //  openGL is better, but renderscript is more maintainable for future devs.
        //  however, openGL is more relevant to my studies, which could be helpful
        //  it avoids the over head of copying the bitmap twice which is costly
        //  as opposed to web which takes advantage of the desktops superior hardware


        // sink?.onFrame(resultBundle.results as VideoFrame)
    }

    // Video Processor Interface

    override fun onCapturerStarted(success: Boolean) {
        segmenterHelper = ImageSegmenterHelper(context = context, imageSegmenterListener = this)
    }

    override fun onCapturerStopped() {
        segmenterHelper?.destroyImageSegmenter()
    }

    override fun onFrameCaptured(frame: VideoFrame) {
        segmenterHelper?.segmentLiveStreamFrame(frame, isFrontFacing)
    }

    override fun setSink(sink: VideoSink?) {
        this.sink = sink
    }
}
