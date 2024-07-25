/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.repository

import com.nextcloud.talk.contacts.ContactsRepository
import com.nextcloud.talk.contacts.apiService.FakeItem
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall
import com.nextcloud.talk.models.json.conversations.RoomOverall

class FakeRepositorySuccess() : ContactsRepository {
    override suspend fun getContacts(searchQuery: String?, shareTypes: List<String>): AutocompleteOverall {
        return FakeItem.contactsOverall
    }

    override suspend fun createRoom(
        roomType: String,
        sourceType: String,
        userId: String,
        conversationName: String?
    ): RoomOverall {
        return FakeItem.roomOverall
    }

    override fun getImageUri(avatarId: String, requestBigSize: Boolean): String {
        return "https://mydomain.com/index.php/avatar/$avatarId/512"
    }
}
