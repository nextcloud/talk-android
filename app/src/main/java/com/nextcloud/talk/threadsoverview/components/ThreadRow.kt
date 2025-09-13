/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.threadsoverview.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.ColorGenerator

@Suppress("LongParameterList", "Detekt.LongMethod")
@Composable
fun ThreadRow(
    roomToken: String,
    threadId: Int,
    title: String,
    secondLineTitle: String,
    secondLine: String,
    numReplies: String,
    date: String,
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
        ThreadsIcon(title)

        Spacer(modifier = Modifier.Companion.width(12.dp))

        Column {
            Row {
                Text(
                    modifier = Modifier.Companion.weight(1f),
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Companion.Ellipsis
                )
                Spacer(modifier = Modifier.Companion.width(4.dp))
                Text(
                    text = numReplies,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Spacer(modifier = Modifier.Companion.height(2.dp))

            Row(
                verticalAlignment = Alignment.Companion.CenterVertically
            ) {
                Text(
                    text = secondLineTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Companion.Ellipsis
                )
                Spacer(modifier = Modifier.Companion.width(4.dp))
                Text(
                    modifier = Modifier.Companion.weight(1f),
                    text = secondLine,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
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

@Composable
fun ThreadsIcon(title: String) {
    val baseColorInt = ColorGenerator.usernameToColor(title)

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(baseColorInt).copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.outline_forum_24),
            contentDescription = null,
            tint = Color(baseColorInt).copy(alpha = 0.9f),
            modifier = Modifier.size(32.dp)
        )
    }
}

@Preview
@Composable
fun ThreadRowPreview() {
    ThreadRow(
        roomToken = "1234",
        threadId = 123,
        title = "title1",
        secondLine = "last message",
        secondLineTitle = "Mia:",
        numReplies = "12 replies",
        date = "14 sec ago",
        onClick = null
    )
}

@Preview(
    name = "Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun ThreadRowPreviewDark() {
    ThreadRow(
        roomToken = "1234",
        threadId = 123,
        title = "title2",
        secondLine = "last message",
        secondLineTitle = "Mia:",
        numReplies = "12 replies",
        date = "14 sec ago",
        onClick = null
    )
}

@Preview
@Composable
fun ThreadRowUnreadMessagePreview() {
    ThreadRow(
        roomToken = "1234",
        threadId = 123,
        title = "title3",
        secondLine = "last message",
        secondLineTitle = "Mia:",
        numReplies = "12 replies",
        date = "14 sec ago",
        onClick = null
    )
}

@Preview
@Composable
fun ThreadRowMentionPreview() {
    ThreadRow(
        roomToken = "1234",
        threadId = 123,
        title = "title3",
        secondLine = "last message",
        secondLineTitle = "Mia:",
        numReplies = "12 replies",
        date = "14 sec ago",
        onClick = null
    )
}

@Preview
@Composable
fun ThreadRowDirectMentionPreview() {
    ThreadRow(
        roomToken = "1234",
        threadId = 123,
        title = "title with a verrrrrrrrrrrrrrrrrrrrrrrrry long text",
        secondLine = "title with a verrrrrrrrrrrrrrrrrrrrrrrrry long text",
        secondLineTitle = "Mia:",
        numReplies = "12 replies",
        date = "14 sec ago",
        onClick = null
    )
}
