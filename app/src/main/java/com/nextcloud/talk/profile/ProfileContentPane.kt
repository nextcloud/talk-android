/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.profile

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.utils.DisplayUtils

@Composable
internal fun ProfileContentPane(
    state: ProfileUiState,
    callbacks: ProfileCallbacks,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    when (val cs = state.contentState) {
        is ProfileContentState.Loading -> ProfileShimmerLoading(modifier = modifier)
        is ProfileContentState.Empty -> ProfileEmptyState(
            headline = cs.headline,
            message = cs.message,
            iconRes = cs.iconRes,
            modifier = modifier
        )

        is ProfileContentState.ShowList -> ProfileItemList(
            state = state,
            callbacks = callbacks,
            modifier = modifier,
            bottomPadding = bottomPadding
        )
    }
}

@Composable
private fun ProfileItemList(
    state: ProfileUiState,
    callbacks: ProfileCallbacks,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    val displayItems = if (state.isEditMode) state.items else state.filteredItems
    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding),
        modifier = modifier
    ) {
        if (state.showProfileEnabledCard) {
            item(key = "profile_enabled_card") {
                ProfileEnabledCard(
                    isEnabled = state.isProfileEnabled,
                    onCheckedChange = callbacks.onProfileEnabledChange,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
                )
            }
        }
        itemsIndexed(displayItems) { index, item ->
            val position = when {
                displayItems.size == 1 -> UserInfoDetailItemPosition.FIRST
                index == 0 -> UserInfoDetailItemPosition.FIRST
                index == displayItems.lastIndex -> UserInfoDetailItemPosition.LAST
                else -> UserInfoDetailItemPosition.MIDDLE
            }
            if (state.isEditMode) {
                UserInfoDetailItemEditable(
                    data = UserInfoDetailItemData(
                        icon = item.icon,
                        text = item.text.orEmpty(),
                        hint = item.hint,
                        scope = item.scope
                    ),
                    listeners = UserInfoDetailListeners(
                        onTextChange = { newText -> callbacks.onTextChange(index, newText) },
                        onScopeClick = { callbacks.onScopeClick(index, item.field) }
                    ),
                    position = position,
                    enabled = state.editableFields.contains(item.field.toString().lowercase()),
                    multiLine = item.field == ProfileActivity.Field.BIOGRAPHY
                )
            } else {
                val displayText = when (item.field) {
                    ProfileActivity.Field.WEBSITE -> DisplayUtils.beautifyURL(item.text)
                    ProfileActivity.Field.TWITTER -> DisplayUtils.beautifyTwitterHandle(item.text)
                    else -> item.text.orEmpty()
                }
                UserInfoDetailItemViewOnly(
                    userInfo = UserInfoDetailItemData(
                        icon = item.icon,
                        text = displayText,
                        hint = item.hint,
                        scope = item.scope
                    ),
                    position = position,
                    ellipsize = item.field == ProfileActivity.Field.EMAIL
                )
            }
        }
    }
}

private const val SHIMMER_PLACEHOLDER_ROWS = 4

@Composable
private fun ProfileShimmerLoading(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha)
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        repeat(SHIMMER_PLACEHOLDER_ROWS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(shimmerColor)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerColor)
                )
            }
        }
    }
}

@Composable
private fun ProfileEmptyState(
    headline: String,
    message: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
