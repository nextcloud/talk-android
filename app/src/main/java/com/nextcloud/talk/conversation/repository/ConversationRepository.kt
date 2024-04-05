/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversation.repository

import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import io.reactivex.Observable

interface ConversationRepository {

    fun renameConversation(roomToken: String, roomNameNew: String): Observable<GenericOverall>

    fun createConversation(roomName: String, conversationType: Conversation.ConversationType?): Observable<RoomOverall>
}
