/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo.data

import com.nextcloud.talk.data.sync.Syncable
import com.nextcloud.talk.models.domain.ConversationModel
import kotlinx.coroutines.flow.Flow

interface ConversationInfoRepository : Syncable {

    /**
     * Gets the current state of the conversation
     */
    fun getRoomInfo(accountId: Long, token: String): Flow<ConversationModel>
}
