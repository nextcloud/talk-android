/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent

private val headerTextSize = 16.sp
private val headerIconSize = 18.dp

@Composable
fun DeckMessage(
    typeContent: MessageTypeContent.Deck,
    message: ChatMessageUi,
    isOneToOneConversation: Boolean = false,
    conversationThreadId: Long? = null
) {
    val context = LocalContext.current

    MessageScaffold(
        uiMessage = message,
        isOneToOneConversation = isOneToOneConversation,
        conversationThreadId = conversationThreadId,
        forceTimeBelow = true,
        content = {
            if (typeContent.cardName.isNotEmpty()) {
                val cardDescription = String.format(
                    LocalResources.current.getString(R.string.deck_card_description),
                    typeContent.stackName,
                    typeContent.boardName
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 4.dp)
                        .clickable(enabled = typeContent.cardLink.isNotBlank()) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(typeContent.cardLink)))
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row {
                            Icon(
                                painter = painterResource(R.drawable.deck),
                                tint = colorScheme.onSurface,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(top = 2.dp, end = 6.dp)
                                    .size(headerIconSize)
                                    .align(Alignment.Top)
                            )
                            Text(
                                text = typeContent.cardName,
                                fontSize = headerTextSize,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = cardDescription,
                            color = colorScheme.onSurface,
                            fontSize = headerTextSize
                        )
                    }
                }
            }
        }
    )
}
