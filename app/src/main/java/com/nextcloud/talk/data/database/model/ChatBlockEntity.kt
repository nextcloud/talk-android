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

@Entity(
    tableName = "ChatBlocks"
    // indices = [
    //     androidx.room.Index(value = ["accountId"])
    // ]
)
data class ChatBlockEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Int = 0,
    // accountId@token
    @ColumnInfo(name = "internalConversationId") var internalConversationId: String,
    // @ColumnInfo(name = "accountId") var accountId: Long? = null,
    // @ColumnInfo(name = "token") var token: String?,
    @ColumnInfo(name = "oldestMessageId") var oldestMessageId: Long,
    @ColumnInfo(name = "newestMessageId") var newestMessageId: Long,
    @ColumnInfo(name = "hasHistory") var hasHistory: Boolean
)
