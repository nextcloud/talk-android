/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val replyThresholdDp = 100.dp
private val swipeLimitDp = 130.dp
private val showIconThresholdDp = 30.dp
private const val HORIZONTAL_DRAG_ANGLE_MULTIPLIER = 1.5f

@Suppress("Detekt.LongMethod")
@Composable
fun SwipeToReplyContainer(replyable: Boolean, onSwipeReply: () -> Unit, content: @Composable () -> Unit) {
    if (!replyable) {
        content()
        return
    }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val replyThresholdPx = with(density) { replyThresholdDp.toPx() }
    val swipeLimitPx = with(density) { swipeLimitDp.toPx() }
    val showIconThresholdPx = with(density) { showIconThresholdDp.toPx() }

    val offsetX = remember { Animatable(0f) }
    var didTriggerReply by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var iconAlpha by remember { mutableFloatStateOf(0f) }
    var iconScale by remember { mutableFloatStateOf(0f) }

    val iconColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var horizontalDragStarted = false

                val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                    val dx = abs(change.position.x - change.previousPosition.x)
                    val dy = abs(change.position.y - change.previousPosition.y)
                    if (dx > dy * HORIZONTAL_DRAG_ANGLE_MULTIPLIER) {
                        change.consume()
                        horizontalDragStarted = true
                    }
                }

                if (dragStart != null && horizontalDragStarted) {
                    dragging = true
                    didTriggerReply = false
                    horizontalDrag(dragStart.id) { change ->
                        val deltaX = change.position.x - change.previousPosition.x
                        val newOffset = (offsetX.value + deltaX).coerceIn(0f, swipeLimitPx)
                        coroutineScope.launch { offsetX.snapTo(newOffset) }

                        val progress = (newOffset / swipeLimitPx).coerceIn(0f, 1f)
                        iconAlpha = if (newOffset > showIconThresholdPx) progress else 0f
                        iconScale = (newOffset / replyThresholdPx).coerceIn(0f, 1f)

                        if (!didTriggerReply && newOffset >= replyThresholdPx) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            didTriggerReply = true
                        }

                        change.consume()
                    }

                    if (didTriggerReply) {
                        onSwipeReply()
                    }

                    dragging = false
                    iconAlpha = 0f
                    iconScale = 0f
                    coroutineScope.launch {
                        offsetX.animateTo(0f, animationSpec = spring())
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
            content()
        }

        if (dragging && iconAlpha > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .size(36.dp)
                    .scale(iconScale)
                    .alpha(iconAlpha)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_reply),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(2.dp),
                    colorFilter = ColorFilter.tint(iconColor)
                )
            }
        }
    }
}
