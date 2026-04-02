/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.chat.ui.model.ChatMessageUi

@Composable
fun TextMessage(uiMessage: ChatMessageUi, isOneToOneConversation: Boolean = false, conversationThreadId: Long? = null) {
    MessageScaffold(
        uiMessage = uiMessage,
        conversationThreadId = conversationThreadId,
        isOneToOneConversation = isOneToOneConversation,
        content = {
            EnrichedText(
                uiMessage,
                Modifier.padding(start = 0.dp)
            )
        }
    )
}
