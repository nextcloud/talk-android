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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.settings.AppearanceUiState
import com.nextcloud.talk.settings.LoggingStatus
import com.nextcloud.talk.settings.SettingsUiState

@Composable
internal fun AppearanceSection(state: AppearanceUiState, callbacks: SettingsScreenCallbacks) {
    Column {
        SettingsSectionHeader(stringResource(R.string.nc_settings_appearance))
        SettingsDropdown(
            label = stringResource(R.string.nc_settings_theme_title),
            options = stringArrayResource(R.array.theme_entry_values)
                .zip(stringArrayResource(R.array.theme_descriptions)),
            selectedValue = state.theme,
            onValueSelected = callbacks.onThemeChange
        )
        if (state.showEcosystemSetting) {
            SettingsSwitchItem(
                title = stringResource(R.string.nc_show_ecosystem_title),
                summary = stringResource(R.string.nc_show_ecosystem_description),
                checked = state.showEcosystem,
                onCheckedChange = callbacks.onShowEcosystemChange
            )
        }
    }
}

@Composable
internal fun DiagnosisSection(loggingStatus: LoggingStatus, callbacks: SettingsScreenCallbacks) {
    Column {
        SettingsSectionHeader(stringResource(R.string.nc_settings_diagnosis_logs_section_title))
        SettingsItem(
            title = stringResource(R.string.nc_settings_diagnosis_title),
            summary = stringResource(R.string.nc_settings_diagnosis_subtitle),
            onClick = callbacks.onOpenDiagnosis
        )
        SettingsItem(
            title = stringResource(R.string.nc_settings_logs_title),
            summary = stringResource(
                when (loggingStatus) {
                    LoggingStatus.DISABLED -> R.string.nc_logs_logging_disabled_note
                    LoggingStatus.ENABLED -> R.string.nc_logs_logging_enabled
                    LoggingStatus.ADVANCED -> R.string.nc_logs_advanced_logging_enabled_warning
                }
            ),
            onClick = callbacks.onOpenLogs
        )
        SettingsItem(
            title = stringResource(R.string.nc_settings_share_report_title),
            summary = stringResource(R.string.nc_settings_share_report_subtitle),
            onClick = callbacks.onShareReport
        )
    }
}

@Composable
internal fun AdvancedSection(state: SettingsUiState, callbacks: SettingsScreenCallbacks) {
    var showMediaQualityDialog by rememberSaveable { mutableStateOf(false) }
    Column {
        SettingsSectionHeader(stringResource(R.string.nc_settings_advanced_title))
        SettingsItem(
            title = stringResource(R.string.nc_settings_media_quality_title),
            summary = stringResource(
                if (state.mediaQualityReduced) R.string.nc_media_quality_reduced else R.string.nc_media_quality_original
            ),
            onClick = { showMediaQualityDialog = true }
        )
        if (state.isOnline) {
            SettingsItem(
                title = stringResource(
                    if (state.clientCertificateSet) R.string.nc_client_cert_change else R.string.nc_client_cert_setup
                ),
                onClick = callbacks.onClientCertificateClick
            )
            ProxySettings(state.proxy, callbacks.proxy)
        }
    }
    if (showMediaQualityDialog) {
        MediaQualityDialog(
            reducedQuality = state.mediaQualityReduced,
            onSelect = {
                showMediaQualityDialog = false
                callbacks.onMediaQualityChange(it)
            },
            onDismiss = { showMediaQualityDialog = false }
        )
    }
}

@Composable
internal fun AboutSection(isOnline: Boolean, callbacks: SettingsScreenCallbacks) {
    val uriHandler = LocalUriHandler.current
    val privacyUrl = stringResource(R.string.nc_privacy_url)
    val sourceCodeUrl = stringResource(R.string.nc_source_code_url)
    val licenseUrl = stringResource(R.string.nc_gpl3_url)
    Column {
        SettingsSectionHeader(stringResource(R.string.nc_about))
        if (isOnline && privacyUrl.isNotEmpty()) {
            SettingsItem(
                title = stringResource(R.string.nc_settings_privacy),
                onClick = { uriHandler.openUri(privacyUrl) }
            )
        }
        if (isOnline && sourceCodeUrl.isNotEmpty()) {
            SettingsItem(
                title = stringResource(R.string.nc_get_source_code),
                onClick = { uriHandler.openUri(sourceCodeUrl) }
            )
        }
        if (isOnline && licenseUrl.isNotEmpty()) {
            SettingsItem(
                title = stringResource(R.string.nc_license_title),
                summary = stringResource(R.string.nc_license_summary),
                onClick = { uriHandler.openUri(licenseUrl) }
            )
        }
        SettingsItem(
            title = stringResource(R.string.nc_app_product_name),
            summary = "v" + BuildConfig.VERSION_NAME,
            onClick = callbacks.onVersionClick
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun GeneralSectionsPreview() {
    val state = previewSettingsState()
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            Column {
                AppearanceSection(state.appearance, SettingsScreenCallbacks())
                DiagnosisSection(state.loggingStatus, SettingsScreenCallbacks())
                AdvancedSection(state, SettingsScreenCallbacks())
                AboutSection(state.isOnline, SettingsScreenCallbacks())
            }
        }
    }
}
