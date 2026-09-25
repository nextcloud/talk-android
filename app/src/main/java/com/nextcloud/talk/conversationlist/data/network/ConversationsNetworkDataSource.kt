/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data.network

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.ConversationDto
import io.reactivex.Observable

/**
 * The outcome of a conversation list request.
 *
 * [conversations] are the rooms the server returned, [modifiedBefore] the timestamp it reported in
 * `X-Nextcloud-Talk-Modified-Before`, or null when it sent none. [wasDelta] is true when the
 * response covers only the conversations changed since a given timestamp, and false when it covers
 * all of them - including when a `modifiedSince` was sent but the server answered in full anyway.
 */
data class RoomListResult(val conversations: List<ConversationDto>, val modifiedBefore: Long?, val wasDelta: Boolean)

interface ConversationsNetworkDataSource {
    /**
     * Loads [user]'s conversation list.
     *
     * With [includeStatus] the response carries the user status of one-to-one rooms. With
     * [modifiedSince] set, and on a server that supports it, the response covers only the
     * conversations changed since that timestamp.
     */
    fun getRooms(
        user: User,
        url: String,
        includeStatus: Boolean,
        modifiedSince: Long? = null
    ): Observable<RoomListResult>
}
