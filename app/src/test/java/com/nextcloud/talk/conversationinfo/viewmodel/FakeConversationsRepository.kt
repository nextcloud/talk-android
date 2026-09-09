/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfo.viewmodel

import com.nextcloud.talk.conversationinfo.CreateRoomRequest
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.TalkBan
import com.nextcloud.talk.models.json.profile.Profile
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import io.reactivex.Observable

/**
 * Answers with what a test needs of [ConversationsRepository.allowGuests], and fails everything
 * else this test does not exercise.
 */
class FakeConversationsRepository : ConversationsRepository {

    var failAllowGuests = false
    var lastAllowGuestsPassword: String? = null

    override suspend fun allowGuests(
        user: User,
        url: String,
        token: String,
        allow: Boolean,
        password: String
    ): GenericOverall {
        lastAllowGuestsPassword = password
        if (allow && failAllowGuests) {
            error("the server refused to make the room public without a password")
        }
        return GenericOverall()
    }

    override fun resendInvitations(
        user: User,
        url: String
    ): Observable<ConversationsRepository.ResendInvitationsResult> = throw UnsupportedOperationException()

    override suspend fun archiveConversation(credentials: String, url: String): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun unarchiveConversation(credentials: String, url: String): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun banActor(
        credentials: String,
        url: String,
        actorType: String,
        actorId: String,
        internalNote: String
    ): TalkBan = throw UnsupportedOperationException()

    override suspend fun listBans(credentials: String, url: String): List<TalkBan> =
        throw UnsupportedOperationException()

    override suspend fun unbanActor(credentials: String, url: String): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun setPassword(user: User, url: String, password: String): GenericOverall = GenericOverall()

    override suspend fun setConversationReadOnly(user: User, url: String, state: Int): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun clearChatHistory(user: User, url: String): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun createRoom(credentials: String, url: String, body: CreateRoomRequest): RoomOverall =
        throw UnsupportedOperationException()

    override suspend fun getProfile(credentials: String, url: String): Profile? = throw UnsupportedOperationException()

    override suspend fun markConversationAsSensitive(
        credentials: String,
        baseUrl: String,
        roomToken: String
    ): GenericOverall = throw UnsupportedOperationException()

    override suspend fun markConversationAsInsensitive(
        credentials: String,
        baseUrl: String,
        roomToken: String
    ): GenericOverall = throw UnsupportedOperationException()

    override suspend fun markConversationAsImportant(
        credentials: String,
        baseUrl: String,
        roomToken: String
    ): GenericOverall = throw UnsupportedOperationException()

    override suspend fun markConversationAsUnImportant(
        credentials: String,
        baseUrl: String,
        roomToken: String
    ): GenericOverall = throw UnsupportedOperationException()

    override suspend fun markConversationAsRead(credentials: String, url: String, messageId: Int?): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun markConversationAsUnread(credentials: String, url: String): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun addConversationToFavorites(credentials: String, url: String): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun removeConversationFromFavorites(credentials: String, url: String): GenericOverall =
        throw UnsupportedOperationException()
}
