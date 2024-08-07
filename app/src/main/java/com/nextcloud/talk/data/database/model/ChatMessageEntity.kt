/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nextcloud.talk.chat.data.model.ChatMessage

@Entity(
    tableName = "ChatMessages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = arrayOf("internalId"),
            childColumns = arrayOf("internalConversationId"),
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["internalId"], unique = true),
        Index(value = ["internalConversationId"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey
    // accountId@roomtoken@messageId
    @ColumnInfo(name = "internalId") var internalId: String,
    @ColumnInfo(name = "accountId") var accountId: Long? = null,
    @ColumnInfo(name = "token") var token: String? = null,
    @ColumnInfo(name = "id") var id: Long = 0,
    // accountId@roomtoken
    @ColumnInfo(name = "internalConversationId") var internalConversationId: String? = null,

    @ColumnInfo(name = "actorType") var actorType: String? = null,
    @ColumnInfo(name = "actorId") var actorId: String? = null,
    @ColumnInfo(name = "actorDisplayName") var actorDisplayName: String? = null,
    @ColumnInfo(name = "timestamp") var timestamp: Long = 0,
    @ColumnInfo(name = "systemMessage") var systemMessageType: ChatMessage.SystemMessageType? = null,
    @ColumnInfo(name = "messageType") var messageType: String? = null,
    @ColumnInfo(name = "isReplyable") var replyable: Boolean = false,
    // TODO: add "referenceId"
    @ColumnInfo(name = "message") var message: String? = null,
    @ColumnInfo(name = "messageParameters") var messageParameters: HashMap<String?, HashMap<String?, String?>>? = null,
    @ColumnInfo(name = "expirationTimestamp") var expirationTimestamp: Int = 0,
    @ColumnInfo(name = "parent") var parentMessageId: Long? = null,
    @ColumnInfo(name = "reactions") var reactions: LinkedHashMap<String, Int>? = null,
    @ColumnInfo(name = "reactionsSelf") var reactionsSelf: ArrayList<String>? = null,
    @ColumnInfo(name = "markdown") var renderMarkdown: Boolean? = null,
    @ColumnInfo(name = "lastEditActorType") var lastEditActorType: String? = null,
    @ColumnInfo(name = "lastEditActorId") var lastEditActorId: String? = null,
    @ColumnInfo(name = "lastEditActorDisplayName") var lastEditActorDisplayName: String? = null,
    @ColumnInfo(name = "lastEditTimestamp") var lastEditTimestamp: Long? = 0,
    @ColumnInfo(name = "deleted") var deleted: Boolean = false,
    // TODO: add "silent"
)
