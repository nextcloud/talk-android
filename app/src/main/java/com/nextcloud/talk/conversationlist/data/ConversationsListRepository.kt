/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data

import com.nextcloud.talk.data.sync.Syncable
import com.nextcloud.talk.models.domain.ConversationModel
import kotlinx.coroutines.flow.Flow

interface ConversationsListRepository : Syncable {

    fun getRooms(accountId: Long): Flow<List<ConversationModel>>
}
