/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.repositories.reactions

import com.nextcloud.talk.models.domain.ReactionAddedModel
import com.nextcloud.talk.models.domain.ReactionDeletedModel
import com.nextcloud.talk.chat.data.model.ChatMessage
import io.reactivex.Observable

interface ReactionsRepository {

    fun addReaction(roomToken: String, message: ChatMessage, emoji: String): Observable<ReactionAddedModel>

    fun deleteReaction(roomToken: String, message: ChatMessage, emoji: String): Observable<ReactionDeletedModel>
}
