/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.openconversations.data

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observable

class OpenConversationsRepositoryImpl(private val ncApi: NcApi, currentUserProvider: CurrentUserProviderNew) :
    OpenConversationsRepository {

    val currentUser: User = currentUserProvider.currentUser.blockingGet()
    val credentials: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)!!

    val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, 1))

    override fun fetchConversations(searchTerm: String): Observable<List<Conversation>> {
        val roomOverall = ncApi.getOpenConversations(
            credentials,
            ApiUtils.getUrlForOpenConversations(apiVersion, currentUser.baseUrl!!),
            searchTerm
        )
        return roomOverall.map { it.ocs?.data!! }
    }
}
