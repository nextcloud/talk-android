/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data.network

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.Conversation
import io.reactivex.Observable

interface ConversationsNetworkDataSource {
    fun getRooms(user: User, url: String, includeStatus: Boolean): Observable<List<Conversation>>
}
