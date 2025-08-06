/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 David Leibovych <ariedov@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.nextcloud.talk.data.database.model.FileUploadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileUploadsDao {

    @Query("""
        SELECT *
        FROM FileUploads
        WHERE internalConversationId = :internalConversationId
        ORDER BY timestamp DESC, id DESC
    """)
    fun getFileUploadsForConversation(internalConversationId: String): Flow<List<FileUploadEntity>>

    @Query("UPDATE FileUploads SET progress = :progress WHERE id = :id")
    fun updateProgress(id: Int, progress: Float)
}
