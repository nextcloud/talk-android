/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversation.repository

import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.conversations.RoomOverall
import io.reactivex.Observable

interface ConversationRepository {

    fun createConversation(
        roomName: String,
        conversationType: ConversationEnums.ConversationType?
    ): Observable<RoomOverall>
}
