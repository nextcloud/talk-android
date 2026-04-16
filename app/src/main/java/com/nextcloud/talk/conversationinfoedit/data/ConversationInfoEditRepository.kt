/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfoedit.data

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.generic.GenericOverall
import java.io.File

interface ConversationInfoEditRepository {

    suspend fun getRoom(user: User, roomToken: String): ConversationInfoEditRoomData

    suspend fun uploadConversationAvatar(user: User, roomToken: String, file: File): ConversationModel

    suspend fun deleteConversationAvatar(user: User, roomToken: String): ConversationModel

    suspend fun renameConversation(user: User, roomToken: String, newRoomName: String): GenericOverall

    suspend fun setConversationDescription(
        user: User,
        roomToken: String,
        conversationDescription: String?
    ): GenericOverall
}
