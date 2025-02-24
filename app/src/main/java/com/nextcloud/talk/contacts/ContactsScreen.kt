/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextcloud.talk.R
import com.nextcloud.talk.contacts.components.AppBar
import com.nextcloud.talk.contacts.components.ContactsList
import com.nextcloud.talk.contacts.components.ConversationCreationOptions

@Composable
fun ContactsScreen(contactsViewModel: ContactsViewModel, uiState: ContactsUiState) {
    val context = LocalContext.current

    val searchQuery by contactsViewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearchActive by contactsViewModel.isSearchActive.collectAsStateWithLifecycle()
    val isAddParticipants by contactsViewModel.isAddParticipantsView.collectAsStateWithLifecycle()
    val autocompleteUsers by contactsViewModel.selectedParticipantsList.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(R.string.nc_app_product_name),
                searchQuery = searchQuery,
                isSearchActive = isSearchActive,
                isAddParticipants = isAddParticipants,
                autocompleteUsers = autocompleteUsers,
                onEnableSearch = {
                    contactsViewModel.setSearchActive(true)
                },
                onDisableSearch = {
                    contactsViewModel.setSearchActive(false)
                },
                onUpdateSearchQuery = {
                    contactsViewModel.updateSearchQuery(query = it)
                },
                onUpdateAutocompleteUsers = {
                    contactsViewModel.getContactsFromSearchParams()
                }
            )
        },
        content = {
            Column(
                Modifier.padding(it)
                    .background(colorResource(id = R.color.bg_default))
            ) {
                ConversationCreationOptions(
                    context = context,
                    contactsViewModel = contactsViewModel
                )
                ContactsList(
                    contactsUiState = uiState,
                    contactsViewModel = contactsViewModel,
                    context = context
                )
            }
        }
    )
}
