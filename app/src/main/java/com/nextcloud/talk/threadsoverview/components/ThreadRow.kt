/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.threadsoverview.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nextcloud.talk.R

@Suppress("LongParameterList", "Detekt.LongMethod")
@Composable
fun ThreadRow(
    roomToken: String,
    threadId: Int,
    firstLineTitle: String,
    firstLine: String,
    secondLineTitle: String,
    secondLine: String,
    numReplies: String,
    date: String,
    imageRequest: ImageRequest?,
    onClick: ((String, Int) -> Unit?)?
) {
    Row(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .clickable(enabled = onClick != null) {
                onClick?.invoke(roomToken, threadId)
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

        Column {
            Row {
                Text(
                    text = firstLineTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Companion.Ellipsis
                )
                Spacer(modifier = Modifier.Companion.width(4.dp))
                Text(
                    modifier = Modifier.Companion.weight(1f),
                    text = firstLine,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Thin,
                    maxLines = 1,
                    overflow = TextOverflow.Companion.Ellipsis
                )
                Spacer(modifier = Modifier.Companion.width(4.dp))
                Text(
                    text = numReplies,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.Companion.height(2.dp))

            Row(
                verticalAlignment = Alignment.Companion.CenterVertically
            ) {
                Text(
                    text = secondLineTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Companion.Ellipsis
                )
                Spacer(modifier = Modifier.Companion.width(4.dp))
                Text(
                    modifier = Modifier.Companion.weight(1f),
                    text = secondLine,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Thin,
                    maxLines = 1,
                    overflow = TextOverflow.Companion.Ellipsis
                )
                Spacer(modifier = Modifier.Companion.width(4.dp))
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.Companion.width(16.dp))
    }
}

@Preview
@Composable
fun ThreadRowPreview() {
    ThreadRow(
        roomToken = "1234",
        threadId = 123,
        firstLine = "first message",
        secondLine = "last message",
        firstLineTitle = "Marsellus",
        secondLineTitle = "Mia:",
        numReplies = "12 replies",
        date = "14 sec ago",
        onClick = null,
        imageRequest = null
    )
}

@Preview
@Composable
fun ThreadRowUnreadMessagePreview() {
    ThreadRow(
        roomToken = "1234",
        threadId = 123,
        firstLine = "first message",
        secondLine = "last message",
        firstLineTitle = "Marsellus",
        secondLineTitle = "Mia:",
        numReplies = "12 replies",
        date = "14 sec ago",
        onClick = null,
        imageRequest = null
    )
}

@Preview
@Composable
fun ThreadRowMentionPreview() {
    ThreadRow(
        roomToken = "1234",
        threadId = 123,
        firstLine = "first message",
        secondLine = "last message",
        firstLineTitle = "Marsellus",
        secondLineTitle = "Mia:",
        numReplies = "12 replies",
        date = "14 sec ago",
        onClick = null,
        imageRequest = null
    )
}

@Preview
@Composable
fun ThreadRowDirectMentionPreview() {
    ThreadRow(
        roomToken = "1234",
        threadId = 123,
        firstLine = "first message with a verrrrrrrrrrrrrrrrrrrrrrrrry long text",
        secondLine = "last message with a verrrrrrrrrrrrrrrrrrrrrrrrry long text",
        firstLineTitle = "Marsellus",
        secondLineTitle = "Mia:",
        numReplies = "12 replies",
        date = "14 sec ago",
        onClick = null,
        imageRequest = null
    )
}
