/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui

import com.nextcloud.talk.chat.MenuItemData

enum class ChatToolbarAvatarType {
    NONE,
    URL,
    SYSTEM,
    NOTE_TO_SELF
}

data class ChatToolbarState(
    val title: String = "",
    val subtitle: String = "",
    /** Which kind of avatar (if any) to render in the toolbar. */
    val avatarType: ChatToolbarAvatarType = ChatToolbarAvatarType.NONE,
    /** Set only when [avatarType] is [ChatToolbarAvatarType.URL]. */
    val avatarUrl: String? = null,
    /** HTTP Basic / Bearer credential string for the avatar request. */
    val credentials: String? = null,
    /** "online" | "away" | "busy" | "dnd" — drives the status badge. Null = hide badge. */
    val userStatus: String? = null,
    val isSearchMode: Boolean = false,
    val isLoading: Boolean = false,
    val showVoiceCall: Boolean = false,
    val showVideoCall: Boolean = false,
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
