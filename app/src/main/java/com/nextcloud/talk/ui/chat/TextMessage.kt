/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.nextcloud.talk.chat.data.model.ChatMessage

@Composable
fun TextMessage(
    message: ChatMessage,
    showAvatar: Boolean,
    conversationThreadId: Long? = null, state: MutableState<Boolean>
) {
    CommonMessageBody(
        message = message,
        conversationThreadId = conversationThreadId,
        playAnimation = state.value,
        showAvatar = true,
        content = {
            EnrichedText(
                message
            )
        }
    )
}
