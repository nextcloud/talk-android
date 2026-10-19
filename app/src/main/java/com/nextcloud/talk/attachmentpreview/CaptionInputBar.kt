/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R

internal val ScrimColor = Color.Black.copy(alpha = 0.5f)
private const val SEND_BUTTON_SIZE_DP = 56

/** The caption field floating over the photo, with the "add more files" button at its start. */
@Composable
internal fun CaptionInputBar(
    caption: String,
    onCaptionChange: (String) -> Unit,
    addMoreActions: AddMoreActions,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(dimensionResource(R.dimen.button_corner_radius)))
            .background(ScrimColor)
    ) {
        AddMoreButton(addMoreActions)

        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = dimensionResource(R.dimen.min_size_clickable_area))
                .padding(start = 4.dp, end = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (caption.isEmpty()) {
                Text(
                    text = stringResource(R.string.nc_caption),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            BasicTextField(
                value = caption,
                onValueChange = onCaptionChange,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AddMoreButton(actions: AddMoreActions) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.AddPhotoAlternate,
                contentDescription = stringResource(R.string.nc_attachment_add_menu),
                tint = Color.White
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nc_add_more_files)) },
                onClick = {
                    expanded = false
                    actions.onPickFromGallery()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.take_photo)) },
                onClick = {
                    expanded = false
                    actions.onTakePhoto()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nc_take_video)) },
                onClick = {
                    expanded = false
                    actions.onTakeVideo()
                }
            )
        }
    }
}

/** The round "send" button. */
@Composable
internal fun SendButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val container = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(SEND_BUTTON_SIZE_DP.dp)
            .clip(CircleShape)
            .background(container)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_send_24px),
            contentDescription = stringResource(R.string.nc_description_send_message_button),
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}
