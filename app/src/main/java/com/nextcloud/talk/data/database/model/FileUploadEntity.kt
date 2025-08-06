/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "FileUploads",
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
data class FileUploadEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") var id: Int,
    @ColumnInfo(name = "internalConversationId") val internalConversationId: String,
    @ColumnInfo(name = "fileName") val fileName: String? = null,
    @ColumnInfo(name = "progress") var progress: Float = 0f,
    @ColumnInfo(name = "status") var status: String? = null,
    @ColumnInfo(name = "hidden") var hidden: Boolean = false,
    @ColumnInfo(name = "timestamp") var timestamp: Long = 0,
)
