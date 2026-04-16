/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.location.components

import androidx.compose.ui.text.input.TextFieldValue

data class GeocodingScreenListenerInputs(
    val onQueryChange: (TextFieldValue) -> Unit,
    val onSearch: () -> Unit,
    val onBack: () -> Unit,
    val onItemClick: (Int) -> Unit
)
