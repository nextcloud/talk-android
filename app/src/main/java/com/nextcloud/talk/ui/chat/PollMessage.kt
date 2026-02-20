/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent

private const val AUTHOR_TEXT_SIZE = 12

@Composable
fun PollMessage(
    typeContent: MessageTypeContent.Poll,
    message: ChatMessageUi,
    conversationThreadId: Long? = null
) {
    MessageScaffold(
        uiMessage = message,
        conversationThreadId = conversationThreadId,
        content = {
            Column {
                Row(modifier = Modifier.padding(start = 8.dp)) {
                    Icon(painterResource(R.drawable.ic_baseline_bar_chart_24), "")
                    Text(
                        typeContent.pollName,
                        fontSize = AUTHOR_TEXT_SIZE.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButtonNoStyling(stringResource(R.string.message_poll_tap_to_open)) {
                    // NOTE: read only for now
                }
            }
        }
    )
}

@Composable
private fun TextButtonNoStyling(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text,
            fontSize = AUTHOR_TEXT_SIZE.sp,
            color = Color.White
        )
    }
}
