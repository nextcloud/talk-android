/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.ParticipantUiState
import org.webrtc.EglBase
import org.webrtc.MediaStream
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun ScreenShareView(
    participantUiState: ParticipantUiState,
    eglBase: EglBase?,
    modifier: Modifier = Modifier,
    onCloseIconClick: () -> Unit?
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        if (participantUiState.isScreenStreamEnabled && participantUiState.screenMediaStream != null) {
            WebRTCScreenShareView(participantUiState.screenMediaStream, eglBase)
        }

        IconButton(
            onClick = { onCloseIconClick() },
            modifier = Modifier
                .padding(12.dp)
                .size(36.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
                tint = Color.White
            )
        }
    }
}

@Composable
fun WebRTCScreenShareView(mediaStream: MediaStream, eglBase: EglBase?) {
    val context = LocalContext.current
    val videoTrack = remember(mediaStream) { mediaStream.videoTracks.firstOrNull() }

    val renderer = remember { SurfaceViewRenderer(context) }

    DisposableEffect(renderer, eglBase, videoTrack) {
        renderer.init(eglBase?.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setMirror(false)
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        videoTrack?.addSink(renderer)

        onDispose {
            videoTrack?.removeSink(renderer)
            renderer.release()
        }
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val minScale = 1f
    val maxScale = 5f

    val modifier = Modifier.pointerInput(Unit) {
        detectTransformGestures(
            onGesture = { centroid, pan, zoom, _ ->
                val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                val scaleFactor = newScale / scale
                scale = newScale

                offsetX = (offsetX + pan.x * scaleFactor)
                offsetY = (offsetY + pan.y * scaleFactor)
            }
        )
    }.pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = {
                scale = 1f
                offsetX = 0f
                offsetY = 0f
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(modifier),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { renderer },
            modifier = Modifier
                .wrapContentSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}
