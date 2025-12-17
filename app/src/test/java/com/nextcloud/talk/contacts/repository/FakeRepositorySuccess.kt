/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.repository

import com.nextcloud.talk.contacts.ContactsRepository
import com.nextcloud.talk.contacts.apiService.FakeItem
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeRepositorySuccess : ContactsRepository {
    override suspend fun getContacts(user: User, searchQuery: String?, shareTypes: List<String>) =
        FakeItem.contactsOverall

    override suspend fun createRoom(
        user: User,
        roomType: String,
        sourceType: String?,
        userId: String,
        conversationName: String?
    ) = FakeItem.roomOverall

    override fun getImageUri(user: User, avatarId: String, requestBigSize: Boolean) =
        "https://mydomain.com/index.php/avatar/$avatarId/512"

    override fun getContactsFlow(user: User, searchQuery: String?): Flow<List<AutocompleteUser>> =
        flow {
            // unused atm
        }
}
