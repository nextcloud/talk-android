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
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage

private const val AUTHOR_TEXT_SIZE = 12

@Composable
fun PollMessage(message: ChatMessage, conversationThreadId: Long? = null, state: MutableState<Boolean>) {
    CommonMessageBody(
        message = message,
        conversationThreadId = conversationThreadId,
        playAnimation = state.value,
        content = {
            Column {
                if (message.messageParameters != null && message.messageParameters!!.size > 0) {
                    for (key in message.messageParameters!!.keys) {
                        val individualHashMap: Map<String?, String?> = message.messageParameters!![key]!!
                        if (individualHashMap["type"] == "talk-poll") {
                            // val pollId = individualHashMap["id"]
                            val pollName = individualHashMap["name"].toString()
                            Row(modifier = Modifier.padding(start = 8.dp)) {
                                Icon(painterResource(R.drawable.ic_baseline_bar_chart_24), "")
                                Text(pollName, fontSize = AUTHOR_TEXT_SIZE.sp, fontWeight = FontWeight.Bold)
                            }

                            TextButtonNoStyling(stringResource(R.string.message_poll_tap_to_open)) {
                                // NOTE: read only for now
                            }
                        }
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
            fontSize = AUTHOR_TEXT_SIZE.sp,
            color = Color.White
        )
    }
}
