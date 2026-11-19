/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings.ui

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R
import com.nextcloud.talk.settings.NotificationSound
import com.nextcloud.talk.settings.NotificationsUiState
import com.nextcloud.talk.settings.PushAvailability
import com.nextcloud.talk.settings.RingtoneState
import com.nextcloud.talk.settings.UnifiedPushUiState

@Composable
internal fun NotificationsSection(
    state: NotificationsUiState,
    baseUrl: String,
    highlightIssues: Boolean,
    callbacks: SettingsScreenCallbacks
) {
    Column {
        SettingsSectionHeader(stringResource(R.string.nc_settings_notification_sounds_post_oreo))
        state.unifiedPush?.let { UnifiedPushItems(it, callbacks) }
        when (state.pushAvailability) {
            PushAvailability.AVAILABLE -> PushSetupItems(state, baseUrl, highlightIssues, callbacks)
            PushAvailability.GOOGLE_PLAY_SERVICES_UNAVAILABLE ->
                SettingsItem(title = stringResource(R.string.gplay_available_no))
            PushAvailability.UNIFIED_PUSH_DISABLED_AND_GOOGLE_PLAY_SERVICES_UNAVAILABLE ->
                SettingsItem(title = stringResource(R.string.nc_diagnosis_push_available_no))
        }
        state.fullScreenIntentGranted?.let { granted ->
            SettingsItem(
                title = stringResource(R.string.full_screen_intent_permission_title),
                summary = stringResource(
                    if (granted) {
                        R.string.full_screen_intent_permission_granted
                    } else {
                        R.string.full_screen_intent_permission_not_granted
                    }
                ),
                summaryColor = statusColor(granted),
                highlight = highlightIssues && !granted,
                onClick = { callbacks.onOpenSystemSettings(SystemSettingsScreen.FULL_SCREEN_INTENT) }
            )
        }
        RingtoneItems(state, highlightIssues, callbacks)
        if (state.showNotificationWarningSetting) {
            SettingsSwitchItem(
                title = stringResource(R.string.nc_show_notification_warning_title),
                summary = stringResource(R.string.nc_show_notification_warning_description),
                checked = state.showNotificationWarning,
                onCheckedChange = callbacks.onShowNotificationWarningChange
            )
        }
    }
}

@Composable
private fun UnifiedPushItems(state: UnifiedPushUiState, callbacks: SettingsScreenCallbacks) {
    SettingsSwitchItem(
        title = stringResource(R.string.nc_settings_screen_enable_unifiedpush_title),
        summary = stringResource(R.string.nc_settings_screen_enable_unifiedpush_desc),
        checked = state.enabled,
        onCheckedChange = callbacks.onUnifiedPushChange
    )
    if (state.canPickDistributor) {
        SettingsItem(
            title = stringResource(R.string.nc_settings_screen_unifiedpush_service_title),
            summary = state.distributor,
            onClick = callbacks.onPickDistributor
        )
    }
}

/** The settings that push notifications depend on, shown if push notifications are available at all. */
@Composable
private fun PushSetupItems(
    state: NotificationsUiState,
    baseUrl: String,
    highlightIssues: Boolean,
    callbacks: SettingsScreenCallbacks
) {
    var showBatteryOptimizationDialog by rememberSaveable { mutableStateOf(false) }
    var showTroubleshootingDialog by rememberSaveable { mutableStateOf(false) }
    val permissionGranted = state.notificationPermissionGranted

    if (state.serverNotificationAppMissing) {
        SettingsItem(
            title = stringResource(R.string.nc_settings_server_notification_app_not_installed_title),
            summary = stringResource(R.string.nc_settings_contact_admin_of) + "\n" + baseUrl,
            summaryColor = MaterialTheme.colorScheme.error,
            highlight = highlightIssues
        )
    }
    if (permissionGranted != null) {
        NotificationPermissionItem(
            granted = permissionGranted,
            highlight = highlightIssues && !permissionGranted,
            onClick = when {
                !permissionGranted -> callbacks.onRequestNotificationPermission
                state.batteryOptimizationIgnored -> {
                    { showTroubleshootingDialog = true }
                }
                else -> null
            }
        )
    }
    BatteryOptimizationItem(
        ignored = state.batteryOptimizationIgnored,
        highlight = highlightIssues && !state.batteryOptimizationIgnored,
        onClick = when {
            !state.batteryOptimizationIgnored -> {
                { showBatteryOptimizationDialog = true }
            }
            permissionGranted != false -> {
                { showTroubleshootingDialog = true }
            }
            else -> null
        }
    )

    if (showBatteryOptimizationDialog) {
        BatteryOptimizationDialog(
            onOpenSettings = {
                showBatteryOptimizationDialog = false
                callbacks.onOpenSystemSettings(SystemSettingsScreen.BATTERY_OPTIMIZATION)
            },
            onDismiss = { showBatteryOptimizationDialog = false }
        )
    }
    if (showTroubleshootingDialog) {
        TroubleshootingDialog(
            onOpenDiagnosis = callbacks.onOpenDiagnosis,
            onDismiss = { showTroubleshootingDialog = false }
        )
    }
}

