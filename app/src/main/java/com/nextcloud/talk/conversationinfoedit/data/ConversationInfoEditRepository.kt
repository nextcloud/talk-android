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

    fun uploadConversationAvatar(user: User, file: File, roomToken: String): Observable<ConversationModel>

    fun deleteConversationAvatar(user: User, roomToken: String): Observable<ConversationModel>

    suspend fun renameConversation(roomToken: String, roomNameNew: String): GenericOverall

    suspend fun setConversationDescription(roomToken: String, conversationDescription: String?): GenericOverall
}
