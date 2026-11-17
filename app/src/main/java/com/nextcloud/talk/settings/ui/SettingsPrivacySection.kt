/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R
import com.nextcloud.talk.settings.PrivacyUiState
import com.nextcloud.talk.settings.TypingStatusUiState

@Composable
internal fun PrivacySection(state: PrivacyUiState, callbacks: SettingsScreenCallbacks) {
    Column {
        SettingsSectionHeader(stringResource(R.string.nc_settings_privacy))
        SettingsSwitchItem(
            title = stringResource(R.string.nc_settings_screen_lock_title),
            summary = stringResource(
                R.string.nc_settings_screen_lock_desc,
                stringResource(R.string.nc_app_product_name)
            ),
            checked = state.screenLock,
            enabled = state.screenLockAvailable,
            onCheckedChange = callbacks.onScreenLockChange
        )
        SettingsDropdown(
            label = stringResource(R.string.nc_settings_screen_lock_timeout_title),
            options = stringArrayResource(R.array.screen_lock_timeout_entry_values)
                .zip(stringArrayResource(R.array.screen_lock_timeout_descriptions)),
            selectedValue = state.screenLockTimeout,
            enabled = state.screenLockAvailable && state.screenLock,
            onValueSelected = callbacks.onScreenLockTimeoutChange
        )
        SettingsSwitchItem(
            title = stringResource(R.string.nc_settings_screen_security_title),
            summary = stringResource(R.string.nc_settings_screen_security_desc),
            checked = state.screenSecurity,
            onCheckedChange = callbacks.onScreenSecurityChange
        )
        SettingsSwitchItem(
            title = stringResource(R.string.nc_settings_incognito_keyboard_title),
            summary = stringResource(R.string.nc_settings_incognito_keyboard_desc),
            checked = state.incognitoKeyboard,
            onCheckedChange = callbacks.onIncognitoKeyboardChange
        )
        if (state.phoneBookIntegrationAvailable) {
            SettingsSwitchItem(
                title = stringResource(R.string.nc_settings_phone_book_integration_title),
                summary = stringResource(R.string.nc_settings_phone_book_integration_desc),
                checked = state.phoneBookIntegration,
                onCheckedChange = callbacks.onPhoneBookIntegrationChange
            )
        }
        if (state.readStatusAvailable) {
            SettingsSwitchItem(
                title = stringResource(R.string.nc_settings_read_privacy_title),
                summary = stringResource(R.string.nc_settings_read_privacy_desc),
                checked = state.readStatusShared,
                onCheckedChange = callbacks.onReadStatusChange
            )
        }
        TypingStatusItem(state.typingStatus, callbacks.onTypingStatusChange)
    }
}

@Composable
private fun TypingStatusItem(state: TypingStatusUiState, onTypingStatusChange: (Boolean) -> Unit) {
    when (state) {
        TypingStatusUiState.Hidden -> Unit
        TypingStatusUiState.RequiresHighPerformanceBackend -> SettingsSwitchItem(
            title = stringResource(R.string.nc_settings_typing_status_title),
            summary = stringResource(R.string.nc_settings_typing_status_desc),
            supportingText = stringResource(R.string.nc_settings_typing_status_hpb_description),
            checked = false,
            enabled = false,
            onCheckedChange = onTypingStatusChange
        )
        is TypingStatusUiState.Available -> SettingsSwitchItem(
            title = stringResource(R.string.nc_settings_typing_status_title),
            summary = stringResource(R.string.nc_settings_typing_status_desc),
            checked = state.shared,
            onCheckedChange = onTypingStatusChange
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun PrivacySectionPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            PrivacySection(state = previewSettingsState().privacy, callbacks = SettingsScreenCallbacks())
        }
    }
}
