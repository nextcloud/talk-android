/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.model

import com.nextcloud.talk.models.json.chat.ChatMessage

data class ChatMessageModel(
    var id: Int = 0,
    var token: String? = null,
    var actorType: String? = null,
    var actorId: String? = null,
    var actorDisplayName: String? = null,
    var timestamp: Long = 0,
    var message: String? = null,
    var messageParameters: HashMap<String?, HashMap<String?, String?>>? = null,
    var systemMessageType: ChatMessage.SystemMessageType? = null,
    var replyable: Boolean = false,
    var parentMessageId: Long? = null,
    var messageType: String? = null,
    var reactions: LinkedHashMap<String, Int>?,
    var reactionsSelf: ArrayList<String>? = null,
    var expirationTimestamp: Int = 0,
    var renderMarkdown: Boolean? = null,
    var lastEditActorDisplayName: String? = null,
    var lastEditActorId: String? = null,
    var lastEditActorType: String? = null,
    var lastEditTimestamp: Long = 0
)
