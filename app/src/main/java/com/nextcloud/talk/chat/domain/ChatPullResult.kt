/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.domain

import com.nextcloud.talk.models.json.chat.ChatMessageJson

sealed class ChatPullResult {
    data class Success(val messages: List<ChatMessageJson>, val lastCommonRead: Int?) : ChatPullResult()

    object NotModified : ChatPullResult()
    object PreconditionFailed : ChatPullResult()
    data class Error(val throwable: Throwable) : ChatPullResult()
}
