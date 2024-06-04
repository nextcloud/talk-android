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
import androidx.room.PrimaryKey
import com.nextcloud.talk.models.json.chat.ChatMessage

// TODO: ChatMessageEntity.java:5: warning: internal_conversation_id column references a foreign key but it is not
//  part of an index. This may trigger full table scans whenever parent table is modified so you are highly advised to create an index that covers this column.
//     public final class ChatMessageEntity {

@Entity(
    tableName = "ChatMessages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("internal_conversation_id"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,

    @ColumnInfo(name = "internal_conversation_id") var internalConversationId: Long? = null,
    @ColumnInfo(name = "message") var message: String? = null,
    @ColumnInfo(name = "token") var token: String? = null,
    @ColumnInfo(name = "actorType") var actorType: String? = null,
    @ColumnInfo(name = "actorId") var actorId: String? = null,
    @ColumnInfo(name = "actorDisplayName") var actorDisplayName: String? = null,
    @ColumnInfo(name = "timestamp") var timestamp: Long = 0,
    @ColumnInfo(name = "messageParameters") var messageParameters: HashMap<String?, HashMap<String?, String?>>? = null,
    @ColumnInfo(name = "systemMessage") var systemMessageType: ChatMessage.SystemMessageType? = null,
    @ColumnInfo(name = "isReplyable") var replyable: Boolean = false,
    @ColumnInfo(name = "parent") var parentMessageId: Long? = null, // TODO refactor code to deal with this
    @ColumnInfo(name = "messageType") var messageType: String? = null,
    @ColumnInfo(name = "reactions") var reactions: LinkedHashMap<String, Int>? = null,
    @ColumnInfo(name = "reactionsSelf") var reactionsSelf: ArrayList<String>? = null,
    @ColumnInfo(name = "expirationTimestamp") var expirationTimestamp: Int = 0,
    @ColumnInfo(name = "markdown") var renderMarkdown: Boolean? = null,
    @ColumnInfo(name = "lastEditActorDisplayName") var lastEditActorDisplayName: String? = null,
    @ColumnInfo(name = "lastEditActorId") var lastEditActorId: String? = null,
    @ColumnInfo(name = "lastEditActorType") var lastEditActorType: String? = null,
    @ColumnInfo(name = "lastEditTimestamp") var lastEditTimestamp: Long = 0
)
