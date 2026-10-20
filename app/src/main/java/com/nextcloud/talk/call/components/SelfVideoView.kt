/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nextcloud.talk.R
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import kotlin.math.roundToInt

private val selfVideoCornerRadius = 12.dp
private val selfVideoInitialOffsetX = 20.dp
private val selfVideoInitialOffsetY = 100.dp
private val selfVideoButtonSize = 36.dp
private val selfVideoButtonIconSize = 20.dp
private val selfVideoButtonPadding = 8.dp
private const val SELF_VIDEO_BUTTON_BACKGROUND_ALPHA = 0.5f

private enum class SelfVideoSize(val width: Dp, val height: Dp) {
    REGULAR(100.dp, 130.dp),
    ENLARGED(160.dp, 208.dp),
    LARGE(240.dp, 312.dp)
}

/**
 * Self video tile that can be dragged within the bounds of its parent.
 *
 * Tapping the tile toggles between the regular and an enlarged size. The enlarged tile shows buttons to switch the
 * camera and to enlarge the tile even further.
 */
@Composable
fun SelfVideoView(
    eglBase: EglBase.Context,
    videoTrack: VideoTrack?,
    isFrontCamera: Boolean,
    onSwitchCamera: () -> Unit
) {
    var size by remember { mutableStateOf(SelfVideoSize.REGULAR) }
    val isRegular = size == SelfVideoSize.REGULAR
    val toggleLabel =
        stringResource(if (isRegular) R.string.nc_call_self_video_enlarge else R.string.nc_call_self_video_shrink)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = minOf(1f, maxWidth / size.width, maxHeight / size.height)
        val width by animateDpAsState(size.width * scale, label = "selfVideoWidth")
        val height by animateDpAsState(size.height * scale, label = "selfVideoHeight")

        val density = LocalDensity.current
        val maxOffsetX = with(density) { (maxWidth - width).toPx() }.coerceAtLeast(0f)
        val maxOffsetY = with(density) { (maxHeight - height).toPx() }.coerceAtLeast(0f)
        val currentMaxOffset by rememberUpdatedState(Offset(maxOffsetX, maxOffsetY))

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
                .size(width, height)
                .clip(RoundedCornerShape(selfVideoCornerRadius))
        ) {
            SelfVideoRenderer(eglBase, videoTrack, isFrontCamera)

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(orientation) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val max = currentMaxOffset
                            offset = Offset(
                                (offset.x.coerceIn(0f, max.x) + dragAmount.x).coerceIn(0f, max.x),
                                (offset.y.coerceIn(0f, max.y) + dragAmount.y).coerceIn(0f, max.y)
                            )
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = toggleLabel
                    ) {
                        size = if (isRegular) SelfVideoSize.ENLARGED else SelfVideoSize.REGULAR
                    }
            )

            SelfVideoControls(
                size = size,
                onSwitchCamera = onSwitchCamera,
                onToggleLarge = {
                    size = if (size == SelfVideoSize.LARGE) SelfVideoSize.ENLARGED else SelfVideoSize.LARGE
                }
            )
        }
    }
}

@Composable
private fun BoxScope.SelfVideoControls(size: SelfVideoSize, onSwitchCamera: () -> Unit, onToggleLarge: () -> Unit) {
    AnimatedVisibility(
        visible = size != SelfVideoSize.REGULAR,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SelfVideoButton(
                icon = if (size == SelfVideoSize.LARGE) Icons.Filled.CloseFullscreen else Icons.Filled.OpenInFull,
                contentDescription = stringResource(
                    if (size == SelfVideoSize.LARGE) {
                        R.string.nc_call_self_video_shrink
                    } else {
                        R.string.nc_call_self_video_enlarge
                    }
                ),
                onClick = onToggleLarge,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            SelfVideoButton(
                icon = Icons.Filled.Cameraswitch,
                contentDescription = stringResource(R.string.nc_call_button_content_description_switch_camera),
                onClick = onSwitchCamera,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SelfVideoButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(selfVideoButtonPadding)
            .size(selfVideoButtonSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = SELF_VIDEO_BUTTON_BACKGROUND_ALPHA))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(selfVideoButtonIconSize)
        )
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
