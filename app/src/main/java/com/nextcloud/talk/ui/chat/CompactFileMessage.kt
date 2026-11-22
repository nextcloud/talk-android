/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.FileParameters
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.ui.theme.mimetypeIconTint
import com.nextcloud.talk.utils.DrawableUtils
import com.nextcloud.talk.utils.Mimetype

private const val UPLOAD_PERCENT_MAX = 100f

internal fun ChatMessageUi.isUploadingFile(): Boolean =
    statusIcon != MessageStatusIcon.SENT && statusIcon != MessageStatusIcon.FAILED

internal fun isPreviewableMimeType(mimeType: String?): Boolean =
    mimeType?.startsWith(Mimetype.IMAGE_PREFIX) == true || mimeType?.startsWith(Mimetype.VIDEO_PREFIX) == true

@Composable
internal fun CompactFileMessage(
    message: ChatMessageUi,
    isOneToOneConversation: Boolean,
    conversationThreadId: Long?,
    caption: String? = message.message.takeIf { message.plainMessage != "{file}" },
    content: @Composable () -> Unit
) {
    MessageScaffold(
        uiMessage = message,
        isOneToOneConversation = isOneToOneConversation,
        conversationThreadId = conversationThreadId,
        captionText = caption,
        forceTimeBelow = true,
        content = content
    )
}

@Composable
internal fun SyncedFileRow(message: ChatMessageUi, downloadingFiles: List<String>, onClick: () -> Unit) {
    val file = remember(message.messageParameters) {
        FileParameters(HashMap(message.messageParameters.mapValues { (_, params) -> HashMap(params) }))
    }
    val icon = DrawableUtils.getDrawableResourceIdForMimeType(file.mimetype)
    val onLongClick = LocalMessageLongClickHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { onLongClick(message.id) })
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = mimetypeIconTint(icon)
        )
        AttachmentDetails(message, Modifier.weight(1f))
        if (file.id in downloadingFiles) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                painter = painterResource(R.drawable.baseline_download_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun UploadingFileRow(
    message: ChatMessageUi,
    typeContent: MessageTypeContent.UploadingMedia,
    onCancelUpload: (String) -> Unit
) {
    val onLongClick = LocalMessageLongClickHandler.current
    val isFailed = message.statusIcon == MessageStatusIcon.FAILED
    val isUploading = message.isUploadingFile()
    val progress = LocalUploadProgressProvider.current(message.referenceId.orEmpty())
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { onLongClick(message.id) })
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(typeContent.drawableResourceId),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = mimetypeIconTint(typeContent.drawableResourceId)
            )
            AttachmentDetails(message, Modifier.weight(1f), fallbackFileName = typeContent.fileName)
            if (message.statusIcon != MessageStatusIcon.SENT) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (isUploading) FileUploadProgress(progress)
                    IconButton(onClick = { onCancelUpload(message.referenceId.orEmpty()) }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.nc_cancel))
                    }
                }
            }
        }
        if (isFailed) {
            Text(
                text = stringResource(R.string.nc_upload_failed_notification_title),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FileUploadProgress(progress: Int?) {
    if (progress == null) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 2.dp)
    } else {
        CircularProgressIndicator(
            progress = { (progress / UPLOAD_PERCENT_MAX).coerceIn(0f, 1f) },
            modifier = Modifier.size(36.dp),
            strokeWidth = 2.dp
        )
    }
}
