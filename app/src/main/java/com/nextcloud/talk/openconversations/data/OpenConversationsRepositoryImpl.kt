/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.openconversations.data

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OpenConversationsRepositoryImpl(private val ncApiCoroutines: NcApiCoroutines) : OpenConversationsRepository {
    override suspend fun fetchConversations(user: User, url: String, searchTerm: String): Result<List<Conversation>> =
        runCatching {
            val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!

            val roomOverall = ncApiCoroutines.getOpenConversations(
                credentials,
                url,
                searchTerm
            )
            roomOverall.ocs?.data.orEmpty()
        }

    override fun fetchOpenConversationsFlow(user: User, searchTerm: String): Flow<List<Conversation>> =
        flow {
            val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!

            val apiVersion = ApiUtils.getConversationApiVersion(
                user,
                intArrayOf(
                    ApiUtils.API_V4,
                    ApiUtils.API_V3,
                    1
                )
            )
            val url = ApiUtils.getUrlForOpenConversations(apiVersion, user.baseUrl!!)

            val roomOverall = ncApiCoroutines.getOpenConversations(
                credentials,
                url,
                searchTerm
            )

            emit(roomOverall.ocs?.data.orEmpty())
        }
}
