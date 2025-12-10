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

interface ReactionsRepository {

    @Suppress("LongParameterList")
    suspend fun addReaction(
        credentials: String?,
        userId: Long,
        url: String,
        roomToken: String,
        message: ChatMessage,
        emoji: String
    ): ReactionAddedModel

    @Suppress("LongParameterList")
    suspend fun deleteReaction(
        credentials: String?,
        userId: Long,
        url: String,
        roomToken: String,
        message: ChatMessage,
        emoji: String
    ): ReactionDeletedModel
}
