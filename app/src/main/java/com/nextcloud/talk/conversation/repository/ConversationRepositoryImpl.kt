/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversation.repository

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.RetrofitBucket
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class ConversationRepositoryImpl(private val ncApi: NcApi, currentUserProvider: CurrentUserProviderNew) :
    ConversationRepository {

    val currentUser: User = currentUserProvider.currentUser.blockingGet()
    val credentials: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)!!

    override fun createConversation(
        roomName: String,
        conversationType: ConversationEnums.ConversationType?
    ): Observable<RoomOverall> {
        val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))

        val retrofitBucket: RetrofitBucket =
            if (conversationType == ConversationEnums.ConversationType.ROOM_PUBLIC_CALL) {
                ApiUtils.getRetrofitBucketForCreateRoom(
                    apiVersion,
                    currentUser.baseUrl!!,
                    ROOM_TYPE_PUBLIC,
                    null,
                    null,
                    roomName
                )
            } else {
                ApiUtils.getRetrofitBucketForCreateRoom(
                    apiVersion,
                    currentUser.baseUrl!!,
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
    }
}
