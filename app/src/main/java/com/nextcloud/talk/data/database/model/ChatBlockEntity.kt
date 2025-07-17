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

@Entity(
    tableName = "ChatBlocks",
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
        Index(value = ["internalConversationId"])
    ]
)
data class ChatBlockEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Long = 0,
    // accountId@token
    @ColumnInfo(name = "internalConversationId") var internalConversationId: String,
    @ColumnInfo(name = "accountId") var accountId: Long? = null,
    @ColumnInfo(name = "token") var token: String?,
    @ColumnInfo(name = "threadId") var threadId: Long? = null,
    @ColumnInfo(name = "oldestMessageId") var oldestMessageId: Long,
    @ColumnInfo(name = "newestMessageId") var newestMessageId: Long,
    @ColumnInfo(name = "hasHistory") var hasHistory: Boolean
)
