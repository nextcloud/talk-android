/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation

import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.AddParticipantOverall
import java.io.File

interface ConversationCreationRepository {

    suspend fun allowGuests(token: String, allow: Boolean): GenericOverall
    suspend fun renameConversation(roomToken: String, roomNameNew: String?): GenericOverall
    suspend fun setConversationDescription(roomToken: String, description: String?): GenericOverall
    suspend fun openConversation(roomToken: String, scope: Int): GenericOverall
    suspend fun addParticipants(conversationToken: String?, userId: String, sourceType: String): AddParticipantOverall
    suspend fun createRoom(roomType: String, conversationName: String?): RoomOverall
    fun getImageUri(avatarId: String, requestBigSize: Boolean): String
    suspend fun setPassword(roomToken: String, password: String): GenericOverall
    suspend fun uploadConversationAvatar(file: File, roomToken: String): ConversationModel
    suspend fun deleteConversationAvatar(roomToken: String): ConversationModel
}
