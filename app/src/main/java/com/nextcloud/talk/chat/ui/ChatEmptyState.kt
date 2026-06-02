/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R

@Composable
fun ChatEmptyState(type: ChatEmptyStateType, modifier: Modifier = Modifier) {
    val iconRes: Int
    val text: String

    when (type) {
        is ChatEmptyStateType.Lobby -> {
            iconRes = R.drawable.ic_room_service_black_24dp
            text = type.text
        }
        ChatEmptyStateType.Offline -> {
            iconRes = R.drawable.ic_signal_wifi_off_white_24dp
            text = stringResource(R.string.no_offline_messages_saved)
        }
        ChatEmptyStateType.NoMessages -> {
            iconRes = R.drawable.ic_comment
            text = stringResource(R.string.nc_chat_no_messages)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(colorResource(R.color.grey_600), BlendMode.SrcIn)
            )
            Text(
                text = text,
                modifier = Modifier.padding(top = 16.dp),
                color = colorResource(R.color.grey_600),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private const val PREVIEW_WIDTH_DP = 360
private const val PREVIEW_HEIGHT_DP = 640

@Preview(name = "Lobby · Light", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Preview(
    name = "Lobby · Dark",
    widthDp = PREVIEW_WIDTH_DP,
    heightDp = PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun LobbyPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ChatEmptyState(
                type = ChatEmptyStateType.Lobby(
                    text = "You are currently waiting in the lobby.\n\n" +
                        "This meeting is scheduled for Monday, 2 Jun 2026 at 10:00 – in 45 minutes"
                )
            )
        }
    }
}

@Preview(name = "Lobby · RTL / Arabic", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP, locale = "ar")
@Composable
private fun LobbyRtlPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ChatEmptyState(
                type = ChatEmptyStateType.Lobby(
                    text = "أنت في انتظار الدخول إلى القاعة."
                )
            )
        }
    }
}

@Preview(name = "Offline · Light", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Preview(
    name = "Offline · Dark",
    widthDp = PREVIEW_WIDTH_DP,
    heightDp = PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun OfflinePreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ChatEmptyState(type = ChatEmptyStateType.Offline)
        }
    }
}

@Preview(name = "Offline · RTL / Arabic", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP, locale = "ar")
@Composable
private fun OfflineRtlPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ChatEmptyState(type = ChatEmptyStateType.Offline)
        }
    }
}

@Preview(name = "No messages · Light", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Preview(
    name = "No messages · Dark",
    widthDp = PREVIEW_WIDTH_DP,
    heightDp = PREVIEW_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun NoMessagesPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ChatEmptyState(type = ChatEmptyStateType.NoMessages)
        }
    }
}

@Preview(name = "No messages · RTL / Arabic", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP, locale = "ar")
@Composable
private fun NoMessagesRtlPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ChatEmptyState(type = ChatEmptyStateType.NoMessages)
        }
    }
}
