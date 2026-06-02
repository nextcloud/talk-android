/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui

import com.nextcloud.talk.chat.MenuItemData

data class ChatToolbarState(
    val title: String = "",
    val subtitle: String = "",
    /** Non-null only for 1-to-1 conversations; drives avatar display. */
    val avatarUrl: String? = null,
    /** HTTP Basic / Bearer credential string for the avatar request. */
    val credentials: String? = null,
    /** "online" | "away" | "busy" | "dnd" — drives the status badge. Null = hide badge. */
    val userStatus: String? = null,
    val isSearchMode: Boolean = false,
    val isLoading: Boolean = false,
    val showVoiceCall: Boolean = false,
    val showVideoCall: Boolean = false,
    /** Whether the server supports message search for this conversation. */
    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val overflowItems: List<MenuItemData> = emptyList(),
    /** Non-null in thread view; the drawable resource for the current notification level. */
    val threadNotificationIcon: Int? = null,
    val showEventMenu: Boolean = false,
    /** Whether tapping the title area should open conversation info. */
    val titleClickable: Boolean = false,
    /** Whether the server capability SILENT_CALL is available (enables long-press on call buttons). */
    val supportsSilentCall: Boolean = false
)
