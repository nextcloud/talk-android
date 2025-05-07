/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nextcloud.talk.call.CallParticipantModel
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

@Composable
fun ScreenshareOverlay(
    modifier: Modifier = Modifier,
    eglBase: EglBase?,
    callParticipantModel: CallParticipantModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val surfaceViewRef = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            surfaceViewRef.value?.let { renderer ->
                callParticipantModel.screenMediaStream?.videoTracks?.firstOrNull()?.removeSink(renderer)
                renderer.clearImage()
                renderer.release()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                SurfaceViewRenderer(context).apply {
                    init(eglBase?.eglBaseContext, null)
                    setZOrderMediaOverlay(true)
                    setEnableHardwareScaler(true)
                    setMirror(false)
                    callParticipantModel.screenMediaStream?.videoTracks?.firstOrNull()?.addSink(this)
                    surfaceViewRef.value = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable {
                    surfaceViewRef.value?.let { renderer ->
                        callParticipantModel.screenMediaStream?.videoTracks?.firstOrNull()?.removeSink(renderer)
                        renderer.clearImage()
                        renderer.release()
                        surfaceViewRef.value = null
                    }
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Text("X", color = Color.White, fontSize = 16.sp)
        }
    }
}

// @Preview
// @Composable
// fun ParticipantGridPreview() {
//     ParticipantGrid(
//         participants = getTestParticipants(1),
//         eglBase = null,
//         isVoiceOnlyCall = false
//     ) {}
// }
