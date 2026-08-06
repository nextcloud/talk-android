/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationtags.data

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.tags.AssignConversationTagsRequest
import com.nextcloud.talk.models.json.tags.ConversationTagOverall
import com.nextcloud.talk.models.json.tags.ConversationTagsOverall
import com.nextcloud.talk.models.json.tags.CreateConversationTagRequest
import com.nextcloud.talk.models.json.tags.ReorderConversationTagsRequest
import com.nextcloud.talk.models.json.tags.UpdateConversationTagRequest
import com.nextcloud.talk.utils.ApiUtils
import javax.inject.Inject

class ConversationTagsRepositoryImpl @Inject constructor(private val ncApiCoroutines: NcApiCoroutines) :
    ConversationTagsRepository {

    override suspend fun getTags(credentials: String, baseUrl: String): ConversationTagsOverall =
        ncApiCoroutines.getConversationTags(credentials, ApiUtils.getUrlForConversationTags(baseUrl))

    override suspend fun createTag(credentials: String, baseUrl: String, name: String): ConversationTagOverall {
        val request = CreateConversationTagRequest().apply { this.name = name }
        return ncApiCoroutines.createConversationTag(credentials, ApiUtils.getUrlForConversationTags(baseUrl), request)
    }

    override suspend fun updateTag(
        credentials: String,
        baseUrl: String,
        tagId: String,
        name: String
    ): ConversationTagOverall {
        val request = UpdateConversationTagRequest().apply { this.name = name }
        return ncApiCoroutines.updateConversationTag(
            credentials,
            ApiUtils.getUrlForConversationTag(baseUrl, tagId),
            request
        )
    }

    override suspend fun deleteTag(credentials: String, baseUrl: String, tagId: String) {
        ncApiCoroutines.deleteConversationTag(credentials, ApiUtils.getUrlForConversationTag(baseUrl, tagId))
    }

    override suspend fun reorderTags(
        credentials: String,
        baseUrl: String,
        orderedIds: List<String>
    ): ConversationTagsOverall {
        val request = ReorderConversationTagsRequest().apply { this.orderedIds = orderedIds }
        return ncApiCoroutines.reorderConversationTags(
            credentials,
            ApiUtils.getUrlForConversationTagsReorder(baseUrl),
            request
        )
    }

    override suspend fun assignTags(
        credentials: String,
        baseUrl: String,
        roomToken: String,
        tagIds: List<String>
    ): RoomOverall {
        val request = AssignConversationTagsRequest().apply { this.tagIds = tagIds }
        return ncApiCoroutines.assignConversationTags(
            credentials,
            ApiUtils.getUrlForRoomTags(baseUrl, roomToken),
            request
        )
    }
}
