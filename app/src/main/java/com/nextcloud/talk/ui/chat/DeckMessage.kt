/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.nextcloud.talk.chat.ui.model.ChatMessageUi

private const val AUTHOR_TEXT_SIZE = 12

@Composable
fun DeckMessage(message: ChatMessageUi, conversationThreadId: Long? = null, state: MutableState<Boolean>) {
    MessageScaffold(
        uiMessage = message,
        conversationThreadId = conversationThreadId,
        playAnimation = state.value,
        content = {
            // Column {
            //     if (message.messageParameters != null && message.messageParameters!!.isNotEmpty()) {
            //         for (key in message.messageParameters!!.keys) {
            //             val individualHashMap: Map<String?, String?> = message.messageParameters!![key]!!
            //             if (individualHashMap["type"] == "deck-card") {
            //                 val cardName = individualHashMap["name"]
            //                 val stackName = individualHashMap["stackname"]
            //                 val boardName = individualHashMap["boardname"]
            //                 // val cardLink = individualHashMap["link"]
            //
            //                 if (cardName?.isNotEmpty() == true) {
            //                     val cardDescription = String.format(
            //                         LocalContext.current.resources.getString(R.string.deck_card_description),
            //                         stackName,
            //                         boardName
            //                     )
            //                     Row(modifier = Modifier.padding(start = 8.dp)) {
            //                         Icon(painterResource(R.drawable.deck), "")
            //                         Text(cardName, fontSize = AUTHOR_TEXT_SIZE.sp, fontWeight = FontWeight.Bold)
            //                     }
            //                     Text(cardDescription, fontSize = AUTHOR_TEXT_SIZE.sp)
            //                 }
            //             }
            //         }
            //     }
            // }
        }
    )
}
