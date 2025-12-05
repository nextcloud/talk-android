/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.repositories.conversations

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.conversationinfo.CreateRoomRequest
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.TalkBan
import com.nextcloud.talk.models.json.profile.Profile
import com.nextcloud.talk.repositories.conversations.ConversationsRepository.ResendInvitationsResult
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observable

class ConversationsRepositoryImpl(private val api: NcApi, private val coroutineApi: NcApiCoroutines) :
    ConversationsRepository {
    override suspend fun allowGuests(user: User, url: String, token: String, allow: Boolean): GenericOverall {
        val credentials = ApiUtils.getCredentials(user.username, user.token)!!
        val result: GenericOverall = if (allow) {
            coroutineApi.makeRoomPublic(
                credentials,
                url
            )
        } else {
            coroutineApi.makeRoomPrivate(
                credentials,
                url
            )
        }
        return result
    }

    override fun resendInvitations(user: User, url: String): Observable<ResendInvitationsResult> {
        val credentials = ApiUtils.getCredentials(user.username, user.token)!!
        val apiObservable = api.resendParticipantInvitations(
            credentials,
            url
        )
        return apiObservable.map {
            ResendInvitationsResult(true)
        }
    }

    override suspend fun archiveConversation(credentials: String, url: String): GenericOverall =
        coroutineApi.archiveConversation(credentials, url)

    override suspend fun unarchiveConversation(credentials: String, url: String): GenericOverall =
        coroutineApi.unarchiveConversation(credentials, url)

    override suspend fun setConversationReadOnly(user: User, url: String, state: Int): GenericOverall {
        val credentials = ApiUtils.getCredentials(user.username, user.token)!!
        val result = coroutineApi.setConversationReadOnly(
            credentials,
            url,
            state
        )
        return result
    }

    override suspend fun setPassword(user: User, url: String, password: String): GenericOverall {
        val credentials = ApiUtils.getCredentials(user.username, user.token)!!
        val result = coroutineApi.setPassword(
            credentials,
            url,
            password
        )
        return result
    }

    override suspend fun clearChatHistory(user: User, url: String): GenericOverall {
        val credentials = ApiUtils.getCredentials(user.username, user.token)!!
        val result = coroutineApi.clearChatHistory(
            credentials,
            url
        )
        return result
    }

    override suspend fun createRoom(credentials: String, url: String, body: CreateRoomRequest): RoomOverall {
        val response = coroutineApi.createRoomWithBody(
            credentials,
            url,
            body
        )
        return response
    }

    override suspend fun getProfile(credentials: String, url: String): Profile? =
        coroutineApi.getProfile(credentials, url).ocs?.data

    override suspend fun markConversationAsSensitive(
        credentials: String,
        baseUrl: String,
        roomToken: String
    ): GenericOverall {
        val url = ApiUtils.getUrlForSensitiveConversation(baseUrl, roomToken)
        return coroutineApi.markConversationAsSensitive(credentials, url)
    }

    override suspend fun markConversationAsInsensitive(
        credentials: String,
        baseUrl: String,
        roomToken: String
    ): GenericOverall {
        val url = ApiUtils.getUrlForSensitiveConversation(baseUrl, roomToken)
        return coroutineApi.markConversationAsInsensitive(credentials, url)
    }

    override suspend fun markConversationAsImportant(
        credentials: String,
        baseUrl: String,
        roomToken: String
    ): GenericOverall {
        val url = ApiUtils.getUrlForImportantConversation(baseUrl, roomToken)
        return coroutineApi.markConversationAsImportant(credentials, url)
    }

    override suspend fun markConversationAsUnImportant(
        credentials: String,
        baseUrl: String,
        roomToken: String
    ): GenericOverall {
        val url = ApiUtils.getUrlForImportantConversation(baseUrl, roomToken)
        return coroutineApi.markConversationAsUnimportant(credentials, url)
    }

    override suspend fun banActor(
        credentials: String,
        url: String,
        actorType: String,
        actorId: String,
        internalNote: String
    ): TalkBan = coroutineApi.banActor(credentials, url, actorType, actorId, internalNote)

    override suspend fun listBans(credentials: String, url: String): List<TalkBan> {
        val talkBanOverall = coroutineApi.listBans(credentials, url)
        return talkBanOverall.ocs?.data!!
    }

    override suspend fun unbanActor(credentials: String, url: String): GenericOverall =
        coroutineApi.unbanActor(credentials, url)

    companion object {
        const val STATUS_CODE_OK = 200
    }
}
