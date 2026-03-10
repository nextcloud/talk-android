/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.profile

import androidx.annotation.DrawableRes
import com.nextcloud.talk.data.user.model.User

/** Content pane state: loading skeleton, empty placeholder, or populated list. */
sealed interface ProfileContentState {
    data object Loading : ProfileContentState
    data class Empty(val headline: String, val message: String, @DrawableRes val iconRes: Int) : ProfileContentState

    data object ShowList : ProfileContentState
}

/**
 * Immutable snapshot of every value the Profile screen needs to render.
 * The Activity drives changes by writing a new copy via `profileUiState = profileUiState.copy(…)`.
 */
data class ProfileUiState(
    val displayName: String = "",
    val baseUrl: String = "",
    val currentUser: User? = null,
    /** Increment to tell the avatar AndroidView to reload (e.g. after upload/delete). */
    val avatarRefreshKey: Int = 0,
    val isEditMode: Boolean = false,
    val showAvatarButtons: Boolean = false,
    val showProfileEnabledCard: Boolean = false,
    val isProfileEnabled: Boolean = false,
    val contentState: ProfileContentState = ProfileContentState.Loading,
    /** Full list (all fields, used in edit mode). */
    val items: List<ProfileActivity.UserInfoDetailsItem> = emptyList(),
    /** Filtered list (non-empty fields only, used in view mode). */
    val filteredItems: List<ProfileActivity.UserInfoDetailsItem> = emptyList(),
    val editableFields: List<String> = emptyList()
)

/** Callbacks from the Profile screen UI back to the Activity. */
data class ProfileCallbacks(
    val onNavigateBack: () -> Unit = {},
    val onEditSave: () -> Unit = {},
    val onAvatarUploadClick: () -> Unit = {},
    val onAvatarChooseClick: () -> Unit = {},
    val onAvatarCameraClick: () -> Unit = {},
    val onAvatarDeleteClick: () -> Unit = {},
    val onProfileEnabledChange: (Boolean) -> Unit = {},
    val onTextChange: (position: Int, newText: String) -> Unit = { _, _ -> },
    val onScopeClick: (position: Int, field: ProfileActivity.Field) -> Unit = { _, _ -> }
)
