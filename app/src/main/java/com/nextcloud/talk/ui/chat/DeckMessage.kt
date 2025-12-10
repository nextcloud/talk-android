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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent

private const val AUTHOR_TEXT_SIZE = 12

@Composable
fun DeckMessage(typeContent: MessageTypeContent.Deck, message: ChatMessageUi, conversationThreadId: Long? = null) {
    MessageScaffold(
        uiMessage = message,
        conversationThreadId = conversationThreadId,
        content = {
            Column {
                if (typeContent.cardName.isNotEmpty()) {
                    val cardDescription = String.format(
                        LocalResources.current.getString(R.string.deck_card_description),
                        typeContent.stackName,
                        typeContent.boardName
                    )
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Icon(painterResource(R.drawable.deck), "")
                        Text(
                            text = typeContent.cardName,
                            fontSize = AUTHOR_TEXT_SIZE.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(cardDescription, fontSize = AUTHOR_TEXT_SIZE.sp)
                }
            }
        }
    )
}
