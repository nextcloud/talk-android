/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import android.content.Context
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser

@Composable
fun ContactsList(contactsUiState: ContactsUiState, contactsViewModel: ContactsViewModel, context: Context) {
    when (contactsUiState) {
        is ContactsUiState.None -> {
        }
        is ContactsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ContactsUiState.Success -> {
            val contacts = contactsUiState.contacts
            Log.d(CompanionClass.TAG, "Contacts:$contacts")
            if (contacts != null) {
                ContactsItem(contacts, contactsViewModel, context)
            }
        }
        is ContactsUiState.Error -> {
            val errorMessage = contactsUiState.message
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: $errorMessage", color = Color.Red)
            }
        }
    }
}

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
    val selectedContacts = remember { mutableStateListOf<AutocompleteUser>() }
    LazyColumn(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
        contentPadding = PaddingValues(all = 10.dp),
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
                    context = context,
                    selectedContacts = selectedContacts
                )
                Log.d(CompanionClass.TAG, "Contacts:$contact")
            }
        }
    }
}

@Composable
fun Header(header: String) {
    Text(
        text = header,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(start = 60.dp),
        color = Color.Blue,
        fontWeight = FontWeight.Bold
    )
}
