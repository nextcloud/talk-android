/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.runtime.Composable
import com.nextcloud.talk.chat.ui.model.ChatMessageUi

@Composable
fun TextMessage(
    uiMessage: ChatMessageUi,
    showAvatar: Boolean,
    conversationThreadId: Long? = null
) {
    MessageScaffold(
        uiMessage = uiMessage,
        conversationThreadId = conversationThreadId,
        showAvatar = showAvatar,
        content = {
            EnrichedText(
                uiMessage
            )
        }
    )
}
