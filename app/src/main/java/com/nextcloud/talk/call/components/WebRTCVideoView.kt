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
import com.nextcloud.talk.adapters.ParticipantUiState
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

@Composable
fun WebRTCVideoView(participant: ParticipantUiState, eglBase: EglBase?) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglBase?.eglBaseContext, null)
                setEnableHardwareScaler(true)
                setMirror(false)
                participant.mediaStream?.videoTracks?.firstOrNull()?.addSink(this)
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = {
            participant.mediaStream?.videoTracks?.firstOrNull()?.removeSink(it)
            it.release()
        }
    )
}
