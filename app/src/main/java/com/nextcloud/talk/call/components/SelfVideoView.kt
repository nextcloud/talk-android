/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import kotlin.math.roundToInt

private val selfVideoWidth = 100.dp
private val selfVideoHeight = 130.dp
private val selfVideoCornerRadius = 12.dp
private val selfVideoInitialOffsetX = 20.dp
private val selfVideoInitialOffsetY = 100.dp

/**
 * Self video tile that can be dragged within the bounds of its parent. Tapping it switches the camera.
 */
@Composable
fun SelfVideoView(
    eglBase: EglBase.Context,
    videoTrack: VideoTrack?,
    isFrontCamera: Boolean,
    onSwitchCamera: () -> Unit
) {
    val currentOnSwitchCamera by rememberUpdatedState(onSwitchCamera)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxOffsetX = with(density) { (maxWidth - selfVideoWidth).toPx() }.coerceAtLeast(0f)
        val maxOffsetY = with(density) { (maxHeight - selfVideoHeight).toPx() }.coerceAtLeast(0f)

        val orientation = LocalConfiguration.current.orientation
        var offset by remember(orientation) {
            mutableStateOf(
                with(density) { Offset(selfVideoInitialOffsetX.toPx(), selfVideoInitialOffsetY.toPx()) }
            )
        }
        val clampedOffset = Offset(offset.x.coerceIn(0f, maxOffsetX), offset.y.coerceIn(0f, maxOffsetY))

        Box(
            modifier = Modifier
                .offset { IntOffset(clampedOffset.x.roundToInt(), clampedOffset.y.roundToInt()) }
                .size(selfVideoWidth, selfVideoHeight)
                .clip(RoundedCornerShape(selfVideoCornerRadius))
        ) {
            SelfVideoRenderer(eglBase, videoTrack, isFrontCamera)

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(maxOffsetX, maxOffsetY) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offset = Offset(
                                (offset.x + dragAmount.x).coerceIn(0f, maxOffsetX),
                                (offset.y + dragAmount.y).coerceIn(0f, maxOffsetY)
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { currentOnSwitchCamera() })
                    }
            )
        }
    }
}

@Composable
private fun SelfVideoRenderer(eglBase: EglBase.Context, videoTrack: VideoTrack?, isFrontCamera: Boolean) {
    var renderer: TextureViewRenderer? by remember { mutableStateOf(null) }

    AndroidView(
        factory = { context ->
            TextureViewRenderer(context).apply {
                init(eglBase)
                setMirror(isFrontCamera)
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

    DisposableEffect(videoTrack, renderer) {
        val sink = renderer
        if (sink != null) {
            videoTrack?.addSink(sink)
        }
        onDispose {
            if (sink != null) {
                videoTrack?.removeSink(sink)
            }
        }
    }
}
