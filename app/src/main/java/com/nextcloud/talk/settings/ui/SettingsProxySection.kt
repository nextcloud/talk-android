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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nextcloud.talk.R
import com.nextcloud.talk.settings.ProxyUiState

@Composable
internal fun ProxySettings(state: ProxyUiState, callbacks: ProxyCallbacks) {
    val proxyTypeLabels = stringArrayResource(R.array.proxy_type_descriptions)
    val options = remember(proxyTypeLabels) {
        (listOf(ProxyUiState.NO_PROXY) + ProxyUiState.PROXY_TYPES).zip(proxyTypeLabels)
    }
    Column {
        SettingsDropdown(
            label = stringResource(R.string.nc_settings_proxy_type_title),
            options = options,
            selectedValue = state.type,
            onValueSelected = callbacks.onTypeChange
        )
        if (state.enabled) {
            ProxyTextField(
                label = stringResource(R.string.nc_settings_proxy_host_title),
                initialValue = state.host,
                keyboardType = KeyboardType.Uri,
                onCommit = callbacks.onHostChange
            )
            // the port is replaced by the default port of a newly selected type
            ProxyTextField(
                label = stringResource(R.string.nc_settings_proxy_port_title),
                initialValue = state.port,
                resetKey = state.type,
                keyboardType = KeyboardType.Number,
                onCommit = callbacks.onPortChange
            )
            SettingsSwitchItem(
                title = stringResource(R.string.nc_settings_use_credentials_title),
                checked = state.useCredentials,
                onCheckedChange = callbacks.onUseCredentialsChange
            )
            if (state.useCredentials) {
                ProxyTextField(
                    label = stringResource(R.string.nc_settings_proxy_username_title),
                    initialValue = state.username,
                    keyboardType = KeyboardType.Text,
                    onCommit = callbacks.onUsernameChange
                )
                ProxyTextField(
                    label = stringResource(R.string.nc_settings_proxy_password_title),
                    initialValue = state.password,
                    keyboardType = KeyboardType.Password,
                    onCommit = callbacks.onPasswordChange
                )
            }
        }
    }
}

/**
 * A text field whose text is committed with [onCommit] once editing ends: when it loses the focus, leaves the screen or
 * the app goes to the background. That avoids storing every intermediate value. The text is reset to [initialValue]
 * when [resetKey] changes.
 */
@Composable
private fun ProxyTextField(
    label: String,
    initialValue: String,
    keyboardType: KeyboardType,
    onCommit: (String) -> Unit,
    resetKey: Any? = null
) {
    var text by rememberSaveable(resetKey) { mutableStateOf(initialValue) }
    var committedText by rememberSaveable(resetKey) { mutableStateOf(initialValue) }
    val currentOnCommit by rememberUpdatedState(onCommit)
    val commit by rememberUpdatedState {
        if (text != committedText) {
            committedText = text
            currentOnCommit(text)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { commit() }
    DisposableEffect(Unit) {
        onDispose { commit() }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (keyboardType == KeyboardType.Password) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .onFocusChanged { if (!it.isFocused) commit() }
    )
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun ProxySettingsPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            ProxySettings(state = previewSettingsState().proxy, callbacks = ProxyCallbacks())
        }
    }
}
