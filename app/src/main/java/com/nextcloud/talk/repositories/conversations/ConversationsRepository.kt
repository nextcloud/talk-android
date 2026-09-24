/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.repositories.conversations

import com.nextcloud.talk.conversationinfo.CreateRoomRequestDto
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.TalkBanDto
import com.nextcloud.talk.models.json.profile.ProfileDto
import io.reactivex.Observable

interface ConversationsRepository {

    suspend fun allowGuests(
        user: User,
        url: String,
        token: String,
        allow: Boolean,
        password: String = ""
    ): GenericOverall

    data class ResendInvitationsResult(val successful: Boolean)
    fun resendInvitations(user: User, url: String): Observable<ResendInvitationsResult>

    suspend fun archiveConversation(credentials: String, url: String): GenericOverall

    suspend fun unarchiveConversation(credentials: String, url: String): GenericOverall

    suspend fun banActor(
        credentials: String,
        url: String,
        actorType: String,
        actorId: String,
        internalNote: String
    ): TalkBanDto

    suspend fun listBans(credentials: String, url: String): List<TalkBanDto>
    suspend fun unbanActor(credentials: String, url: String): GenericOverall

    suspend fun setPassword(user: User, url: String, password: String): GenericOverall

    suspend fun setConversationReadOnly(user: User, url: String, state: Int): GenericOverall

    suspend fun clearChatHistory(user: User, url: String): GenericOverall

    suspend fun createRoom(credentials: String, url: String, body: CreateRoomRequestDto): RoomOverall

    suspend fun getProfile(credentials: String, url: String): ProfileDto?

    suspend fun markConversationAsSensitive(credentials: String, baseUrl: String, roomToken: String): GenericOverall

    suspend fun markConversationAsInsensitive(credentials: String, baseUrl: String, roomToken: String): GenericOverall

    suspend fun markConversationAsImportant(credentials: String, baseUrl: String, roomToken: String): GenericOverall

    suspend fun markConversationAsUnImportant(credentials: String, baseUrl: String, roomToken: String): GenericOverall

    suspend fun markConversationAsRead(credentials: String, url: String, messageId: Int?): GenericOverall

    suspend fun markConversationAsUnread(credentials: String, url: String): GenericOverall

    suspend fun addConversationToFavorites(credentials: String, url: String): GenericOverall

    suspend fun removeConversationFromFavorites(credentials: String, url: String): GenericOverall
}
