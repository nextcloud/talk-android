/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer

@Composable
fun WebRTCVideoView(mediaStream: MediaStream, eglBase: EglBase?) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglBase?.eglBaseContext, null)
                setEnableHardwareScaler(true)
                setMirror(false)
                mediaStream.videoTracks?.firstOrNull()?.addSink(this)
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = {
            mediaStream.videoTracks?.firstOrNull()?.removeSink(it)
            it.release()
        }
    )
}
