/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.components

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import kotlinx.coroutines.flow.drop

/**
 * Search input meant for the title slot of a TopAppBar while search is active. [initialQuery] only sets the text the
 * field starts with; afterwards the field owns its text and reports edits through [onQueryChange]. It takes focus when
 * shown, hides the soft keyboard when removed, and both its back button and the system back gesture call
 * [onSearchDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBarSearchField(
    initialQuery: String,
    onQueryChange: (String) -> Unit,
    onSearchDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState(initialQuery)
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)

    BackHandler(onBack = onSearchDismiss)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        onDispose { keyboardController?.hide() }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .drop(1)
            .collect { currentOnQueryChange(it) }
    }

    // The TopAppBar title slot provides a title text style, which is too large for an input field.
    ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
        // A Surface instead of SearchBar: the collapsed SearchBar blocks the soft keyboard, as typing is meant to
        // happen in an expanded search bar, which is not used here.
        Surface(
            shape = SearchBarDefaults.inputFieldShape,
            color = SearchBarDefaults.colors().containerColor,
            modifier = modifier.fillMaxWidth()
        ) {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = { keyboardController?.hide() },
                placeholder = { Text(stringResource(R.string.nc_search)) },
                leadingIcon = {
                    IconButton(onClick = onSearchDismiss) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_chevron_backward_24px),
                            contentDescription = stringResource(R.string.back_button)
                        )
                    }
                },
                trailingIcon = {
                    if (textFieldState.text.isNotEmpty()) {
                        IconButton(onClick = { textFieldState.setTextAndPlaceCursorAtEnd("") }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_close_search),
                                contentDescription = stringResource(R.string.nc_search_clear)
                            )
                        }
                    }
                },
                modifier = Modifier.focusRequester(focusRequester)
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL – Arabic", showBackground = true, locale = "ar")
@Composable
private fun PreviewAppBarSearchField() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            AppBarSearchField(
                initialQuery = "design",
                onQueryChange = {},
                onSearchDismiss = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
