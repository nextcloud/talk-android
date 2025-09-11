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
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextcloud.talk.R
import com.nextcloud.talk.contacts.components.ContactsAppBar
import com.nextcloud.talk.contacts.components.ContactsList
import com.nextcloud.talk.contacts.components.ContactsSearchAppBar
import com.nextcloud.talk.contacts.components.ConversationCreationOptions

@Composable
fun ContactsScreen(contactsViewModel: ContactsViewModel, uiState: ContactsUiState) {
    val searchQuery by contactsViewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearchActive by contactsViewModel.isSearchActive.collectAsStateWithLifecycle()
    val isAddParticipants by contactsViewModel.isAddParticipantsView.collectAsStateWithLifecycle()
    val autocompleteUsers by contactsViewModel.selectedParticipantsList.collectAsStateWithLifecycle()
    val enableAddButton by contactsViewModel.enableAddButton.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .statusBarsPadding()
            .displayCutoutPadding(),
        topBar = {
            if (isSearchActive) {
                ContactsSearchAppBar(
                    searchQuery = searchQuery,
                    onTextChange = {
                        contactsViewModel.updateSearchQuery(it)
                        contactsViewModel.getContactsFromSearchParams()
                    },
                    onCloseSearch = {
                        contactsViewModel.updateSearchQuery("")
                        contactsViewModel.setSearchActive(false)
                        contactsViewModel.getContactsFromSearchParams()
                    },
                    enableAddButton = enableAddButton,
                    isAddParticipants = isAddParticipants,
                    clickAddButton = { contactsViewModel.modifyClickAddButton(true) }
                )
            } else {
                ContactsAppBar(
                    isAddParticipants = isAddParticipants,
                    autocompleteUsers = autocompleteUsers,
                    onStartSearch = { contactsViewModel.setSearchActive(true) }
                )
            }
        },
        content = { paddingValues ->
            Column(
                Modifier
                    .background(colorResource(id = R.color.bg_default))
                    .padding(0.dp, paddingValues.calculateTopPadding(), 0.dp, paddingValues.calculateBottomPadding())
            ) {
                if (!isAddParticipants) {
                    ConversationCreationOptions()
                }

                ContactsList(
                    contactsUiState = uiState,
                    contactsViewModel = contactsViewModel
                )
            }
        }
    )
}
