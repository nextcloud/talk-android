/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.profile

import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.DisplayUtils

@Composable
internal fun AvatarSection(
    state: ProfileUiState,
    callbacks: ProfileCallbacks,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    if (isLandscape) {
        AvatarSectionLandscape(state, callbacks, modifier)
    } else {
        AvatarSectionPortrait(state, callbacks, modifier)
    }
}

@Composable
private fun AvatarImage(state: ProfileUiState, avatarSize: Dp) {
    key(state.currentUser?.userId, state.avatarRefreshKey) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    transitionName = "userAvatar.transitionTag"
                    contentDescription = ctx.getString(R.string.avatar)
                }.also { imageView ->
                    DisplayUtils.loadAvatarImage(state.currentUser, imageView, state.avatarRefreshKey > 0)
                }
            },
            modifier = Modifier.size(avatarSize).clip(CircleShape)
        )
    }
}

@Composable
private fun AvatarSectionLandscape(state: ProfileUiState, callbacks: ProfileCallbacks, modifier: Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            AvatarImage(state, 72.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                if (state.displayName.isNotEmpty()) {
                    Text(
                        text = state.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.baseUrl.isNotEmpty()) {
                    Text(
                        text = state.baseUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        if (state.showAvatarButtons) {
            AvatarButtonsRow(callbacks = callbacks, modifier = Modifier.padding(top = 8.dp, start = 40.dp))
        }
        if (state.showProfileEnabledCard) {
            ProfileEnabledCard(
                isEnabled = state.isProfileEnabled,
                onCheckedChange = callbacks.onProfileEnabledChange,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun AvatarSectionPortrait(state: ProfileUiState, callbacks: ProfileCallbacks, modifier: Modifier) {
    Column(modifier = modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AvatarImage(state, 96.dp)
        if (state.displayName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (state.baseUrl.isNotEmpty()) {
            Text(
                text = state.baseUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        if (state.showAvatarButtons) {
            AvatarButtonsRow(callbacks = callbacks, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        }
        if (state.showProfileEnabledCard) {
            ProfileEnabledCard(
                isEnabled = state.isProfileEnabled,
                onCheckedChange = callbacks.onProfileEnabledChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun AvatarButtonsRow(callbacks: ProfileCallbacks, modifier: Modifier = Modifier) {
    val buttonShape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalIconButton(
            onClick = callbacks.onAvatarUploadClick,
            modifier = Modifier.size(40.dp),
            shape = buttonShape
        ) {
            Icon(
                painter = painterResource(R.drawable.upload),
                contentDescription = stringResource(R.string.upload_new_avatar_from_device)
            )
        }
        FilledTonalIconButton(
            onClick = callbacks.onAvatarChooseClick,
            modifier = Modifier.size(40.dp),
            shape = buttonShape
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = stringResource(R.string.choose_avatar_from_cloud)
            )
        }
        FilledTonalIconButton(
            onClick = callbacks.onAvatarCameraClick,
            modifier = Modifier.size(40.dp),
            shape = buttonShape
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_photo_camera_24),
                contentDescription = stringResource(R.string.set_avatar_from_camera)
            )
        }
        FilledTonalIconButton(
            onClick = callbacks.onAvatarDeleteClick,
            modifier = Modifier.size(40.dp),
            shape = buttonShape
        ) {
            Icon(
                painter = painterResource(R.drawable.trashbin),
                contentDescription = stringResource(R.string.delete_avatar)
            )
        }
    }
}
