/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R

@Composable
fun DisplaySearch(text: String, onTextChange: (String) -> Unit, contactsViewModel: ContactsViewModel) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)

    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            value = text,
            onValueChange = { onTextChange(it) },
            placeholder = {
                Text(
                    text = stringResource(R.string.nc_search),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },

            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            ),
            singleLine = true,
            leadingIcon = {
                IconButton(
                    onClick = {
                        onTextChange("")
                        contactsViewModel.updateSearchState(false)
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back_button),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },

            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onTextChange("")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_icon),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },

            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),

            keyboardActions = KeyboardActions(
                onSearch = {
                    if (text.trim().isNotEmpty()) {
                        keyboardController?.hide()
                    } else {
                        return@KeyboardActions
                    }
                }
            ),
            maxLines = 1,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledContainerColor = Color.White,
                focusedTextColor = Color.Black,
                cursorColor = Color.Black
            )
        )
    }
}