@Composable
private fun NotificationPermissionItem(granted: Boolean, highlight: Boolean, onClick: (() -> Unit)?) {
    SettingsItem(
        title = stringResource(R.string.notification_permission),
        summary = stringResource(
            if (granted) R.string.nc_settings_notifications_granted else R.string.nc_settings_notifications_declined
        ),
        summaryColor = statusColor(granted),
        highlight = highlight,
        onClick = onClick
    )
}

@Composable
private fun BatteryOptimizationItem(ignored: Boolean, highlight: Boolean, onClick: (() -> Unit)?) {
    SettingsItem(
        title = stringResource(R.string.battery_optimization_title),
        summary = stringResource(
            if (ignored) R.string.battery_optimization_ignored else R.string.battery_optimization_not_ignored
        ),
        summaryColor = statusColor(ignored),
        highlight = highlight,
        onClick = onClick
    )
}

@Composable
private fun TroubleshootingDialog(onOpenDiagnosis: () -> Unit, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val checklistUrl = stringResource(R.string.notification_checklist_url)
    val dontKillMyAppUrl = stringResource(R.string.dontkillmyapp_url)
    NotificationTroubleshootingDialog(
        onOpenChecklist = {
            onDismiss()
            uriHandler.openUri(checklistUrl)
        },
        onOpenDontKillMyApp = {
            onDismiss()
            uriHandler.openUri(dontKillMyAppUrl)
        },
        onOpenDiagnosis = {
            onDismiss()
            onOpenDiagnosis()
        },
        onDismiss = onDismiss
    )
}

@Composable
private fun RingtoneItems(state: NotificationsUiState, highlightIssues: Boolean, callbacks: SettingsScreenCallbacks) {
    var soundDialog by rememberSaveable { mutableStateOf<NotificationSound?>(null) }

    NotificationSound.entries.forEach { sound ->
        val ringtone = state.ringtone(sound)
        RingtoneItem(
            title = stringResource(sound.titleRes),
            ringtone = ringtone,
            enabled = state.ringtoneSettingsEnabled,
            highlight = highlightIssues,
            onClick = {
                if (ringtone == RingtoneState.Disabled) {
                    callbacks.onOpenSystemSettings(sound.systemSettingsScreen)
                } else {
                    soundDialog = sound
                }
            }
        )
    }

    soundDialog?.let { sound ->
        SoundDialog(
            sound = sound,
            current = state.ringtone(sound),
            callbacks = callbacks,
            onDismiss = { soundDialog = null }
        )
    }
}

private fun NotificationsUiState.ringtone(sound: NotificationSound): RingtoneState =
    when (sound) {
        NotificationSound.CALL -> callRingtone
        NotificationSound.MESSAGE -> messageRingtone
    }

internal val NotificationSound.systemSettingsScreen: SystemSettingsScreen
    get() = when (this) {
        NotificationSound.CALL -> SystemSettingsScreen.CALL_NOTIFICATIONS
        NotificationSound.MESSAGE -> SystemSettingsScreen.MESSAGE_NOTIFICATIONS
    }

internal val NotificationSound.titleRes: Int
    @StringRes get() = when (this) {
        NotificationSound.CALL -> R.string.nc_settings_call_ringtone
        NotificationSound.MESSAGE -> R.string.nc_settings_other_notifications_ringtone
    }

@Composable
private fun RingtoneItem(
    title: String,
    ringtone: RingtoneState,
    enabled: Boolean,
    highlight: Boolean,
    onClick: () -> Unit
) {
    val disabled = ringtone == RingtoneState.Disabled
    SettingsItem(
        title = title,
        summary = when (ringtone) {
            RingtoneState.Disabled -> stringResource(R.string.nc_common_disabled)
            RingtoneState.Silent -> stringResource(R.string.nc_settings_no_ringtone)
            RingtoneState.Default -> stringResource(R.string.nc_settings_default_ringtone)
            is RingtoneState.Custom -> ringtone.title
        },
        summaryColor = statusColor(!disabled),
        enabled = enabled,
        highlight = highlight && disabled,
        onClick = onClick
    )
}

@Composable
private fun statusColor(ok: Boolean): Color =
    if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun NotificationsSectionPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            NotificationsSection(
                state = previewSettingsState().notifications.copy(serverNotificationAppMissing = true),
                baseUrl = "https://cloud.example.com",
                highlightIssues = false,
                callbacks = SettingsScreenCallbacks()
            )
        }
    }
}
