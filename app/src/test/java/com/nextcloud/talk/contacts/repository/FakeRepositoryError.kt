/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kota@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.repository

import com.nextcloud.talk.contacts.ContactsRepository
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall
import com.nextcloud.talk.models.json.conversations.RoomOverall

class FakeRepositoryError : ContactsRepository {
    @Suppress("Detekt.TooGenericExceptionThrown")
    override suspend fun getContacts(searchQuery: String?, shareTypes: List<String>): AutocompleteOverall =
        throw Exception("unable to fetch contacts")

    @Suppress("Detekt.TooGenericExceptionThrown")
    override suspend fun createRoom(
        roomType: String,
        sourceType: String?,
        userId: String,
        conversationName: String?
    ): RoomOverall = throw Exception("unable to create room")

    override fun getImageUri(avatarId: String, requestBigSize: Boolean) =
        "https://mydoman.com/index.php/avatar/$avatarId/512"
}
