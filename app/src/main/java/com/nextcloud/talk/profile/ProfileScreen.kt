/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.profile

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.userprofile.Scope

private const val FULL_WEIGHT = 1f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(state: ProfileUiState, callbacks: ProfileCallbacks, modifier: Modifier = Modifier) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Scaffold(
        topBar = { ProfileTopBar(state, callbacks) },
        // safeDrawing insets: top consumed by TopAppBar, bottom passed to the LazyColumn as
        // contentPadding so items scroll behind — not above — the navigation bar.
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier
    ) { innerPadding ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AvatarSection(
                    state = state,
                    callbacks = callbacks,
                    modifier = Modifier.weight(FULL_WEIGHT)
                )
                ProfileContentPane(
                    state = state,
                    callbacks = callbacks,
                    modifier = Modifier.weight(FULL_WEIGHT)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Only top padding here; bottom is handled per-content so the list can
                    // draw behind the navigation bar.
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                AvatarSection(
                    state = state,
                    callbacks = callbacks,
                    modifier = Modifier.fillMaxWidth()
                )
                // weight(1f) — fills the space that remains after the avatar section.
                ProfileContentPane(
                    state = state,
                    callbacks = callbacks,
                    modifier = Modifier.weight(FULL_WEIGHT),
                    bottomPadding = innerPadding.calculateBottomPadding()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTopBar(state: ProfileUiState, callbacks: ProfileCallbacks) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.nc_profile_personal_info_title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = callbacks.onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back_button)
                )
            }
        },
        actions = {
            if (state.editableFields.isNotEmpty()) {
                IconButton(onClick = callbacks.onEditSave) {
                    Icon(
                        painter = painterResource(
                            if (state.isEditMode) R.drawable.ic_check else R.drawable.ic_edit
                        ),
                        contentDescription = stringResource(
                            if (state.isEditMode) R.string.save else R.string.edit
                        )
                    )
                }
            }
        }
    )
}

@Preview(name = "Light", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewLight() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        ProfileScreen(state = previewState, callbacks = previewCallbacks)
    }
}

@Preview(name = "Dark", showBackground = true, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewDark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        ProfileScreen(state = previewState, callbacks = previewCallbacks)
    }
}

@Preview(
    name = "Landscape · Light",
    showBackground = true,
    widthDp = 891,
    heightDp = 411
)
@Preview(
    name = "Landscape · Dark",
    showBackground = true,
    widthDp = 891,
    heightDp = 411,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewLandscape() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        ProfileScreen(state = previewState, callbacks = previewCallbacks)
    }
}

@Preview(name = "RTL · Arabic", showBackground = true, showSystemUi = true, locale = "ar")
@Composable
private fun PreviewRtlArabic() {
    val arabicItems = listOf(
        ProfileActivity.UserInfoDetailsItem(
            R.drawable.ic_user,
            "جون دو",
            "الاسم الكامل",
            ProfileActivity.Field.DISPLAYNAME,
            Scope.PRIVATE
        ),
        ProfileActivity.UserInfoDetailsItem(
            R.drawable.ic_email,
            "john@example.com",
            "بريد إلكتروني",
            ProfileActivity.Field.EMAIL,
            Scope.LOCAL
        ),
        ProfileActivity.UserInfoDetailsItem(
            R.drawable.ic_phone,
            "٠١٢٣ ٤٥٦ ٧٨٩ ١٢",
            "هاتف",
            ProfileActivity.Field.PHONE,
            Scope.FEDERATED
        ),
        ProfileActivity.UserInfoDetailsItem(
            R.drawable.ic_map_marker,
            "برلين، ألمانيا",
            "العنوان",
            ProfileActivity.Field.ADDRESS,
            Scope.PUBLISHED
        ),
        ProfileActivity.UserInfoDetailsItem(
            R.drawable.ic_web,
            "nextcloud.com",
            "الموقع الإلكتروني",
            ProfileActivity.Field.WEBSITE,
            Scope.PRIVATE
        )
    )
    MaterialTheme(colorScheme = lightColorScheme()) {
        ProfileScreen(
            state = ProfileUiState(
                displayName = "جون دو",
                baseUrl = "nextcloud.example.com",
                contentState = ProfileContentState.ShowList,
                items = arabicItems,
                filteredItems = arabicItems,
                editableFields = listOf("email", "phone")
            ),
            callbacks = previewCallbacks
        )
    }
}

@Preview(name = "Loading", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewLoading() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        ProfileScreen(
            state = ProfileUiState(contentState = ProfileContentState.Loading),
            callbacks = previewCallbacks
        )
    }
}

@Preview(name = "Edit · Light", showBackground = true, showSystemUi = true)
@Preview(name = "Edit · Dark", showBackground = true, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewEditMode() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        ProfileScreen(
            state = previewState.copy(
                isEditMode = true,
                showAvatarButtons = true,
                showProfileEnabledCard = true,
                isProfileEnabled = true,
                editableFields = listOf("email", "phone", "address", "website")
            ),
            callbacks = previewCallbacks
        )
    }
}

@Preview(name = "Empty · Light", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewEmptyState() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface {
            ProfileScreen(
                state = ProfileUiState(
                    displayName = "John Doe",
                    baseUrl = "nextcloud.example.com",
                    contentState = ProfileContentState.Empty(
                        headline = "No info set",
                        message = "You have not filled in any profile information yet.",
                        iconRes = R.drawable.ic_user
                    )
                ),
                callbacks = previewCallbacks
            )
        }
    }
}
private val sampleItems = listOf(
    ProfileActivity.UserInfoDetailsItem(
        R.drawable.ic_user,
        "John Doe",
        "Display name",
        ProfileActivity.Field.DISPLAYNAME,
        Scope.PRIVATE
    ),
    ProfileActivity.UserInfoDetailsItem(
        R.drawable.ic_email,
        "john@example.com",
        "Email",
        ProfileActivity.Field.EMAIL,
        Scope.LOCAL
    ),
    ProfileActivity.UserInfoDetailsItem(
        R.drawable.ic_phone,
        "+49 123 456 789 12",
        "Phone",
        ProfileActivity.Field.PHONE,
        Scope.FEDERATED
    ),
    ProfileActivity.UserInfoDetailsItem(
        R.drawable.ic_map_marker,
        "Berlin, Germany",
        "Address",
        ProfileActivity.Field.ADDRESS,
        Scope.PUBLISHED
    ),
    ProfileActivity.UserInfoDetailsItem(
        R.drawable.ic_web,
        "nextcloud.com",
        "Website",
        ProfileActivity.Field.WEBSITE,
        Scope.PRIVATE
    )
)

private val previewState = ProfileUiState(
    displayName = "John Doe",
    baseUrl = "nextcloud.example.com",
    isEditMode = false,
    contentState = ProfileContentState.ShowList,
    items = sampleItems,
    filteredItems = sampleItems,
    editableFields = listOf("email", "phone", "address", "website")
)

private val previewCallbacks = ProfileCallbacks()
