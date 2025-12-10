/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.runtime.Immutable
import com.nextcloud.talk.chat.data.model.ChatMessage

@Immutable
data class ChatUiMessage(val message: ChatMessage, val avatarUrl: String?)
