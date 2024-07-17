/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.apiService

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall
import com.nextcloud.talk.models.json.conversations.RoomOverall

class FakeApiService() : NcApiCoroutines {
    override suspend fun getContactsWithSearchParam(
        authorization: String?,
        url: String?,
        listOfShareTypes: List<String>?,
        options: Map<String, Any>?
    ): AutocompleteOverall {
        TODO("Not yet implemented")
    }

    override suspend fun createRoom(authorization: String?, url: String?, options: Map<String, String>?): RoomOverall {
        TODO("Not yet implemented")
    }
}
