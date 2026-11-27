/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.domain

import com.nextcloud.talk.models.json.chat.ChatMessageDto

sealed class ChatPullResult {
    /** [requestedAtNanos] is the [System.nanoTime] at which the request that got the response was started. */
    data class Success(val messages: List<ChatMessageDto>, val lastCommonRead: Int?, val requestedAtNanos: Long) :
        ChatPullResult()

    object NotModified : ChatPullResult()
    object PreconditionFailed : ChatPullResult()
    data class Error(val throwable: Throwable) : ChatPullResult()
}
