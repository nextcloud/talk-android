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
import io.reactivex.Observable
import java.io.File

interface ConversationInfoEditRepository {

    fun uploadConversationAvatar(
        credentials: String?,
        url: String,
        user: User,
        file: File,
        roomToken: String
    ): Observable<ConversationModel>

    fun deleteConversationAvatar(
        credentials: String?,
        url: String,
        user: User,
        roomToken: String
    ): Observable<ConversationModel>

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
