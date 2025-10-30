/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun SelfVideoView(
    eglBase: EglBase.Context,
    videoTrack: VideoTrack?,
    isFrontCamera: Boolean,
    onSwitchCamera: () -> Unit
) {
    var renderer: SurfaceViewRenderer? = remember { null }

    Box(
        modifier = Modifier
            .size(120.dp, 160.dp)
    ) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    init(eglBase, null)
                    setMirror(isFrontCamera)
                    setZOrderOnTop(true)
                    setEnableHardwareScaler(false)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    renderer = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { it.setMirror(isFrontCamera) },
            onRelease = { view ->
                videoTrack?.removeSink(view)
                view.clearImage()
                view.release()
            }
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onSwitchCamera() })
                }
        )
    }

    DisposableEffect(videoTrack) {
        videoTrack?.addSink(renderer)
        onDispose { videoTrack?.removeSink(renderer) }
    }
}





