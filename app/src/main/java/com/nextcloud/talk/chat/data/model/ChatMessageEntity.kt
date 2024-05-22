/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.TypeConverters
import com.nextcloud.talk.data.source.local.converters.HashMapHashMapConverter
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.converters.EnumSystemMessageTypeConverter
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "Messages", primaryKeys = ["id"])
@TypeConverters(EnumSystemMessageTypeConverter::class, HashMapHashMapConverter::class)
data class ChatMessageEntity(
    @ColumnInfo(name = "id")
    var jsonMessageId: Int = 0,

    @ColumnInfo(name = "token")
    var token: String? = null,

    // guests or users
    @ColumnInfo(name = "actorType")
    var actorType: String? = null,

    @ColumnInfo(name = "actorId")
    var actorId: String? = null,

    // send when crafting a message
    @ColumnInfo(name = "actorDisplayName")
    var actorDisplayName: String? = null,

    @ColumnInfo(name = "timestamp")
    var timestamp: Long = 0,

    // send when crafting a message, max 1000 lines
    @ColumnInfo(name = "message")
    var message: String? = null,

    @ColumnInfo(name = "messageParameters")
    var messageParameters: HashMap<String?, HashMap<String?, String?>>? = null,

    @ColumnInfo(name = "systemMessage")
    var systemMessageType: ChatMessage.SystemMessageType? = null,

    @ColumnInfo(name = "isReplyable")
    var replyable: Boolean = false,

    @ColumnInfo(name = "parent")
    var parentMessage: ChatMessage? = null, // FIXME figure this out, might replace w/ parent id

    @ColumnInfo(name = "messageType")
    var messageType: String? = null,

    @ColumnInfo(name = "reactions")
    var reactions: LinkedHashMap<String, Int>? = null,

    @ColumnInfo(name = "reactionsSelf")
    var reactionsSelf: ArrayList<String>? = null,

    @ColumnInfo(name = "expirationTimestamp")
    var expirationTimestamp: Int = 0,

    @ColumnInfo(name = "markdown")
    var renderMarkdown: Boolean? = null,

    @ColumnInfo(name = "lastEditActorDisplayName")
    var lastEditActorDisplayName: String? = null,

    @ColumnInfo(name = "lastEditActorId")
    var lastEditActorId: String? = null,

    @ColumnInfo(name = "lastEditActorType")
    var lastEditActorType: String? = null,

    @ColumnInfo(name = "lastEditTimestamp")
    var lastEditTimestamp: Long = 0
) : Parcelable
