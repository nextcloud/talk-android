/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.repository

import com.nextcloud.talk.contacts.ContactsRepository
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall
import com.nextcloud.talk.models.json.conversations.RoomOverall

class FakeRepositoryError() : ContactsRepository {
    override suspend fun getContacts(searchQuery: String?, shareTypes: List<String>): AutocompleteOverall {
        TODO("Not yet implemented")
    }

    override suspend fun createRoom(
        roomType: String,
        sourceType: String,
        userId: String,
        conversationName: String?
    ): RoomOverall {
        TODO("Not yet implemented")
    }
}
