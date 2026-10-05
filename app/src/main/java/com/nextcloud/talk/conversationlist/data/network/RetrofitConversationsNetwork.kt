/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist.data.network

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observable
import retrofit2.HttpException

class RetrofitConversationsNetwork(private val ncApi: NcApi) : ConversationsNetworkDataSource {
    override fun getRooms(
        user: User,
        url: String,
        includeStatus: Boolean,
        modifiedSince: Long?
    ): Observable<RoomListResult> {
        val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, 1))

        val delta = modifiedSince?.takeIf { apiVersion >= ApiUtils.API_V4 && it > 0 }

        return ncApi.getRooms(
            credentials,
            ApiUtils.getUrlForRooms(apiVersion, user.baseUrl!!),
            includeStatus,
            delta
        ).map { response ->
            if (!response.isSuccessful) throw HttpException(response)

            RoomListResult(
                conversations = response.body()?.ocs?.data ?: emptyList(),
                modifiedBefore = response.headers()[HEADER_MODIFIED_BEFORE]?.toLongOrNull()?.takeIf { it > 0 },
                wasDelta = delta != null
            )
        }
    }

    companion object {
        /** Taken by the server before it queries, to be sent as `modifiedSince` on the next request. */
        private const val HEADER_MODIFIED_BEFORE = "X-Nextcloud-Talk-Modified-Before"
    }
}
