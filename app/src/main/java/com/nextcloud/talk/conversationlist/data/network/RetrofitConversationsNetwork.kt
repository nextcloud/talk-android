/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist.data.network

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observable

class RetrofitConversationsNetwork(private val ncApi: NcApi) : ConversationsNetworkDataSource {
    override fun getRooms(user: User, url: String, includeStatus: Boolean): Observable<List<Conversation>> {
        val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, 1))

        return ncApi.getRooms(
            credentials,
            ApiUtils.getUrlForRooms(apiVersion, user.baseUrl!!),
            includeStatus
        ).map { it ->
            it.ocs?.data?.map { it } ?: listOf()
        }
    }
}
