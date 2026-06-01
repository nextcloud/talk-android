/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui

data class ChatToolbarCallbacks(
    val onNavigateUp: () -> Unit = {},
    val onTitleClick: () -> Unit = {},
    val onVoiceCall: () -> Unit = {},
    val onSilentVoiceCall: () -> Unit = {},
    val onVideoCall: () -> Unit = {},
    val onSilentVideoCall: () -> Unit = {},
    val onSearchOpen: () -> Unit = {},
    val onSearchClose: () -> Unit = {},
    val onSearchQueryChange: (String) -> Unit = {},
    val onSearchSubmit: () -> Unit = {},
    val onSearchPrevious: () -> Unit = {},
    val onSearchNext: () -> Unit = {},
    val onThreadNotification: () -> Unit = {},
    val onEventMenu: () -> Unit = {}
)
