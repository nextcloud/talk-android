/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.apiService

import com.nextcloud.talk.models.json.autocomplete.AutocompleteOCS
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUserDto
import com.nextcloud.talk.models.json.conversations.ConversationDto
import com.nextcloud.talk.models.json.conversations.RoomOCS
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericMetaDto
import org.mockito.Mockito.mock

object FakeItem {
    val contacts: List<AutocompleteUserDto> =
        listOf(
            AutocompleteUserDto(id = "android", label = "Android", source = "users"),
            AutocompleteUserDto(id = "android1", label = "Android 1", source = "users"),
            AutocompleteUserDto(id = "android2", label = "Android 2", source = "users"),
            AutocompleteUserDto(id = "Benny", label = "Benny J", source = "users"),
            AutocompleteUserDto(id = "Benjamin", label = "Benjamin Schmidt", source = "users"),
            AutocompleteUserDto(id = "Chris", label = "Christoph Schmidt", source = "users"),
            AutocompleteUserDto(id = "Daniel", label = "Daniel H", source = "users"),
            AutocompleteUserDto(id = "Dennis", label = "Dennis Richard", source = "users"),
            AutocompleteUserDto(id = "Emma", label = "Emma Jackson", source = "users"),
            AutocompleteUserDto(id = "Emily", label = "Emily Jackson", source = "users"),
            AutocompleteUserDto(id = "Mario", label = "Mario Schmidt", source = "users"),
            AutocompleteUserDto(id = "Maria", label = "Maria Schmidt", source = "users"),
            AutocompleteUserDto(id = "Samsung", label = "Samsung A52", source = "users"),
            AutocompleteUserDto(id = "Tom", label = "Tom Müller", source = "users"),
            AutocompleteUserDto(id = "Tony", label = "Tony Baker", source = "users")
        )
    val contactsOverall = AutocompleteOverall(
        ocs = AutocompleteOCS(
            meta = GenericMetaDto(
                status = "ok",
                statusCode = 200,
                message = "OK"
            ),
            data = contacts
        )
    )
    val roomOverall: RoomOverall = RoomOverall(
        ocs = RoomOCS(
            meta = GenericMetaDto(
                status = "ok",
                statusCode = 200,
                message = "OK"
            ),
            data = mock(ConversationDto::class.java)
        )
    )
}
