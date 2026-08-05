/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R

data class AvatarEditPanelState(
    val selectedEmoji: String? = null,
    val selectedEmojiColor: Int? = null,
    val showDeleteButton: Boolean = false,
    val enabled: Boolean = true
)

data class AvatarEditPanelCallbacks(
    val onCameraClick: () -> Unit = {},
    val onUploadClick: () -> Unit = {},
    val onChooseClick: () -> Unit = {},
    val onEmojiAvatarConfirmed: (emoji: String, color: Int?) -> Unit = { _, _ -> },
    val onDeleteClick: () -> Unit = {}
)

@Composable
fun AvatarEditPanel(state: AvatarEditPanelState, callbacks: AvatarEditPanelCallbacks, modifier: Modifier = Modifier) {
    var showEmojiAvatarPicker by remember { mutableStateOf(false) }

    AvatarActionButtonsRow(
        state = state,
        callbacks = callbacks,
        onEmojiButtonClick = { showEmojiAvatarPicker = true },
        modifier = modifier
    )

    if (showEmojiAvatarPicker) {
        EmojiAvatarPickerBottomSheet(
            initialEmoji = state.selectedEmoji,
            initialColor = state.selectedEmojiColor,
            onConfirm = { emoji, color ->
                callbacks.onEmojiAvatarConfirmed(emoji, color)
                showEmojiAvatarPicker = false
            },
            onDismiss = { showEmojiAvatarPicker = false }
        )
    }
}

@Composable
private fun AvatarActionButtonsRow(
    state: AvatarEditPanelState,
    callbacks: AvatarEditPanelCallbacks,
    onEmojiButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AvatarActionButton(onClick = callbacks.onCameraClick, enabled = state.enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_photo_camera_24),
                contentDescription = stringResource(R.string.set_avatar_from_camera)
            )
        }
        AvatarActionButton(onClick = callbacks.onUploadClick, enabled = state.enabled) {
            Icon(
                painter = painterResource(R.drawable.upload),
                contentDescription = stringResource(R.string.upload_new_avatar_from_device)
            )
        }
        AvatarActionButton(onClick = callbacks.onChooseClick, enabled = state.enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = stringResource(R.string.choose_avatar_from_cloud)
            )
        }
        AvatarActionButton(onClick = onEmojiButtonClick, enabled = state.enabled) {
            Icon(
                imageVector = Icons.Outlined.EmojiEmotions,
                contentDescription = stringResource(R.string.nc_set_emoji_as_conversation_picture)
            )
        }
        if (state.showDeleteButton) {
            AvatarActionButton(onClick = callbacks.onDeleteClick, enabled = state.enabled) {
                Icon(
                    painter = painterResource(R.drawable.trashbin),
                    contentDescription = stringResource(R.string.delete_avatar)
                )
            }
        }
    }
}

@Composable
private fun AvatarActionButton(onClick: () -> Unit, enabled: Boolean, icon: @Composable () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(avatarEditButtonSize),
        shape = avatarEditButtonShape,
        content = icon
    )
}

private val avatarEditButtonSize = 40.dp
private val avatarEditButtonShape = RoundedCornerShape(12.dp)
