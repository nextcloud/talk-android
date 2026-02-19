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

@Composable
fun TextMessage(
    uiMessage: ChatMessageUi,
    showAvatar: Boolean,
    conversationThreadId: Long? = null,
    state: MutableState<Boolean>
) {
    MessageScaffold(
        uiMessage = uiMessage,
        conversationThreadId = conversationThreadId,
        showAvatar = showAvatar,
        playAnimation = state.value,
        content = {
            EnrichedText(
                uiMessage
            )
        }
    )
}
