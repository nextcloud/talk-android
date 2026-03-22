/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

private const val SHIMMER_ITEM_COUNT = 4
private const val SHIMMER_ANIM_DURATION_MS = 800
private const val SHIMMER_ALPHA_MIN = 0.2f
private const val TITLE_WIDTH = 0.6f
private const val SUBLINE_WIDTH = 0.9f

private const val SHIMMER_ALPHA_MAX = TITLE_WIDTH

/**
 * Top-level wrapper rendered by the shimmer_compose_view ComposeView.
 * Animates in/out via [AnimatedVisibility] and shows skeleton placeholder rows
 * while the conversation list is loading for the first time.
 */
@Composable
fun ConversationListSkeleton(isVisible: Boolean, itemCount: Int = SHIMMER_ITEM_COUNT) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
        val shimmerAlpha by infiniteTransition.animateFloat(
            initialValue = SHIMMER_ALPHA_MIN,
            targetValue = SHIMMER_ALPHA_MAX,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = SHIMMER_ANIM_DURATION_MS),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmerAlpha"
        )
        val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha)
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            repeat(itemCount) {
                ShimmerConversationItem(shimmerColor = shimmerColor)
            }
        }
    }
}

@Composable
private fun ShimmerConversationItem(shimmerColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(shimmerColor)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(TITLE_WIDTH)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(SUBLINE_WIDTH)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor)
            )
        }
    }
}

@Preview(showBackground = true, name = "Shimmer – visible")
@Preview(showBackground = true, name = "Shimmer – visible (dark)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ShimmerVisibleDarkPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        ConversationListSkeleton(isVisible = true)
    }
}
