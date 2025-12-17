/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kota@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.repository

import com.nextcloud.talk.contacts.ContactsRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.conversations.RoomOverall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeRepositoryError : ContactsRepository {
    @Suppress("Detekt.TooGenericExceptionThrown")
    override suspend fun getContacts(user: User, searchQuery: String?, shareTypes: List<String>): AutocompleteOverall =
        throw Exception("unable to fetch contacts")

    @Suppress("Detekt.TooGenericExceptionThrown")
    override suspend fun createRoom(
        user: User,
        roomType: String,
        sourceType: String?,
        userId: String,
        conversationName: String?
    ): RoomOverall = throw Exception("unable to create room")

    override fun getImageUri(user: User, avatarId: String, requestBigSize: Boolean) =
        "https://mydoman.com/index.php/avatar/$avatarId/512"

    override fun getContactsFlow(user: User, searchQuery: String?): Flow<List<AutocompleteUser>> =
        flow {
            // unused atm
        }
}
