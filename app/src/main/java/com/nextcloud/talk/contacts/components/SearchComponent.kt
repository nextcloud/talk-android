/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.components

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R

@Composable
fun SearchComponent(text: String, onTextChange: (String) -> Unit, onDisableSearch: () -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current

    TextField(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxWidth()
            .height(60.dp),
        value = text,
        onValueChange = { onTextChange(it) },
        placeholder = { Text(text = stringResource(R.string.nc_search)) },
        textStyle = TextStyle(fontSize = 16.sp),
        singleLine = true,
        leadingIcon = { LeadingIcon(onTextChange, onDisableSearch) },
        trailingIcon = { TrailingIcon(text, onTextChange) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = searchKeyboardActions(text, keyboardController),
        colors = searchTextFieldColors(),
        maxLines = 1
    )
}

@Composable
fun searchTextFieldColors() =
    TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
    )

@Composable
fun LeadingIcon(onTextChange: (String) -> Unit, onDisableSearch: () -> Unit) {
    IconButton(
        onClick = {
            onTextChange("")
            onDisableSearch()
        }
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Default.ArrowBack,
            contentDescription = stringResource(R.string.back_button)
        )
    }
}

@Composable
fun TrailingIcon(text: String, onTextChange: (String) -> Unit) {
    if (text.isNotEmpty()) {
        IconButton(
            onClick = { onTextChange("") }
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.close_icon)
            )
        }
    }
}

fun searchKeyboardActions(text: String, keyboardController: SoftwareKeyboardController?) =
    KeyboardActions(
        onSearch = {
            if (text.trim().isNotEmpty()) {
                keyboardController?.hide()
            }
        }
    )
