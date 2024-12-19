/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.openconversations.data

import com.nextcloud.talk.models.json.conversations.Conversation
import io.reactivex.Observable

interface OpenConversationsRepository {

    fun fetchConversations(searchTerm: String): Observable<List<Conversation>>
}
