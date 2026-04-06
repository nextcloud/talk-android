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

    suspend fun getRoom(credentials: String, url: String, user: User): ConversationModel

    suspend fun uploadConversationAvatar(
        credentials: String?,
        url: String,
        user: User,
        file: File,
        roomToken: String
    ): ConversationModel

    suspend fun deleteConversationAvatar(
        credentials: String?,
        url: String,
        user: User,
        roomToken: String
    ): ConversationModel

    suspend fun renameConversation(
        credentials: String?,
        url: String,
        roomToken: String,
        newRoomName: String
    ): GenericOverall

    suspend fun setConversationDescription(
        credentials: String?,
        url: String,
        roomToken: String,
        conversationDescription: String?
    ): GenericOverall
}
