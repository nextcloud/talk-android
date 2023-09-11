/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.conversation.repository

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.RetrofitBucket
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class ConversationRepositoryImpl(private val ncApi: NcApi, currentUserProvider: CurrentUserProviderNew) :
    ConversationRepository {

    val currentUser: User = currentUserProvider.currentUser.blockingGet()
    val credentials: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)

    override fun renameConversation(
        roomToken: String,
        roomNameNew: String
    ): Observable<GenericOverall> {
        val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.APIv4, ApiUtils.APIv1))

        return ncApi.renameRoom(
            credentials,
            ApiUtils.getUrlForRoom(
                apiVersion,
                currentUser.baseUrl,
                roomToken
            ),
            roomNameNew
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(API_RETRIES)
    }

    override fun createConversation(
        roomName: String,
        conversationType: Conversation.ConversationType?
    ): Observable<RoomOverall> {
        val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.APIv4, ApiUtils.APIv1))

        val retrofitBucket: RetrofitBucket = if (conversationType == Conversation.ConversationType.ROOM_PUBLIC_CALL) {
            ApiUtils.getRetrofitBucketForCreateRoom(
                apiVersion,
                currentUser.baseUrl,
                ROOM_TYPE_PUBLIC,
                null,
                null,
                roomName
            )
        } else {
            ApiUtils.getRetrofitBucketForCreateRoom(
                apiVersion,
                currentUser.baseUrl,
                ROOM_TYPE_GROUP,
                null,
                null,
                roomName
            )
        }
        return ncApi.createRoom(credentials, retrofitBucket.url, retrofitBucket.queryMap)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(1)
    }

    companion object {
        private const val ROOM_TYPE_PUBLIC = "3"
        private const val ROOM_TYPE_GROUP = "2"
        const val API_RETRIES: Long = 3
    }
}
