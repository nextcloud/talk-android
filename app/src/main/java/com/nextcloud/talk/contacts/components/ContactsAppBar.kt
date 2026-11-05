/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.components

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.components.AppBarSearchField
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUserDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsAppBar(
    state: ContactsAppBarState,
    autocompleteUsers: List<AutocompleteUserDto>,
    listenerInput: ContactsAppBarListenerInput
) {
    val context = LocalContext.current
    TopAppBar(
        navigationIcon = {
            if (!state.isSearchActive) {
                IconButton(onClick = { (context as? Activity)?.finish() }) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_back_black_24dp),
                        contentDescription = stringResource(R.string.back_button)
                    )
                }
            }
        },
        title = {
            if (state.isSearchActive) {
                AppBarSearchField(
                    initialQuery = state.searchQuery,
                    onQueryChange = listenerInput.onSearchQueryChange,
                    onSearchDismiss = listenerInput.onCloseSearch,
                    modifier = Modifier.padding(end = if (state.isAddParticipants) 0.dp else 16.dp)
                )
            } else {
                Text(
                    text = if (state.isAddParticipants) {
                        stringResource(R.string.nc_participants_add)
                    } else {
                        stringResource(R.string.nc_new_conversation)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        actions = {
            ContactsAppBarActions(
                state = state,
                autocompleteUsers = autocompleteUsers,
                listenerInput = listenerInput
            )
        }
    )
}

@Composable
private fun ContactsAppBarActions(
    state: ContactsAppBarState,
    autocompleteUsers: List<AutocompleteUserDto>,
    listenerInput: ContactsAppBarListenerInput
) {
    val context = LocalContext.current
    if (state.isSearchActive) {
        if (state.isAddParticipants) {
            TextButton(
                onClick = {
                    listenerInput.onCloseSearch()
                    listenerInput.onAddSelected()
                },
                enabled = state.enableAddButton
            ) {
                Text(text = stringResource(R.string.add_participants))
            }
        }
    } else {
        IconButton(onClick = listenerInput.onStartSearch) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_search_white_24dp),
                contentDescription = stringResource(R.string.search_icon)
            )
        }
        if (state.isAddParticipants) {
            TextButton(
                onClick = {
                    val resultIntent = Intent().apply {
                        putParcelableArrayListExtra("selectedParticipants", ArrayList(autocompleteUsers))
                    }
                    (context as? Activity)?.setResult(Activity.RESULT_OK, resultIntent)
                    (context as? Activity)?.finish()
                }
            ) {
                Text(text = stringResource(R.string.nc_contacts_done))
            }
        }
    }
}

data class ContactsAppBarState(
    val isAddParticipants: Boolean,
    val isSearchActive: Boolean,
    val searchQuery: String,
    val enableAddButton: Boolean
)

class ContactsAppBarListenerInput(
    val onStartSearch: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onCloseSearch: () -> Unit,
    val onAddSelected: () -> Unit
)

private val previewListenerInput = ContactsAppBarListenerInput(
    onStartSearch = {},
    onSearchQueryChange = {},
    onCloseSearch = {},
    onAddSelected = {}
)

@Composable
private fun ContactsAppBarPreview(state: ContactsAppBarState) {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            ContactsAppBar(
                state = state,
                autocompleteUsers = emptyList(),
                listenerInput = previewListenerInput
            )
        }
    }
}

@Preview(name = "Light Mode")
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL / Arabic", locale = "ar")
@Composable
private fun ContactsAppBarNewConversationPreview() {
    ContactsAppBarPreview(
        ContactsAppBarState(
            isAddParticipants = false,
            isSearchActive = false,
            searchQuery = "",
            enableAddButton = false
        )
    )
}

@Preview(name = "Light Mode")
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL / Arabic", locale = "ar")
@Composable
private fun ContactsAppBarAddParticipantsPreview() {
    ContactsAppBarPreview(
        ContactsAppBarState(
            isAddParticipants = true,
            isSearchActive = false,
            searchQuery = "",
            enableAddButton = false
        )
    )
}

@Preview(name = "Light Mode")
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL / Arabic", locale = "ar")
@Composable
private fun ContactsAppBarSearchPreview() {
    ContactsAppBarPreview(
        ContactsAppBarState(
            isAddParticipants = false,
            isSearchActive = true,
            searchQuery = "Alice",
            enableAddButton = false
        )
    )
}

@Preview(name = "Light Mode")
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL / Arabic", locale = "ar")
@Composable
private fun ContactsAppBarSearchAddParticipantsPreview() {
    ContactsAppBarPreview(
        ContactsAppBarState(
            isAddParticipants = true,
            isSearchActive = true,
            searchQuery = "Alice",
            enableAddButton = true
        )
    )
}
