/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.openconversations.data

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.Conversation
import kotlinx.coroutines.flow.Flow

interface OpenConversationsRepository {

    suspend fun fetchConversations(user: User, url: String, searchTerm: String): Result<List<Conversation>>

    fun fetchOpenConversationsFlow(user: User, searchTerm: String): Flow<List<Conversation>>
}
