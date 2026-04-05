/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chooseaccount.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.models.json.status.ClearAt
import com.nextcloud.talk.models.json.status.predefined.PredefinedStatus

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(
    name = "Light · Landscape",
    device = "spec:width=411dp,height=891dp,orientation=landscape"
)
@Preview(
    name = "Dark · Landscape",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    device = "spec:width=411dp,height=891dp,orientation=landscape"
)
@Composable
private fun PreviewStatusMessageSheet() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            StatusMessageSheetContentStateless(
                emoji = "🏖️",
                message = "On vacation",
                clearAtPosition = 3,
                predefinedStatuses = previewPredefinedStatuses(),
                isBackupStatusAvailable = false,
                onEmojiSelected = {},
                onMessageChanged = {},
                onClearAtPositionSelected = {},
                onRevertStatus = {},
                onSelectStatus = {},
                onClear = {},
                onSet = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "RTL · Arabic", showBackground = true, locale = "ar")
@Preview(
    name = "RTL · Arabic · Landscape",
    locale = "ar",
    device = "spec:width=411dp,height=891dp,orientation=landscape"
)
@Composable
private fun PreviewStatusMessageSheetRtl() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            StatusMessageSheetContentStateless(
                emoji = "📆",
                message = "In a meeting",
                clearAtPosition = 1,
                predefinedStatuses = previewPredefinedStatuses(),
                isBackupStatusAvailable = false,
                onEmojiSelected = {},
                onMessageChanged = {},
                onClearAtPositionSelected = {},
                onRevertStatus = {},
                onSelectStatus = {},
                onClear = {},
                onSet = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "With backup status · Light")
@Preview(name = "With backup status · Dark · German", uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "de")
@Preview(name = "With backup status · RTL · Arabic", locale = "ar")
@Preview(
    name = "With backup status · Light · Landscape",
    device = "spec:width=411dp,height=891dp,orientation=landscape"
)
@Preview(
    name = "With backup status · Dark · German · Landscape",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    locale = "de",
    device = "spec:width=411dp,height=891dp,orientation=landscape"
)
@Preview(
    name = "With backup status · RTL · Arabic · Landscape",
    locale = "ar",
    device = "spec:width=411dp,height=891dp,orientation=landscape"
)
@Composable
private fun PreviewStatusMessageSheetWithBackup() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            StatusMessageSheetContentStateless(
                emoji = "🏖️",
                message = "On vacation",
                clearAtPosition = 0,
                predefinedStatuses = previewPredefinedStatusesWithBackup(),
                isBackupStatusAvailable = true,
                onEmojiSelected = {},
                onMessageChanged = {},
                onClearAtPositionSelected = {},
                onRevertStatus = {},
                onSelectStatus = {},
                onClear = {},
                onSet = {}
            )
        }
    }
}

private fun previewPredefinedStatuses() =
    listOf(
        PredefinedStatus(
            id = "meeting",
            icon = "📆",
            message = "In a meeting",
            clearAt = ClearAt(type = "period", time = "3600")
        ),
        PredefinedStatus(
            id = "commuting",
            icon = "🚌",
            message = "Commuting",
            clearAt = ClearAt(type = "period", time = "1800")
        ),
        PredefinedStatus(
            id = "remote",
            icon = "🏡",
            message = "Working remotely",
            clearAt = ClearAt(type = "end-of", time = "day")
        ),
        PredefinedStatus(
            id = "sick",
            icon = "🤒",
            message = "Out sick",
            clearAt = ClearAt(type = "end-of", time = "day")
        ),
        PredefinedStatus(id = "vacation", icon = "🏖️", message = "On vacation", clearAt = null)
    )

private fun previewPredefinedStatusesWithBackup() =
    listOf(PredefinedStatus(id = "backup", icon = "⌛", message = "Be right back", clearAt = null)) +
        previewPredefinedStatuses()
