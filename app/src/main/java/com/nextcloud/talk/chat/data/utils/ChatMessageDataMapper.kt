/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.utils

import com.nextcloud.talk.chat.data.model.ChatMessageEntity
import com.nextcloud.talk.chat.data.model.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatMessage

object ChatMessageDataMapper {

    fun mapToMessage(msg: ChatMessageJson?): ChatMessage {
        return msg?.let {
            ChatMessage().apply {
                jsonMessageId = msg.jsonMessageId
                token = msg.token
                actorType = msg.actorType
                actorId = msg.actorId
                actorDisplayName = msg.actorDisplayName
                timestamp = msg.timestamp
                message = msg.message
                messageParameters = msg.messageParameters
                systemMessageType = msg.systemMessageType
                replyable = msg.replyable
                parentMessage = mapToMessage(msg.parentMessage)
                messageType = msg.messageType
                reactions = msg.reactions
                reactionsSelf = msg.reactionsSelf
                expirationTimestamp = msg.expirationTimestamp
                renderMarkdown = msg.renderMarkdown
                lastEditActorDisplayName = msg.lastEditActorDisplayName
                lastEditActorId = msg.lastEditActorId
                lastEditActorType = msg.lastEditActorType
                lastEditTimestamp = msg.lastEditTimestamp
            }
        } ?: ChatMessage()
    }

    fun mapToMessage(msg: ChatMessageEntity): ChatMessage {
        return ChatMessage().apply {
            jsonMessageId = msg.jsonMessageId
            token = msg.token
            actorType = msg.actorType
            actorId = msg.actorId
            actorDisplayName = msg.actorDisplayName
            timestamp = msg.timestamp
            message = msg.message
            messageParameters = msg.messageParameters
            systemMessageType = msg.systemMessageType
            replyable = msg.replyable
            parentMessage = msg.parentMessage
            messageType = msg.messageType
            reactions = msg.reactions
            reactionsSelf = msg.reactionsSelf
            expirationTimestamp = msg.expirationTimestamp
            renderMarkdown = msg.renderMarkdown
            lastEditActorDisplayName = msg.lastEditActorDisplayName
            lastEditActorId = msg.lastEditActorId
            lastEditActorType = msg.lastEditActorType
            lastEditTimestamp = msg.lastEditTimestamp
        }
    }

    fun mapToEntity(msg: ChatMessage): ChatMessageEntity {
        return ChatMessageEntity().apply {
            jsonMessageId = msg.jsonMessageId
            token = msg.token
            actorType = msg.actorType
            actorId = msg.actorId
            actorDisplayName = msg.actorDisplayName
            timestamp = msg.timestamp
            message = msg.message
            messageParameters = msg.messageParameters
            systemMessageType = msg.systemMessageType
            replyable = msg.replyable
            parentMessage = msg.parentMessage
            messageType = msg.messageType
            reactions = msg.reactions
            reactionsSelf = msg.reactionsSelf
            expirationTimestamp = msg.expirationTimestamp
            renderMarkdown = msg.renderMarkdown
            lastEditActorDisplayName = msg.lastEditActorDisplayName
            lastEditActorId = msg.lastEditActorId
            lastEditActorType = msg.lastEditActorType
            lastEditTimestamp = msg.lastEditTimestamp
        }
    }
}
