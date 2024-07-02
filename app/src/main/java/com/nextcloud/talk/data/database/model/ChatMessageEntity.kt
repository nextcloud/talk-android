/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nextcloud.talk.models.json.chat.ChatMessage

@Entity(
    tableName = "ChatMessages",
    // TODO fix this weird error with foreign keys causing SQLite constraint exception
    //  when I set internal_conversation_id
    // foreignKeys = [
    //     ForeignKey(
    //         entity = ConversationEntity::class,
    //         parentColumns = arrayOf("id"),
    //         childColumns = arrayOf("internal_conversation_id"),
    //         onDelete = ForeignKey.CASCADE,
    //         onUpdate = ForeignKey.CASCADE,
    //     )
    // ],
    // indices = [
    //     Index(value = ["id"], unique = true),
    //     Index(value = ["internal_conversation_id"])
    // ]
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
    // TODO refactor code to deal with this
    @ColumnInfo(name = "parent") var parentMessageId: Long? = null,
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
