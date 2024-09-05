/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.RetrofitBucket
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.AddParticipantOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ApiUtils.getRetrofitBucketForAddParticipant
import com.nextcloud.talk.utils.ApiUtils.getRetrofitBucketForAddParticipantWithSource

class ConversationCreationRepositoryImpl(
    private val ncApiCoroutines: NcApiCoroutines,
    private val userManager: UserManager
) : ConversationCreationRepository {
    private val _currentUser = userManager.currentUser.blockingGet()
    val currentUser: User = _currentUser
    val credentials = ApiUtils.getCredentials(_currentUser.username, _currentUser.token)
    val apiVersion = ApiUtils.getConversationApiVersion(_currentUser, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))

    override suspend fun renameConversation(roomToken: String, roomNameNew: String?): GenericOverall {
        return ncApiCoroutines.renameRoom(
            credentials,
            ApiUtils.getUrlForRoom(
                apiVersion,
                _currentUser.baseUrl,
                roomToken
            ),
            roomNameNew
        )
    }

    override suspend fun setConversationDescription(roomToken: String, description: String?): GenericOverall {
        return ncApiCoroutines.setConversationDescription(
            credentials,
            ApiUtils.getUrlForConversationDescription(
                apiVersion,
                _currentUser.baseUrl,
                roomToken
            ),
            description
        )
    }

    override suspend fun openConversation(roomToken: String, scope: Int): GenericOverall {
        return ncApiCoroutines.openConversation(
            credentials,
            ApiUtils.getUrlForOpeningConversations(
                apiVersion,
                _currentUser.baseUrl,
                roomToken
            ),
            scope
        )
    }

    override suspend fun addParticipants(
        conversationToken: String?,
        userId: String,
        sourceType: String
    ): AddParticipantOverall {
        val retrofitBucket: RetrofitBucket = if (sourceType == "users") {
            getRetrofitBucketForAddParticipant(
                apiVersion,
                _currentUser.baseUrl,
                conversationToken,
                userId
            )
        } else {
            getRetrofitBucketForAddParticipantWithSource(
                apiVersion,
                _currentUser.baseUrl,
                conversationToken,
                sourceType,
                userId
            )
        }
        val participants = ncApiCoroutines.addParticipant(credentials, retrofitBucket.url, retrofitBucket.queryMap)
        return participants
    }

    override suspend fun createRoom(roomType: String, conversationName: String?): RoomOverall {
        val retrofitBucket: RetrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            apiVersion,
            _currentUser.baseUrl,
            roomType,
            null,
            null,
            conversationName
        )
        val response = ncApiCoroutines.createRoom(
            credentials,
            retrofitBucket.url,
            retrofitBucket.queryMap
        )
        return response
    }

    override fun getImageUri(avatarId: String, requestBigSize: Boolean): String {
        return ApiUtils.getUrlForAvatar(
            _currentUser.baseUrl,
            avatarId,
            requestBigSize
        )
    }

    override suspend fun setPassword(roomToken: String, password: String): GenericOverall {
        val result = ncApiCoroutines.setPassword(
            credentials,
            ApiUtils.getUrlForRoomPassword(
                apiVersion,
                _currentUser.baseUrl!!,
                roomToken
            ),
            password
        )
        return result
    }

    override suspend fun allowGuests(token: String, allow: Boolean): GenericOverall {
        val url = ApiUtils.getUrlForRoomPublic(
            apiVersion,
            _currentUser.baseUrl!!,
            token
        )

        val result: GenericOverall = if (allow) {
            ncApiCoroutines.makeRoomPublic(
                credentials,
                url
            )
        } else {
            ncApiCoroutines.makeRoomPrivate(
                credentials,
                url
            )
        }

        return result
    }
}
