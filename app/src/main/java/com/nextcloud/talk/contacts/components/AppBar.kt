/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(
    title: String,
    searchQuery: String,
    isSearchActive: Boolean,
    isAddParticipants: Boolean,
    autocompleteUsers: List<AutocompleteUser>,
    onEnableSearch: () -> Unit,
    onDisableSearch: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onUpdateAutocompleteUsers: () -> Unit
) {
    val context = LocalContext.current

    TopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            IconButton(onClick = {
                (context as? Activity)?.finish()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
            }
        },
        actions = {
            IconButton(onClick = onEnableSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_icon))
            }
            if (isAddParticipants) {
                Text(
                    text = stringResource(id = R.string.nc_contacts_done),
                    modifier = Modifier.clickable {
                        val resultIntent = Intent().apply {
                            putParcelableArrayListExtra(
                                "selectedParticipants",
                                ArrayList(autocompleteUsers)
                            )
                        }
                        (context as? Activity)?.setResult(Activity.RESULT_OK, resultIntent)
                        (context as? Activity)?.finish()
                    }
                )
            }
        }
    )
    if (isSearchActive) {
        Row {
            SearchComponent(
                text = searchQuery,
                onTextChange = { searchQuery ->
                    onUpdateSearchQuery(searchQuery)
                    onUpdateAutocompleteUsers()
                },
                onDisableSearch = onDisableSearch
            )
        }
    }
}
