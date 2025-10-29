/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.ParticipantUiState
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.MediaStream
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun ScreenShareView(
    participantUiState: ParticipantUiState,
    eglBase: EglBase?,
    modifier: Modifier = Modifier,
    onCloseIconClick: () -> Unit
) {
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(5000)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        ScreenShareVideo(
            participantUiState = participantUiState,
            eglBase = eglBase,
            onSingleTap = { controlsVisible = true }
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ScreenShareControls(
                nick = participantUiState.nick.orEmpty(),
                onCloseClick = onCloseIconClick
            )
        }
    }
}

@Composable
private fun ScreenShareVideo(participantUiState: ParticipantUiState, eglBase: EglBase?, onSingleTap: () -> Unit) {
    if (participantUiState.isScreenStreamEnabled && participantUiState.screenMediaStream != null) {
        WebRTCScreenShareView(
            mediaStream = participantUiState.screenMediaStream,
            eglBase = eglBase,
            onSingleTap = onSingleTap
        )
    }
}

@Composable
private fun ScreenShareControls(nick: String, onCloseClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                )
            )
            .padding(top = 8.dp, start = 12.dp, end = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = nick,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            CloseButton(onClick = onCloseClick)
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(4.dp)
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

@Composable
fun WebRTCScreenShareView(mediaStream: MediaStream, eglBase: EglBase?, onSingleTap: () -> Unit) {
    val context = LocalContext.current
    val renderer = remember { SurfaceViewRenderer(context) }
    val videoTrack = remember(mediaStream) { mediaStream.videoTracks.firstOrNull() }

    SetupSurfaceRenderer(renderer, eglBase, videoTrack)

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var videoWidth by remember { mutableFloatStateOf(0f) }
    var videoHeight by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .zoomableVideo(
                scaleState = { scale },
                onScaleChange = { scale = it },
                offsetXState = { offsetX },
                offsetYState = { offsetY },
                onOffsetChange = { x, y ->
                    offsetX = x
                    offsetY = y
                },
                videoWidthState = { videoWidth },
                videoHeightState = { videoHeight },
                onSingleTap = onSingleTap
            ),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { renderer },
            modifier = Modifier
                .wrapContentSize()
                .onGloballyPositioned { coordinates ->
                    videoWidth = coordinates.size.width.toFloat()
                    videoHeight = coordinates.size.height.toFloat()
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

@Composable
private fun SetupSurfaceRenderer(renderer: SurfaceViewRenderer, eglBase: EglBase?, videoTrack: VideoTrack?) {
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
}

fun Modifier.zoomableVideo(
    scaleState: () -> Float,
    onScaleChange: (Float) -> Unit,
    offsetXState: () -> Float,
    offsetYState: () -> Float,
    onOffsetChange: (Float, Float) -> Unit,
    videoWidthState: () -> Float,
    videoHeightState: () -> Float,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    onSingleTap: () -> Unit = {}
): Modifier =
    pointerInput(Unit) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            val prevScale = scaleState()
            val newScale = (prevScale * zoom).coerceIn(minScale, maxScale)

            val focusX = centroid.x - offsetXState() - videoWidthState() / 2
            val focusY = centroid.y - offsetYState() - videoHeightState() / 2

            var offsetX = offsetXState() - focusX * (newScale / prevScale - 1) + pan.x
            var offsetY = offsetYState() - focusY * (newScale / prevScale - 1) + pan.y

            val maxOffsetX = (videoWidthState() * (newScale - 1)) / 2
            val maxOffsetY = (videoHeightState() * (newScale - 1)) / 2
            offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
            offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)

            onScaleChange(newScale)
            onOffsetChange(offsetX, offsetY)
        }
    }.pointerInput(Unit) {
        detectTapGestures(
            onTap = { onSingleTap() },
            onDoubleTap = {
                onScaleChange(1f)
                onOffsetChange(0f, 0f)
            }
        )
    }
