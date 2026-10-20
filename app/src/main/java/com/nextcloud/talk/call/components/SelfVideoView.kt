/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.viewinterop.AndroidView
import com.nextcloud.talk.R
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import kotlin.math.roundToInt

private val selfVideoCornerRadius = 12.dp
private val selfVideoInitialMarginEnd = 20.dp
private val selfVideoInitialOffsetY = 100.dp
private val selfVideoButtonSize = 36.dp
private val selfVideoButtonIconSize = 20.dp
private val selfVideoButtonPadding = 8.dp
private val selfVideoLoadingIndicatorSize = 24.dp
private val selfVideoLoadingStrokeWidth = 2.dp
private const val SELF_VIDEO_BUTTON_BACKGROUND_ALPHA = 0.5f

private val selfVideoRegularSize = DpSize(100.dp, 130.dp)
private val selfVideoEnlargedSize = DpSize(160.dp, 208.dp)
private val selfVideoBorderWidth = 1.dp
private val selfVideoShadowElevation = 8.dp
private const val SELF_VIDEO_BORDER_ALPHA = 0.4f
private const val SELF_VIDEO_LARGE_SCALE = 0.85f
private const val SELF_VIDEO_SCRIM_ALPHA = 0.6f

private enum class SelfVideoSize {
    REGULAR,
    ENLARGED,

    /** Covers most of the available area, centered above a scrim. */
    LARGE
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
    isVideoEnabled: Boolean,
    onSwitchCamera: () -> Unit
) {
    var size by remember { mutableStateOf(SelfVideoSize.REGULAR) }
    val isRegular = size == SelfVideoSize.REGULAR
    val isLarge = size == SelfVideoSize.LARGE

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val tileSize = if (isRegular) selfVideoRegularSize else selfVideoEnlargedSize
        val scale = minOf(1f, maxWidth / tileSize.width, maxHeight / tileSize.height)
        val tileWidth by animateDpAsState(tileSize.width * scale, label = "selfVideoWidth")
        val tileHeight by animateDpAsState(tileSize.height * scale, label = "selfVideoHeight")
        val largeFraction by animateFloatAsState(if (isLarge) 1f else 0f, label = "selfVideoLargeFraction")

        val density = LocalDensity.current
        val maxOffset = with(density) {
            Offset(
                (maxWidth - tileWidth).toPx().coerceAtLeast(0f),
                (maxHeight - tileHeight).toPx().coerceAtLeast(0f)
            )
        }

        val orientation = LocalConfiguration.current.orientation
        var draggedOffset by remember(orientation) { mutableStateOf<Offset?>(null) }
        val offset = draggedOffset ?: density.initialSelfVideoOffset(maxWidth)
        val clampedOffset = Offset(offset.x.coerceIn(0f, maxOffset.x), offset.y.coerceIn(0f, maxOffset.y))
        val largeSize = DpSize(maxWidth * SELF_VIDEO_LARGE_SCALE, maxHeight * SELF_VIDEO_LARGE_SCALE)
        val largeOffset =
            with(density) { Offset((maxWidth - largeSize.width).toPx(), (maxHeight - largeSize.height).toPx()) } / 2f

        if (!isVideoEnabled) return@BoxWithConstraints

        SelfVideoScrim(largeFraction = largeFraction, onTap = { size = SelfVideoSize.ENLARGED })

        Box(
            modifier = Modifier.selfVideoTile(
                offset = lerp(clampedOffset, largeOffset, largeFraction),
                size = lerp(DpSize(tileWidth, tileHeight), largeSize, largeFraction)
            )
        ) {
            SelfVideoRenderer(eglBase, videoTrack, isFrontCamera)

            SelfVideoGestureLayer(
                gestureKey = orientation,
                isRegular = isRegular,
                onDrag = { dragAmount ->
                    if (!isLarge) {
                        draggedOffset = Offset(
                            (clampedOffset.x + dragAmount.x).coerceIn(0f, maxOffset.x),
                            (clampedOffset.y + dragAmount.y).coerceIn(0f, maxOffset.y)
                        )
                    }
                },
                onTap = { size = if (isRegular) SelfVideoSize.ENLARGED else SelfVideoSize.REGULAR }
            )

            SelfVideoControls(
                size = size,
                onSwitchCamera = onSwitchCamera,
                onToggleLarge = { size = if (isLarge) SelfVideoSize.ENLARGED else SelfVideoSize.LARGE }
            )
        }
    }
}

/** Places the regular sized self video in the upper right corner. */
private fun Density.initialSelfVideoOffset(maxWidth: Dp): Offset =
    Offset(
        (maxWidth - selfVideoRegularSize.width - selfVideoInitialMarginEnd).toPx().coerceAtLeast(0f),
        selfVideoInitialOffsetY.toPx()
    )

private fun Modifier.selfVideoTile(offset: Offset, size: DpSize): Modifier {
    val shape = RoundedCornerShape(selfVideoCornerRadius)
    return this
        .absoluteOffset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
        .size(size)
        .shadow(selfVideoShadowElevation, shape)
        .border(selfVideoBorderWidth, Color.White.copy(alpha = SELF_VIDEO_BORDER_ALPHA), shape)
        .clip(shape)
}

/**
 * Dims the views behind the self video while it is shown large. Tapping it shrinks the self video again.
 */
@Composable
private fun SelfVideoScrim(largeFraction: Float, onTap: () -> Unit) {
    if (largeFraction > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SELF_VIDEO_SCRIM_ALPHA * largeFraction))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.nc_call_self_video_shrink),
                    onClick = onTap
                )
        )
    }
}

@Composable
private fun BoxScope.SelfVideoGestureLayer(
    gestureKey: Any,
    isRegular: Boolean,
    onDrag: (Offset) -> Unit,
    onTap: () -> Unit
) {
    val currentOnDrag by rememberUpdatedState(onDrag)

    Box(
        modifier = Modifier
            .matchParentSize()
            .pointerInput(gestureKey) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    currentOnDrag(dragAmount)
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(
                    if (isRegular) R.string.nc_call_self_video_enlarge else R.string.nc_call_self_video_shrink
                ),
                onClick = onTap
            )
    )
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
    var isFirstFrameRendered by remember { mutableStateOf(false) }
    val cornerRadius = with(LocalDensity.current) { selfVideoCornerRadius.toPx() }

    AndroidView(
        factory = { context ->
            TextureViewRenderer(context).apply {
                init(eglBase)
                setCornerRadius(cornerRadius)
                setMirror(isFrontCamera)
                setOnFirstFrameRenderedListener { isFirstFrameRendered = true }
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

    if (!isFirstFrameRendered) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = selfVideoLoadingStrokeWidth,
                modifier = Modifier.size(selfVideoLoadingIndicatorSize)
            )
        }
    }

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
