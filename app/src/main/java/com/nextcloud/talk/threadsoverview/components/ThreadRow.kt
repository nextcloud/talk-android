/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.threadsoverview.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nextcloud.talk.R

@Composable
fun ThreadRow(
    threadId: Int,
    threadName: String,
    threadMessage: String,
    lastActivityDate: String,
    numReplies: Int?,
    unreadMention: Boolean,
    unreadMentionDirect: Boolean,
    imageRequest: ImageRequest?,
    roomToken: String,
    onThreadClick: ((String, Int) -> Unit?)?
) {
    Row(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .clickable(enabled = onThreadClick != null) {
                onThreadClick?.invoke(roomToken, threadId)
            }
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.Companion.CenterVertically
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = stringResource(R.string.user_avatar),
            modifier = Modifier.Companion.size(48.dp)
        )

        Spacer(modifier = Modifier.Companion.width(12.dp))

        Column(modifier = Modifier.Companion.weight(1f)) {
            Text(
                text = threadName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Companion.Ellipsis
            )
            Spacer(modifier = Modifier.Companion.height(2.dp))
            Text(
                text = threadMessage,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Companion.Ellipsis
            )
        }

        Spacer(modifier = Modifier.Companion.width(8.dp))

        Column(horizontalAlignment = Alignment.Companion.End) {
            Text(
                text = lastActivityDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.Companion.height(4.dp))

            if ((numReplies ?: 0) > 0) {
                val isOutlined = unreadMention
                val chipColor = when {
                    unreadMentionDirect -> MaterialTheme.colorScheme.primary
                    unreadMention -> Color.Companion.Transparent
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val chipTextColor = when {
                    unreadMentionDirect -> MaterialTheme.colorScheme.onPrimary
                    unreadMention -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val border = if (isOutlined)
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                else
                    null

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = chipColor,
                    border = border,
                ) {
                    Text(
                        text = numReplies.toString(),
                        modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = chipTextColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.Companion.width(16.dp))
    }
}

@Preview
@Composable
fun ThreadRowPreview() {
    ThreadRow(
        threadId = 123,
        threadName = "actor name aka. thread name",
        threadMessage = "The message of the first message of the thread...",
        numReplies = 0,
        unreadMention = false,
        unreadMentionDirect = false,
        lastActivityDate = "14 sec ago",
        roomToken = "1234",
        onThreadClick = null,
        imageRequest = null
    )
}

@Preview
@Composable
fun ThreadRowUnreadMessagePreview() {
    ThreadRow(
        threadId = 123,
        threadName = "actor name aka. thread name",
        threadMessage = "The message of the first message of the thread...",
        numReplies = 3,
        unreadMention = false,
        unreadMentionDirect = false,
        lastActivityDate = "14 sec ago",
        roomToken = "1234",
        onThreadClick = null,
        imageRequest = null
    )
}

@Preview
@Composable
fun ThreadRowMentionPreview() {
    ThreadRow(
        threadId = 123,
        threadName = "actor name aka. thread name",
        threadMessage = "The message of the first message of the thread...",
        numReplies = 3,
        unreadMention = true,
        unreadMentionDirect = false,
        lastActivityDate = "14 sec ago",
        roomToken = "1234",
        onThreadClick = null,
        imageRequest = null
    )
}

@Preview
@Composable
fun ThreadRowDirectMentionPreview() {
    ThreadRow(
        threadId = 123,
        threadName = "actor name aka. thread name",
        threadMessage = "The message of the first message of the thread...",
        numReplies = 3,
        unreadMention = false,
        unreadMentionDirect = true,
        lastActivityDate = "14 sec ago",
        roomToken = "1234",
        onThreadClick = null,
        imageRequest = null
    )
}
