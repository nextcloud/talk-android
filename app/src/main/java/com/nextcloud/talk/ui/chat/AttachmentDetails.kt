/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.chat

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.FileParameters
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import java.util.Locale

@Composable
fun AttachmentDetails(message: ChatMessageUi, modifier: Modifier = Modifier, fallbackFileName: String? = null) {
    val file = remember(message.messageParameters) {
        FileParameters(HashMap(message.messageParameters.mapValues { (_, params) -> HashMap(params) }))
    }
    val name = file.name.ifBlank { fallbackFileName.orEmpty() }
    val extension = name.substringAfterLast('.', "")
        .takeIf { name.lastIndexOf('.') > 0 && it.isNotBlank() }
        ?.uppercase(Locale.ROOT)
    val size = file.size?.takeIf { it >= 0 }?.let { Formatter.formatShortFileSize(LocalContext.current, it) }
        ?: stringResource(R.string.nc_attachment_size_unknown)
    val details = listOfNotNull(extension, size).joinToString(" · ")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = name.ifBlank { stringResource(R.string.shared_items_file) },
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = details,
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
