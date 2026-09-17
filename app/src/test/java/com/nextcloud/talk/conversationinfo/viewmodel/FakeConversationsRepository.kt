/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfo.viewmodel

import com.nextcloud.talk.conversationinfo.CreateRoomRequestDto
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOCS
import com.nextcloud.talk.models.json.generic.GenericMetaDto
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.TalkBanDto
import com.nextcloud.talk.models.json.profile.ProfileDto
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import io.reactivex.Observable
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException

/**
 * Answers with what a test needs of [ConversationsRepository.allowGuests], and fails everything
 * else this test does not exercise.
 */
class FakeConversationsRepository : ConversationsRepository {

    var failAllowGuests = false
    var lastAllowGuestsPassword: String? = null

    /** How many of the next important-conversation requests fail with a connection problem. */
    var failingImportantRequests = 0
    var importantRequests = 0

    /** The OCS status code the important-conversation requests answer with. */
    var importantResponseStatusCode = 200

    /** When set, the next important-conversation request waits for this before it answers. */
    var importantGate: CompletableDeferred<Unit>? = null

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
    ): TalkBanDto = throw UnsupportedOperationException()

    override suspend fun listBans(credentials: String, url: String): List<TalkBanDto> =
        throw UnsupportedOperationException()

    override suspend fun unbanActor(credentials: String, url: String): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun setPassword(user: User, url: String, password: String): GenericOverall = GenericOverall()

    override suspend fun setConversationReadOnly(user: User, url: String, state: Int): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun clearChatHistory(user: User, url: String): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun createRoom(credentials: String, url: String, body: CreateRoomRequestDto): RoomOverall =
        throw UnsupportedOperationException()

    override suspend fun getProfile(credentials: String, url: String): ProfileDto? =
        throw UnsupportedOperationException()

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
    ): GenericOverall {
        importantRequests++
        importantGate?.let { gate ->
            importantGate = null
            gate.await()
        }
        if (failingImportantRequests > 0) {
            failingImportantRequests--
            throw IOException("no connection")
        }
        return GenericOverall(GenericOCS(GenericMetaDto(statusCode = importantResponseStatusCode)))
    }

    override suspend fun markConversationAsUnImportant(
        credentials: String,
        baseUrl: String,
        roomToken: String
    ): GenericOverall {
        importantRequests++
        if (failingImportantRequests > 0) {
            failingImportantRequests--
            throw IOException("no connection")
        }
        return GenericOverall(GenericOCS(GenericMetaDto(statusCode = importantResponseStatusCode)))
    }

    override suspend fun markConversationAsRead(credentials: String, url: String, messageId: Int?): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun markConversationAsUnread(credentials: String, url: String): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun addConversationToFavorites(credentials: String, url: String): GenericOverall =
        throw UnsupportedOperationException()

    override suspend fun removeConversationFromFavorites(credentials: String, url: String): GenericOverall =
        throw UnsupportedOperationException()
}
