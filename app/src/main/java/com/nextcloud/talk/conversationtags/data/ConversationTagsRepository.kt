/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationtags.data

import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.tags.ConversationTagOverall
import com.nextcloud.talk.models.json.tags.ConversationTagsOverall

interface ConversationTagsRepository {
    suspend fun getTags(credentials: String, baseUrl: String): ConversationTagsOverall

    suspend fun createTag(credentials: String, baseUrl: String, name: String): ConversationTagOverall

    suspend fun updateTag(credentials: String, baseUrl: String, tagId: String, name: String): ConversationTagOverall

    suspend fun deleteTag(credentials: String, baseUrl: String, tagId: String)

    suspend fun reorderTags(credentials: String, baseUrl: String, orderedIds: List<String>): ConversationTagsOverall

    suspend fun assignTags(credentials: String, baseUrl: String, roomToken: String, tagIds: List<String>): RoomOverall
}
