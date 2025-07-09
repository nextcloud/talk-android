/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import android.util.Log
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.RetrofitBucket
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ContactUtils
import com.nextcloud.talk.utils.NoSupportedApiException
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import javax.inject.Inject

class ContactsRepositoryImpl @Inject constructor(
    private val ncApiCoroutines: NcApiCoroutines,
    currentUserProvider: CurrentUserProviderNew
) : ContactsRepository {

    private val _currentUser = currentUserProvider.currentUser.blockingGet()
    val currentUser: User = _currentUser
    val credentials = ApiUtils.getCredentials(_currentUser.username, _currentUser.token)

    override suspend fun getContacts(searchQuery: String?, shareTypes: List<String>): AutocompleteOverall {
        val retrofitBucket: RetrofitBucket = ApiUtils.getRetrofitBucketForContactsSearchFor14(
            currentUser.baseUrl!!,
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
        roomType: String,
        sourceType: String?,
        userId: String,
        conversationName: String?
    ): RoomOverall {
        val apiVersion =
            try {
                ApiUtils.getConversationApiVersion(_currentUser, intArrayOf(ApiUtils.API_V4, 1))
            } catch (e: NoSupportedApiException) {
                // There were crash reports for:
                // Exception java.lang.RuntimeException:
                // ...
                // Caused by com.nextcloud.talk.utils.NoSupportedApiException:
                // at com.nextcloud.talk.utils.ApiUtils.getConversationApiVersion (ApiUtils.kt:134)
                // at com.nextcloud.talk.contacts.ContactsRepositoryImpl.<init> (ContactsRepositoryImpl.kt:28)
                //
                // This could happen because of missing capabilities for user and should be fixed.
                // As a fallback, API v4 is guessed

                Log.e(TAG, "Failed to get an Api version for conversation.", e)
                ApiUtils.API_V4
            }

        val retrofitBucket: RetrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            version = apiVersion,
            baseUrl = _currentUser.baseUrl,
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

    override fun getImageUri(avatarId: String, requestBigSize: Boolean): String =
        ApiUtils.getUrlForAvatar(
            _currentUser.baseUrl,
            avatarId,
            requestBigSize
        )

    companion object {
        private val TAG = ContactsRepositoryImpl::class.simpleName
    }
}
