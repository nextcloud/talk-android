/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent

private val HEADER_TEXT_SIZE = 16.sp
private val HEADER_ICON_SIZE = 18.dp

@Composable
fun PollMessage(
    typeContent: MessageTypeContent.Poll,
    message: ChatMessageUi,
    isOneToOneConversation: Boolean = false,
    conversationThreadId: Long? = null,
    onPollClick: (pollId: String, pollName: String) -> Unit = { _, _ -> }
) {
    MessageScaffold(
        uiMessage = message,
        isOneToOneConversation = isOneToOneConversation,
        conversationThreadId = conversationThreadId,
        forceTimeBelow = true,
        content = {
            Surface(
                shape = MaterialTheme.shapes.small,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_bar_chart_24),
                            tint = colorScheme.onSurface,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(top = 2.dp, end = 6.dp)
                                .size(HEADER_ICON_SIZE)
                                .align(Alignment.Top)
                        )
                        Text(
                            typeContent.pollName,
                            fontSize = HEADER_TEXT_SIZE,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButtonNoStyling(stringResource(R.string.message_poll_tap_to_open)) {
                        onPollClick(typeContent.pollId, typeContent.pollName)
                    }
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
            fontSize = HEADER_TEXT_SIZE
        )
    }
}
