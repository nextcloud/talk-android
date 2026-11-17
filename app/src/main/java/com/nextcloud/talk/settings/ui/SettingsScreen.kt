/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.components.IncognitoKeyboard
import com.nextcloud.talk.components.StandardAppBar
import com.nextcloud.talk.settings.AccountUiState
import com.nextcloud.talk.settings.NotificationSound
import com.nextcloud.talk.settings.NotificationsUiState
import com.nextcloud.talk.settings.PrivacyUiState
import com.nextcloud.talk.settings.ProxyUiState
import com.nextcloud.talk.settings.RingtoneState
import com.nextcloud.talk.settings.ServerAgeWarning
import com.nextcloud.talk.settings.SettingsUiState
import com.nextcloud.talk.settings.TypingStatusUiState
import com.nextcloud.talk.settings.UnifiedPushUiState

/** System settings screens of the app that the settings screen links to. */
enum class SystemSettingsScreen {
    BATTERY_OPTIMIZATION,
    FULL_SCREEN_INTENT,
    CALL_NOTIFICATIONS,
    MESSAGE_NOTIFICATIONS
}

data class SettingsScreenCallbacks(
    val onProfileClick: () -> Unit = {},
    val onAccountMessageShown: () -> Unit = {},
    val onRemoveAccount: () -> Unit = {},
    val onThemeChange: (String) -> Unit = {},
    val onShowEcosystemChange: (Boolean) -> Unit = {},
    val onUnifiedPushChange: (Boolean) -> Unit = {},
    val onPickDistributor: () -> Unit = {},
    val onRequestNotificationPermission: () -> Unit = {},
    val onOpenSystemSettings: (SystemSettingsScreen) -> Unit = {},
    val onOpenDiagnosis: () -> Unit = {},
    /** Opens the system ringtone picker, with the current sound selected. */
    val onPickNotificationSound: (NotificationSound, RingtoneState) -> Unit = { _, _ -> },

    /** Sets the sound by its uri, null for no sound. */
    val onNotificationSoundChange: (NotificationSound, String?) -> Unit = { _, _ -> },
    val onShowNotificationWarningChange: (Boolean) -> Unit = {},
    val onScreenLockChange: (Boolean) -> Unit = {},
    val onScreenLockTimeoutChange: (String) -> Unit = {},
    val onScreenSecurityChange: (Boolean) -> Unit = {},
    val onIncognitoKeyboardChange: (Boolean) -> Unit = {},
    val onPhoneBookIntegrationChange: (Boolean) -> Unit = {},
    val onReadStatusChange: (Boolean) -> Unit = {},
    val onTypingStatusChange: (Boolean) -> Unit = {},
    val onPhoneNumberChange: () -> Unit = {},
    val onSubmitPhoneNumber: (String) -> Unit = {},
    val onDismissPhoneNumberDialog: () -> Unit = {},
    val onOpenLogs: () -> Unit = {},
    val onShareReport: () -> Unit = {},
    val onMediaQualityChange: (Boolean) -> Unit = {},
    val onClientCertificateClick: () -> Unit = {},
    val proxy: ProxyCallbacks = ProxyCallbacks(),
    val onVersionClick: () -> Unit = {}
)

data class ProxyCallbacks(
    val onTypeChange: (String) -> Unit = {},
    val onHostChange: (String) -> Unit = {},
    val onPortChange: (String) -> Unit = {},
    val onUseCredentialsChange: (Boolean) -> Unit = {},
    val onUsernameChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {}
)

