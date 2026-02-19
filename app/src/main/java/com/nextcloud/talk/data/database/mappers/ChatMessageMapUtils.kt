/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.mappers

import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ReadStatus

fun ChatMessageJson.asEntity(accountId: Long) =
    ChatMessageEntity(
        // accountId@token@messageId
        internalId = "$accountId@$token@$id",
        accountId = accountId,
        id = id,
        internalConversationId = "$accountId@$token",
        threadId = threadId,
        isThread = hasThread,
        message = message!!,
        token = token!!,
        actorType = actorType!!,
        actorId = actorId!!,
        actorDisplayName = actorDisplayName!!,
        timestamp = timestamp,
        messageParameters = messageParameters,
        systemMessageType = systemMessageType!!,
        replyable = replyable,
        parentMessageId = parentMessage?.id,
        messageType = messageType!!,
        reactions = reactions,
        reactionsSelf = reactionsSelf,
        expirationTimestamp = expirationTimestamp,
        renderMarkdown = renderMarkdown,
        lastEditActorDisplayName = lastEditActorDisplayName,
        lastEditActorId = lastEditActorId,
        lastEditActorType = lastEditActorType,
        lastEditTimestamp = lastEditTimestamp,
        deleted = deleted,
        referenceId = referenceId,
        silent = silent,
        threadTitle = threadTitle,
        threadReplies = threadReplies,
        pinnedActorType = metaData?.pinnedActorType,
        pinnedActorId = metaData?.pinnedActorId,
        pinnedActorDisplayName = metaData?.pinnedActorDisplayName,
        pinnedAt = metaData?.pinnedAt,
        pinnedUntil = metaData?.pinnedUntil,
        sendAt = sendAt
    )

fun ChatMessageEntity.toDomainModel() =
    ChatMessage(
        jsonMessageId = id.toInt(),
        message = message,
        token = token,
        threadId = threadId,
        isThread = isThread,
        actorType = actorType,
        actorId = actorId,
        actorDisplayName = actorDisplayName,
        timestamp = timestamp,
        messageParameters = messageParameters,
        systemMessageType = systemMessageType,
        replyable = replyable,
        parentMessageId = parentMessageId,
        messageType = messageType,
        reactions = reactions,
        reactionsSelf = reactionsSelf,
        expirationTimestamp = expirationTimestamp,
        renderMarkdown = renderMarkdown,
        lastEditActorDisplayName = lastEditActorDisplayName,
        lastEditActorId = lastEditActorId,
        lastEditActorType = lastEditActorType,
        lastEditTimestamp = lastEditTimestamp,
        isDeleted = deleted,
        referenceId = referenceId,
        isTemporary = isTemporary,
        sendStatus = sendStatus,
        // readStatus = ReadStatus.NONE,
        silent = silent,
        threadTitle = threadTitle,
        threadReplies = threadReplies,
        pinnedActorType = pinnedActorType,
        pinnedActorId = pinnedActorId,
        pinnedActorDisplayName = pinnedActorDisplayName,
        pinnedAt = pinnedAt,
        pinnedUntil = pinnedUntil,
        sendAt = sendAt
    )

fun ChatMessageJson.toDomainModel() =
    ChatMessage(
        jsonMessageId = id.toInt(),
        message = message,
        token = token,
        threadId = threadId,
        isThread = hasThread,
        actorType = actorType,
        actorId = actorId,
        actorDisplayName = actorDisplayName,
        timestamp = timestamp,
        messageParameters = messageParameters,
        systemMessageType = systemMessageType,
        replyable = replyable,
        parentMessageId = parentMessage?.id,
        messageType = messageType,
        reactions = reactions,
        reactionsSelf = reactionsSelf,
        expirationTimestamp = expirationTimestamp,
        renderMarkdown = renderMarkdown,
        lastEditActorDisplayName = lastEditActorDisplayName,
        lastEditActorId = lastEditActorId,
        lastEditActorType = lastEditActorType,
        lastEditTimestamp = lastEditTimestamp,
        isDeleted = deleted,
        referenceId = referenceId,
        silent = silent,
        threadTitle = threadTitle,
        threadReplies = threadReplies,
        pinnedActorType = metaData?.pinnedActorType,
        pinnedActorId = metaData?.pinnedActorId,
        pinnedActorDisplayName = metaData?.pinnedActorDisplayName,
        pinnedAt = metaData?.pinnedAt,
        pinnedUntil = metaData?.pinnedUntil,
        sendAt = sendAt
    )
