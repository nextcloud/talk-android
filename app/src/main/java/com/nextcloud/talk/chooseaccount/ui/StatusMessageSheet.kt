/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chooseaccount.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chooseaccount.viewmodel.StatusMessageViewModel
import com.nextcloud.talk.models.json.status.Status
import com.nextcloud.talk.models.json.status.predefined.PredefinedStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusMessageModalBottomSheet(currentStatus: Status, viewModel: StatusMessageViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDismissed by viewModel.isDismissed.collectAsState()

    LaunchedEffect(currentStatus) {
        viewModel.init(currentStatus)
        viewModel.checkBackupStatus()
        viewModel.fetchPredefinedStatuses()
    }

    LaunchedEffect(isDismissed) {
        if (isDismissed) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        StatusMessageSheetContent(viewModel = viewModel)
    }
}

@Composable
fun StatusMessageSheetContent(modifier: Modifier = Modifier, viewModel: StatusMessageViewModel) {
    val emoji by viewModel.emoji.collectAsState()
    val message by viewModel.message.collectAsState()
    val clearAtPosition by viewModel.clearAtPosition.collectAsState()
    val predefinedStatuses by viewModel.predefinedStatuses.collectAsState()
    val isBackupStatusAvailable by viewModel.isBackupStatusAvailable.collectAsState()

    StatusMessageSheetContentStateless(
        modifier = modifier,
        emoji = emoji,
        message = message,
        clearAtPosition = clearAtPosition,
        predefinedStatuses = predefinedStatuses,
        isBackupStatusAvailable = isBackupStatusAvailable,
        onEmojiSelected = { viewModel.updateEmoji(it) },
        onMessageChanged = { viewModel.updateMessage(it) },
        onClearAtPositionSelected = { viewModel.updateClearAtPosition(it) },
        onRevertStatus = { viewModel.revertStatus() },
        onSelectStatus = { viewModel.selectPredefinedStatus(it) },
        onClear = { viewModel.clearStatus() },
        onSet = { viewModel.setStatus() }
    )
}

@Suppress("LongParameterList")
@Composable
internal fun StatusMessageSheetContentStateless(
    modifier: Modifier = Modifier,
    emoji: String,
    message: String,
    clearAtPosition: Int,
    predefinedStatuses: List<PredefinedStatus>,
    isBackupStatusAvailable: Boolean,
    onEmojiSelected: (String) -> Unit,
    onMessageChanged: (String) -> Unit,
    onClearAtPositionSelected: (Int) -> Unit,
    onRevertStatus: () -> Unit,
    onSelectStatus: (PredefinedStatus) -> Unit,
    onClear: () -> Unit,
    onSet: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
        LandscapeSheetContent(
            modifier = modifier,
            emoji = emoji,
            message = message,
            clearAtPosition = clearAtPosition,
            predefinedStatuses = predefinedStatuses,
            isBackupStatusAvailable = isBackupStatusAvailable,
            onEmojiSelected = onEmojiSelected,
            onMessageChanged = onMessageChanged,
            onClearAtPositionSelected = onClearAtPositionSelected,
            onRevertStatus = onRevertStatus,
            onSelectStatus = onSelectStatus,
            onClear = onClear,
            onSet = onSet
        )
    } else {
        PortraitSheetContent(
            modifier = modifier,
            emoji = emoji,
            message = message,
            clearAtPosition = clearAtPosition,
            predefinedStatuses = predefinedStatuses,
            isBackupStatusAvailable = isBackupStatusAvailable,
            onEmojiSelected = onEmojiSelected,
            onMessageChanged = onMessageChanged,
            onClearAtPositionSelected = onClearAtPositionSelected,
            onRevertStatus = onRevertStatus,
            onSelectStatus = onSelectStatus,
            onClear = onClear,
            onSet = onSet
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun LandscapeSheetContent(
    modifier: Modifier,
    emoji: String,
    message: String,
    clearAtPosition: Int,
    predefinedStatuses: List<PredefinedStatus>,
    isBackupStatusAvailable: Boolean,
    onEmojiSelected: (String) -> Unit,
    onMessageChanged: (String) -> Unit,
    onClearAtPositionSelected: (Int) -> Unit,
    onRevertStatus: () -> Unit,
    onSelectStatus: (PredefinedStatus) -> Unit,
    onClear: () -> Unit,
    onSet: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            StatusMessageTitle()
            EmojiAndMessageRow(
                emoji = emoji,
                message = message,
                onEmojiSelected = onEmojiSelected,
                onMessageChanged = onMessageChanged
            )
            Spacer(modifier = Modifier.height(12.dp))
            ClearAfterDropdown(selectedPosition = clearAtPosition, onPositionSelected = onClearAtPositionSelected)
            Spacer(modifier = Modifier.height(16.dp))
            ActionButtons(onClear = onClear, onSet = onSet)
        }
        PredefinedStatusList(
            statuses = predefinedStatuses,
            isBackupStatusAvailable = isBackupStatusAvailable,
            onRevertStatus = onRevertStatus,
            onSelectStatus = onSelectStatus,
            modifier = Modifier.weight(1f)
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun PortraitSheetContent(
    modifier: Modifier,
    emoji: String,
    message: String,
    clearAtPosition: Int,
    predefinedStatuses: List<PredefinedStatus>,
    isBackupStatusAvailable: Boolean,
    onEmojiSelected: (String) -> Unit,
    onMessageChanged: (String) -> Unit,
    onClearAtPositionSelected: (Int) -> Unit,
    onRevertStatus: () -> Unit,
    onSelectStatus: (PredefinedStatus) -> Unit,
    onClear: () -> Unit,
    onSet: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        StatusMessageTitle()
        EmojiAndMessageRow(
            emoji = emoji,
            message = message,
            onEmojiSelected = onEmojiSelected,
            onMessageChanged = onMessageChanged
        )
        PredefinedStatusList(
            statuses = predefinedStatuses,
            isBackupStatusAvailable = isBackupStatusAvailable,
            onRevertStatus = onRevertStatus,
            onSelectStatus = onSelectStatus,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.height(12.dp))
        ClearAfterDropdown(selectedPosition = clearAtPosition, onPositionSelected = onClearAtPositionSelected)
        Spacer(modifier = Modifier.height(16.dp))
        ActionButtons(onClear = onClear, onSet = onSet)
    }
}

@Composable
private fun StatusMessageTitle() {
    Text(
        text = stringResource(R.string.status_message),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}
