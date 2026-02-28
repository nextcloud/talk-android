/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.location.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextcloud.talk.R
import com.nextcloud.talk.viewmodels.GeoCodingViewModel
import fr.dudie.nominatim.model.Address

@Composable
fun GeocodingScreen(viewModel: GeoCodingViewModel, onBack: () -> Unit, onAddressSelected: (Address) -> Unit) {
    val results by viewModel.geocodingResults.collectAsStateWithLifecycle()
    val initialQuery = viewModel.getQuery()
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (viewModel.getQuery().isNotEmpty() && results.isEmpty()) {
            viewModel.searchLocation()
        }
    }

    GeocodingScreenContent(
        query = textFieldValue,
        results = results.map { it.displayName },
        inputs = GeocodingScreenListenerInputs(
            onQueryChange = { newValue ->
                textFieldValue = newValue
                viewModel.setQuery(textFieldValue.text)
            },
            onSearch = {
                viewModel.setQuery(textFieldValue.text)
                viewModel.searchLocation()
                keyboardController?.hide()
            },
            onBack = onBack,
            onItemClick = { index -> onAddressSelected(results[index]) }
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeocodingScreenContent(
    query: TextFieldValue,
    results: List<String>,
    inputs: GeocodingScreenListenerInputs
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SearchField(
                        value = query,
                        onValueChange = inputs.onQueryChange,
                        onSearch = inputs.onSearch,
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = inputs.onBack) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.ic_arrow_back_black_24dp),
                            contentDescription = stringResource(R.string.back_button)
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(results.size) { index ->
                GeocodingResultItem(
                    displayName = results[index],
                    onClick = { inputs.onItemClick(index) }
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewGeocodingScreen() {
    MaterialTheme {
        GeocodingScreenContent(
            query = TextFieldValue("Berlin", TextRange("Berlin".length)),
            results = listOf(
                "Sonnenallee 50, Rixdorf, Neukölln, Berlin, 12055, Deutschland",
                "Alexanderplatz, Mitte, Berlin, 10178, Deutschland",
                "Brandenburger Tor, Pariser Platz, Mitte, Berlin, 10117, Deutschland"
            ),
            inputs = GeocodingScreenListenerInputs(
                onQueryChange = {},
                onSearch = {},
                onBack = {},
                onItemClick = {}
            )
        )
    }
}

@Preview(name = "RTL - Arabic", showBackground = true, locale = "ar")
@Composable
private fun PreviewGeocodingScreenRtl() {
    MaterialTheme {
        GeocodingScreenContent(
            query = TextFieldValue("الرياض", TextRange("الرياض".length)),
            results = listOf(
                "شارع الملك فهد، الرياض، المملكة العربية السعودية",
                "حي العليا، الرياض، المملكة العربية السعودية"
            ),
            inputs = GeocodingScreenListenerInputs(
                onQueryChange = {},
                onSearch = {},
                onBack = {},
                onItemClick = {}
            )
        )
    }
}
