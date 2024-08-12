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

// ChatBlockEntity.kt:26: internalConversationId column references a foreign key but it is not part of an index. This may trigger full table scans whenever parent table is modified so you are highly advised to create an index that covers this column.

@Entity(
    tableName = "ChatBlocks",
    foreignKeys = [
    androidx.room.ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = arrayOf("internalId"),
        childColumns = arrayOf("internalConversationId"),
        onDelete = androidx.room.ForeignKey.CASCADE,
        onUpdate = androidx.room.ForeignKey.CASCADE
    )
    ],
)
data class ChatBlockEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Long = 0,
    // accountId@token
    @ColumnInfo(name = "internalConversationId") var internalConversationId: String,
    @ColumnInfo(name = "accountId") var accountId: Long? = null,
    @ColumnInfo(name = "token") var token: String?,
    @ColumnInfo(name = "oldestMessageId") var oldestMessageId: Long,
    @ColumnInfo(name = "newestMessageId") var newestMessageId: Long,
    @ColumnInfo(name = "hasHistory") var hasHistory: Boolean
)
