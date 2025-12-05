/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall
import com.nextcloud.talk.models.json.conversations.RoomOverall

interface ContactsRepository {
    suspend fun getContacts(user: User, searchQuery: String?, shareTypes: List<String>): AutocompleteOverall

    suspend fun createRoom(
        user: User,
        roomType: String,
        sourceType: String?,
        userId: String,
        conversationName: String?
    ): RoomOverall

    fun getImageUri(user: User, avatarId: String, requestBigSize: Boolean): String
}
