/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.components.VerticallyCenteredRow

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsSearchAppBar(
    searchQuery: String,
    onTextChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    enableAddButton: Boolean,
    isAddParticipants: Boolean,
    clickAddButton: (Boolean) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = Modifier.height(60.dp)
    ) {
        VerticallyCenteredRow {
            IconButton(
                modifier = Modifier.padding(start = 4.dp),
                onClick = onCloseSearch
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_button)
                )
            }

            TextField(
                value = searchQuery,
                onValueChange = onTextChange,
                placeholder = { Text(text = stringResource(R.string.nc_search)) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = searchKeyboardActions(searchQuery, keyboardController),
                colors = searchTextFieldColors(),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onTextChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.nc_search_clear)
                            )
                        }
                    }
                }
            )

            if (isAddParticipants) {
                TextButton(
                    onClick = {
                        onCloseSearch()
                        clickAddButton(true)
                    },
                    enabled = enableAddButton
                ) {
                    Text(text = stringResource(R.string.add_participants))
                }
            }
        }
    }
}

@Composable
fun searchTextFieldColors() =
    TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
    )

fun searchKeyboardActions(text: String, keyboardController: SoftwareKeyboardController?) =
    KeyboardActions(
        onSearch = {
            if (text.trim().isNotEmpty()) {
                keyboardController?.hide()
            }
        }
    )
