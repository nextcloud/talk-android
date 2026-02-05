/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.RetrofitBucket
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ContactUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class ContactsRepositoryImpl @Inject constructor(private val ncApiCoroutines: NcApiCoroutines) : ContactsRepository {

    override suspend fun getContacts(user: User, searchQuery: String?, shareTypes: List<String>): AutocompleteOverall {
        val credentials = ApiUtils.getCredentials(user.username, user.token)

        val retrofitBucket: RetrofitBucket = ApiUtils.getRetrofitBucketForContactsSearchFor14(
            user.baseUrl!!,
            searchQuery
        )

        val modifiedQueryMap: HashMap<String, Any> = HashMap(retrofitBucket.queryMap)
        modifiedQueryMap["limit"] = ContactUtils.MAX_CONTACT_LIMIT
        modifiedQueryMap["shareTypes[]"] = shareTypes
        val response = ncApiCoroutines.getContactsWithSearchParam(
            credentials,
            retrofitBucket.url,
            shareTypes,
            modifiedQueryMap
        )
        return response
    }

    override suspend fun createRoom(
        user: User,
        roomType: String,
        sourceType: String?,
        userId: String,
        conversationName: String?
    ): RoomOverall {
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, 1))
        val credentials = ApiUtils.getCredentials(user.username, user.token)

        val retrofitBucket: RetrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            version = apiVersion,
            baseUrl = user.baseUrl,
            roomType = roomType,
            source = sourceType,
            invite = userId,
            conversationName = conversationName
        )
        val response = ncApiCoroutines.createRoom(
            credentials,
            retrofitBucket.url,
            retrofitBucket.queryMap
        )
        return response
    }

    override fun getImageUri(user: User, avatarId: String, requestBigSize: Boolean): String =
        ApiUtils.getUrlForAvatar(
            user.baseUrl,
            avatarId,
            requestBigSize
        )

    override fun getContactsFlow(user: User, searchQuery: String?): Flow<List<AutocompleteUser>> =
        flow {
            val credentials = ApiUtils.getCredentials(user.username, user.token)

            val retrofitBucket: RetrofitBucket = ApiUtils.getRetrofitBucketForContactsSearchFor14(
                user.baseUrl!!,
                searchQuery
            )

            val shareTypes = mutableListOf(ShareType.User.shareType).toList()

            val modifiedQueryMap: HashMap<String, Any> = HashMap(retrofitBucket.queryMap)
            modifiedQueryMap["limit"] = ContactUtils.MAX_CONTACT_LIMIT
            modifiedQueryMap["shareTypes[]"] = shareTypes
            val response = ncApiCoroutines.getContactsWithSearchParam(
                credentials,
                retrofitBucket.url,
                shareTypes,
                modifiedQueryMap
            )

            emit(response.ocs?.data.orEmpty())
        }

    companion object {
        private val TAG = ContactsRepositoryImpl::class.simpleName
    }
}
