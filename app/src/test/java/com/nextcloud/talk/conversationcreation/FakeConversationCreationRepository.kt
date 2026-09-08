/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationcreation

import com.nextcloud.talk.conversationcreation.data.ConversationCreationRepository
import com.nextcloud.talk.conversationinfo.CreateRoomRequest
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.RetrofitBucket
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationPreset
import com.nextcloud.talk.models.json.conversations.RoomOCS
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.AddParticipantOverall
import java.io.File

/**
 * Records what [ConversationCreator] asks of the server and answers with what a test needs.
 */
class FakeConversationCreationRepository(private val token: String = "abc123") : ConversationCreationRepository {

    var bodyRequest: CreateRoomRequest? = null
    var formRequest: RetrofitBucket? = null
    var passwordCalls = 0
    var descriptionCalls = 0
    var listableCalls = 0
    val addedParticipants = mutableListOf<String>()

    var failPassword = false
    var failingParticipants = emptySet<String>()

    override suspend fun getConversationPresets(credentials: String?, url: String): List<ConversationPreset> =
        emptyList()

    override suspend fun createRoomWithBody(credentials: String?, url: String, body: CreateRoomRequest): RoomOverall {
        bodyRequest = body
        return roomOverall(hasPassword = body.password?.isNotEmpty() == true)
    }

    override suspend fun createRoom(credentials: String?, retrofitBucket: RetrofitBucket): RoomOverall {
        formRequest = retrofitBucket
        return roomOverall(hasPassword = false)
    }

    override suspend fun setPassword(
        credentials: String?,
        url: String,
        roomToken: String,
        password: String
    ): GenericOverall {
        passwordCalls++
        if (failPassword) {
            error("password rejected")
        }
        return GenericOverall()
    }

    override suspend fun setConversationDescription(
        credentials: String?,
        url: String,
        roomToken: String,
        description: String?
    ): GenericOverall {
        descriptionCalls++
        return GenericOverall()
    }

    override suspend fun openConversation(
        credentials: String?,
        url: String,
        roomToken: String,
        scope: Int
    ): GenericOverall {
        listableCalls++
        return GenericOverall()
    }

    override suspend fun addParticipants(credentials: String?, retrofitBucket: RetrofitBucket): AddParticipantOverall {
        val id = retrofitBucket.queryMap?.get("newParticipant").orEmpty()
        if (id in failingParticipants) {
            error("participant rejected")
        }
        addedParticipants.add(id)
        return AddParticipantOverall()
    }

    override suspend fun uploadConversationAvatar(
        credentials: String?,
        user: User,
        url: String,
        file: File,
        roomToken: String
    ): ConversationModel = throw UnsupportedOperationException()

    override suspend fun setConversationEmojiAvatar(
        credentials: String?,
        url: String,
        emoji: String,
        color: String?
    ): RoomOverall = throw UnsupportedOperationException()

    override suspend fun allowGuests(credentials: String?, url: String, token: String, allow: Boolean): GenericOverall =
        GenericOverall()

    private fun roomOverall(hasPassword: Boolean) =
        RoomOverall(RoomOCS(null, Conversation(token = token, hasPassword = hasPassword)))
}
