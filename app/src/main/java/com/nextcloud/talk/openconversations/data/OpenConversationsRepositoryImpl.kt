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
}
