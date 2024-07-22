/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.apiService

import com.nextcloud.talk.models.json.autocomplete.AutocompleteOCS
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.generic.GenericMeta

object FakeItem {
    val contacts: List<AutocompleteUser>? =
        listOf(
            AutocompleteUser(id = "android", label = "Android", source = "users"),
            AutocompleteUser(id = "android1", label = "Android 1", source = "users"),
            AutocompleteUser(id = "android2", label = "Android 2", source = "users"),
            AutocompleteUser(id = "Benny", label = "Benny J", source = "users"),
            AutocompleteUser(id = "Benjamin", label = "Benjamin Schmidt", source = "users"),
            AutocompleteUser(id = "Chris", label = "Christoph Schmidt", source = "users"),
            AutocompleteUser(id = "Daniel", label = "Daniel H", source = "users"),
            AutocompleteUser(id = "Dennis", label = "Dennis Richard", source = "users"),
            AutocompleteUser(id = "Emma", label = "Emma Jackson", source = "users"),
            AutocompleteUser(id = "Emily", label = "Emily Jackson", source = "users"),
            AutocompleteUser(id = "Mario", label = "Mario Schmidt", source = "users"),
            AutocompleteUser(id = "Maria", label = "Maria Schmidt", source = "users"),
            AutocompleteUser(id = "Samsung", label = "Samsung A52", source = "users"),
            AutocompleteUser(id = "Tom", label = "Tom Müller", source = "users"),
            AutocompleteUser(id = "Tony", label = "Tony Baker", source = "users")
        )
    val contactsOverall = AutocompleteOverall(
        ocs = AutocompleteOCS(
            meta = GenericMeta(
                status = "ok",
                statusCode = 200,
                message = "OK"
            ),
            data = contacts
        )
    )
}
