/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.settings.AccountMessage
import com.nextcloud.talk.settings.AccountUiState
import com.nextcloud.talk.settings.ServerAgeWarning
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.delay

private const val ACCOUNT_MESSAGE_DURATION_MS = 5000L
private const val ACCOUNT_MESSAGE_FADE_OUT_MS = 2500

@Composable
internal fun AccountSection(account: AccountUiState, callbacks: SettingsScreenCallbacks) {
    var showRemoveAccountDialog by rememberSaveable { mutableStateOf(false) }
    Column {
        AccountHeader(account = account, onClick = callbacks.onProfileClick)
        if (account.serverAgeWarning != ServerAgeWarning.NONE) {
            ServerAgeWarningCard(account.serverAgeWarning)
        }
        Text(
            text = stringResource(R.string.nc_settings_remove_account),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showRemoveAccountDialog = true }
                .padding(16.dp)
        )
    }
    if (showRemoveAccountDialog) {
        RemoveAccountDialog(
            onConfirm = {
                showRemoveAccountDialog = false
                callbacks.onRemoveAccount()
            },
            onDismiss = { showRemoveAccountDialog = false }
        )
    }
}

/** Shows [message] for a while and fades it out, then reports it as shown. */
@Composable
internal fun AccountMessageBanner(message: AccountMessage?, onShown: () -> Unit) {
    var visible by remember(message) { mutableStateOf(message != null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(ACCOUNT_MESSAGE_DURATION_MS)
            visible = false
            delay(ACCOUNT_MESSAGE_FADE_OUT_MS.toLong())
            onShown()
        }
    }
    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(ACCOUNT_MESSAGE_FADE_OUT_MS)) + shrinkVertically(tween(ACCOUNT_MESSAGE_FADE_OUT_MS))
    ) {
        val (text, color) = when (message) {
            AccountMessage.ACCOUNT_UPDATED ->
                stringResource(R.string.nc_settings_account_updated) to MaterialTheme.colorScheme.primary
            AccountMessage.SERVER_WITHOUT_TALK ->
                stringResource(R.string.nc_settings_wrong_account) to MaterialTheme.colorScheme.error
            AccountMessage.ACCOUNT_IMPORTED ->
                stringResource(R.string.nc_Server_account_imported) to MaterialTheme.colorScheme.primary
            AccountMessage.ACCOUNT_IMPORT_FAILED ->
                stringResource(R.string.nc_server_failed_to_import_account) to MaterialTheme.colorScheme.error
            null -> "" to Color.Unspecified
        }
        Text(
            text = text,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}

@Composable
private fun AccountHeader(account: AccountUiState, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AccountAvatar(account)
        Text(
            text = account.displayName,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = remember(account.baseUrl) { account.baseUrl.toUri().host.orEmpty() },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun AccountAvatar(account: AccountUiState) {
    val modifier = Modifier.size(avatarSize)
    if (LocalInspectionMode.current) {
        Icon(
            painter = painterResource(R.drawable.account_circle_96dp),
            contentDescription = stringResource(R.string.avatar),
            modifier = modifier
        )
        return
    }
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val request = remember(account.baseUrl, account.avatarId, account.credentials, isDarkTheme) {
        ImageRequest.Builder(context)
            .data(ApiUtils.getUrlForAvatar(account.baseUrl, account.avatarId, true, isDarkTheme))
            .addHeader("Authorization", account.credentials)
            .transformations(CircleCropTransformation())
            .placeholder(R.drawable.account_circle_96dp)
            .error(R.drawable.account_circle_96dp)
            .build()
    }
    AsyncImage(model = request, contentDescription = stringResource(R.string.avatar), modifier = modifier)
}

@Composable
private fun ServerAgeWarningCard(warning: ServerAgeWarning) {
    val (text, color) = if (warning == ServerAgeWarning.END_OF_LIFE) {
        stringResource(R.string.nc_settings_server_eol) to MaterialTheme.colorScheme.error
    } else {
        stringResource(R.string.nc_settings_server_almost_eol) to colorResource(R.color.nc_darkYellow)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_warning_white),
                contentDescription = stringResource(R.string.nc_settings_warning),
                tint = color
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val avatarSize = 96.dp

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun AccountSectionPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            Column {
                AccountMessageBanner(message = AccountMessage.ACCOUNT_IMPORTED, onShown = {})
                AccountSection(account = previewSettingsState().account, callbacks = SettingsScreenCallbacks())
            }
        }
    }
}
