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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R

/** The bottom bar where the user types an optional caption and triggers sending. */
@Composable
internal fun CaptionInputBar(
    caption: String,
    onCaptionChange: (String) -> Unit,
    sendEnabled: Boolean,
    onSend: () -> Unit
) {
    val shape = RoundedCornerShape(dimensionResource(R.dimen.button_corner_radius))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = dimensionResource(R.dimen.min_size_clickable_area))
                .padding(start = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (caption.isEmpty()) {
                Text(
                    text = stringResource(R.string.nc_caption),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = caption,
                onValueChange = onCaptionChange,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }

        val sendTint = if (sendEnabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        IconButton(onClick = onSend, enabled = sendEnabled) {
            Icon(
                painter = painterResource(R.drawable.ic_send_24px),
                contentDescription = stringResource(R.string.nc_description_send_message_button),
                tint = sendTint
            )
        }
    }
}
