/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.location.components

data class GeocodingScreenListenerInputs(
    val onQueryChange: (String) -> Unit,
    val onSearch: () -> Unit,
    val onBack: () -> Unit,
    val onItemClick: (Int) -> Unit
)