private enum class SettingsSection {
    ACCOUNT,
    APPEARANCE,
    NOTIFICATIONS,
    PRIVACY,
    DIAGNOSIS,
    ADVANCED,
    ABOUT
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    callbacks: SettingsScreenCallbacks,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    ColoredStatusBar()
    IncognitoKeyboard(enabled = state.privacy.incognitoKeyboard) {
        Scaffold(
            modifier = Modifier
                .statusBarsPadding()
                .displayCutoutPadding(),
            topBar = { StandardAppBar(title = stringResource(R.string.nc_settings), menuItems = null) },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            SettingsContent(state = state, callbacks = callbacks, paddingValues = paddingValues)
        }
        if (state.showPhoneNumberDialog) {
            PhoneNumberDialog(
                isError = state.phoneNumberError,
                onPhoneNumberChange = callbacks.onPhoneNumberChange,
                onSubmit = callbacks.onSubmitPhoneNumber,
                onDismiss = callbacks.onDismissPhoneNumberDialog
            )
        }
    }
}

@Composable
private fun SettingsContent(state: SettingsUiState, callbacks: SettingsScreenCallbacks, paddingValues: PaddingValues) {
    val listState = rememberLazyListState()
    var scrolledToNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (state.highlightNotificationIssues && !scrolledToNotifications) {
            listState.scrollToItem(SettingsSection.NOTIFICATIONS.ordinal)
            scrolledToNotifications = true
        }
    }
    // outside of the list, so that it is shown and consumed also when the list starts scrolled down
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.bg_default))
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues)
            .imePadding()
    ) {
        AccountMessageBanner(message = state.accountMessage, onShown = callbacks.onAccountMessageShown)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 48.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(SettingsSection.entries, key = { it }) { section ->
                SettingsSectionContent(section, state, callbacks)
            }
        }
    }
}

@Composable
private fun SettingsSectionContent(
    section: SettingsSection,
    state: SettingsUiState,
    callbacks: SettingsScreenCallbacks
) {
    when (section) {
        SettingsSection.ACCOUNT -> AccountSection(state.account, callbacks)
        SettingsSection.APPEARANCE -> AppearanceSection(state.appearance, callbacks)
        SettingsSection.NOTIFICATIONS -> NotificationsSection(
            state = state.notifications,
            baseUrl = state.account.baseUrl,
            highlightIssues = state.highlightNotificationIssues,
            callbacks = callbacks
        )
        SettingsSection.PRIVACY -> PrivacySection(state.privacy, callbacks)
        SettingsSection.DIAGNOSIS -> DiagnosisSection(state.loggingStatus, callbacks)
        SettingsSection.ADVANCED -> AdvancedSection(state, callbacks)
        SettingsSection.ABOUT -> AboutSection(state.isOnline, callbacks)
    }
}

internal fun previewSettingsState(): SettingsUiState =
    SettingsUiState(
        isOnline = true,
        account = AccountUiState(
            displayName = "Jane Doe",
            baseUrl = "https://cloud.example.com",
            avatarId = "jane",
            serverAgeWarning = ServerAgeWarning.ALMOST_END_OF_LIFE
        ),
        notifications = NotificationsUiState(
            unifiedPush = UnifiedPushUiState(enabled = true, distributor = "ntfy", canPickDistributor = true),
            notificationPermissionGranted = false,
            batteryOptimizationIgnored = false,
            fullScreenIntentGranted = true,
            callRingtone = RingtoneState.Default,
            messageRingtone = RingtoneState.Disabled,
            showNotificationWarningSetting = true,
            showNotificationWarning = true
        ),
        privacy = PrivacyUiState(
            screenLockAvailable = true,
            screenLock = true,
            screenLockTimeout = "300",
            phoneBookIntegrationAvailable = true,
            readStatusAvailable = true,
            readStatusShared = true,
            typingStatus = TypingStatusUiState.RequiresHighPerformanceBackend
        ),
        proxy = ProxyUiState(
            type = "HTTP",
            enabled = true,
            host = "proxy.example.com",
            port = "3128",
            useCredentials = true
        )
    )

@Preview(name = "Light", heightDp = 2400)
@Preview(name = "Dark", heightDp = 2400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", heightDp = 2400, locale = "ar")
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        SettingsScreen(state = previewSettingsState(), callbacks = SettingsScreenCallbacks())
    }
}
