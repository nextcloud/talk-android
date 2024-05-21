/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.database

import androidx.room.Dao
import androidx.room.Query
import com.nextcloud.talk.chat.data.model.ChatMessageEntity

@Dao
abstract class ChatDao {
    @Query(
        "SELECT * FROM Messages WHERE token = :roomToken"
    )
    abstract fun pullChatMessages(roomToken: String): List<ChatMessageEntity>
}
