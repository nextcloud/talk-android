/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.contacts.CompanionClass
import com.nextcloud.talk.contacts.ContactsViewModel
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsItem(contacts: List<AutocompleteUser>, contactsViewModel: ContactsViewModel, context: Context) {
    val groupedContacts: Map<String, List<AutocompleteUser>> = contacts.groupBy { contact ->
        (
            if (contact.source == "users") {
                contact.label?.first()?.uppercase()
            } else {
                contact.source?.replaceFirstChar { actorType ->
                    actorType.uppercase()
                }
            }
            ).toString()
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth(),
        contentPadding = PaddingValues(
            top = 10.dp,
            bottom = 40.dp,
            start = 10.dp,
            end = 10.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        groupedContacts.forEach { (initial, contactsForInitial) ->
            stickyHeader {
                Column {
                    Surface(Modifier.fillParentMaxWidth()) {
                        Header(initial)
                    }
                    HorizontalDivider(thickness = 0.1.dp, color = Color.Black)
                }
            }
            items(contactsForInitial) { contact ->
                ContactItemRow(
                    contact = contact,
                    contactsViewModel = contactsViewModel,
                    context = context
                )
                Log.d(CompanionClass.TAG, "Contacts:$contact")
            }
        }
    }
}
