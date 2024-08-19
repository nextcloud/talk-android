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
        return ncApiCoroutines.addParticipant(credentials, retrofitBucket.url, retrofitBucket.queryMap)
    }

    override fun getImageUri(avatarId: String, requestBigSize: Boolean): String {
        return ApiUtils.getUrlForAvatar(
            _currentUser.baseUrl,
            avatarId,
            requestBigSize
        )
    }
}
