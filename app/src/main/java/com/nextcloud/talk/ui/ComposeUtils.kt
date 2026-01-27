/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

// Adapted from source - https://stackoverflow.com/a/68056586
@Composable
fun Modifier.customVerticalScrollbar(
    state: ScrollState,
    width: Dp = 8.dp,
    color: Color = Color.Red
): Modifier {
    val targetAlpha = if (state.isScrollInProgress) 1f else 0f
    val duration = if (state.isScrollInProgress) 150 else 500
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration)
    )
    val cr = CORNER_RADIUS.toFloat()

    return drawWithContent {
        drawContent()

        val needDrawScrollbar = state.isScrollInProgress || alpha > 0.0

        if (needDrawScrollbar) {
            val elementHeight = this.size.height
            val pinnedViewHeight = MAX_HEIGHT
            val scrollBarHeightPercentage = (pinnedViewHeight * 100f) / elementHeight
            val scrollBarHeight = (scrollBarHeightPercentage / 100) * pinnedViewHeight
            val offset = state.scrollIndicatorState?.scrollOffset?.toFloat() ?: 0f

            drawRoundRect(
                color = color,
                topLeft = Offset(this.size.width - width.toPx(), min(offset, elementHeight)),
                size = Size(width.toPx(), scrollBarHeight),
                cornerRadius = CornerRadius(cr, cr),
                alpha = alpha
            )
        }
    }
}
