/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 David Leibovych <ariedov@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nextcloud.talk.data.database.model.FileUploadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileUploadsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun createFileUpload(entity: FileUploadEntity): Long

    @Query(
"""
        SELECT *
        FROM FileUploads
        WHERE internalConversationId = :internalConversationId
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun getFileUploadsForConversation(internalConversationId: String): Flow<List<FileUploadEntity>>

    @Query("UPDATE FileUploads SET progress = :progress WHERE id = :id")
    fun updateProgress(id: Int, progress: Float)

    @Query("UPDATE FileUploads SET status = 'STARTED' WHERE id = :id")
    fun setStarted(id: Long)

    @Query("UPDATE FileUploads SET status = 'COMPLETED' WHERE id = :id")
    fun setCompleted(id: Long)

    @Query("UPDATE FileUploads SET status = 'FAILED' WHERE id = :id")
    fun setFailed(id: Long)
}
