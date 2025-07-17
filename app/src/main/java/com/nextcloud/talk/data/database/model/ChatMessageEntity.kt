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
    // MOST IMPORTANT ATTRIBUTES

    @PrimaryKey
    // accountId@roomtoken@messageId
    @ColumnInfo(name = "internalId") var internalId: String,
    @ColumnInfo(name = "accountId") var accountId: Long,
    @ColumnInfo(name = "token") var token: String,
    @ColumnInfo(name = "id") var id: Long = 0,
    // accountId@roomtoken
    @ColumnInfo(name = "internalConversationId") var internalConversationId: String,
    @ColumnInfo(name = "threadId") var threadId: Long? = null,
    @ColumnInfo(name = "isThread") var isThread: Boolean = false,
    @ColumnInfo(name = "actorDisplayName") var actorDisplayName: String,
    @ColumnInfo(name = "message") var message: String,

    // OTHER ATTRIBUTES IN ALPHABETICAL ORDER

    @ColumnInfo(name = "actorId") var actorId: String,
    @ColumnInfo(name = "actorType") var actorType: String,
    @ColumnInfo(name = "deleted") var deleted: Boolean = false,
    @ColumnInfo(name = "expirationTimestamp") var expirationTimestamp: Int = 0,
    @ColumnInfo(name = "isReplyable") var replyable: Boolean = false,
    @ColumnInfo(name = "isTemporary") var isTemporary: Boolean = false,
    @ColumnInfo(name = "lastEditActorDisplayName") var lastEditActorDisplayName: String? = null,
    @ColumnInfo(name = "lastEditActorId") var lastEditActorId: String? = null,
    @ColumnInfo(name = "lastEditActorType") var lastEditActorType: String? = null,
    @ColumnInfo(name = "lastEditTimestamp") var lastEditTimestamp: Long? = 0,
    @ColumnInfo(name = "markdown") var renderMarkdown: Boolean? = false,
    @ColumnInfo(name = "messageParameters") var messageParameters: HashMap<String?, HashMap<String?, String?>>? = null,
    @ColumnInfo(name = "messageType") var messageType: String,
    @ColumnInfo(name = "parent") var parentMessageId: Long? = null,
    @ColumnInfo(name = "reactions") var reactions: LinkedHashMap<String, Int>? = null,
    @ColumnInfo(name = "reactionsSelf") var reactionsSelf: ArrayList<String>? = null,
    @ColumnInfo(name = "referenceId") var referenceId: String? = null,
    @ColumnInfo(name = "sendStatus") var sendStatus: SendStatus? = null,
    @ColumnInfo(name = "silent") var silent: Boolean = false,
    @ColumnInfo(name = "systemMessage") var systemMessageType: ChatMessage.SystemMessageType,
    @ColumnInfo(name = "timestamp") var timestamp: Long = 0
)
