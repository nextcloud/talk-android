/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chooseaccount.ui

import android.content.res.Configuration
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import com.nextcloud.talk.R
import com.nextcloud.talk.chooseaccount.viewmodel.StatusMessageViewModel
import com.nextcloud.talk.models.json.status.ClearAt
import com.nextcloud.talk.models.json.status.Status
import com.nextcloud.talk.models.json.status.predefined.PredefinedStatus
import com.vanniktech.emoji.EmojiEditText
import com.vanniktech.emoji.EmojiPopup
import com.vanniktech.emoji.installDisableKeyboardInput
import com.vanniktech.emoji.installForceSingleEmoji

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
                ClearAfterDropdown(
                    selectedPosition = clearAtPosition,
                    onPositionSelected = onClearAtPositionSelected
                )
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
    } else {
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
            ClearAfterDropdown(
                selectedPosition = clearAtPosition,
                onPositionSelected = onClearAtPositionSelected
            )
            Spacer(modifier = Modifier.height(16.dp))
            ActionButtons(onClear = onClear, onSet = onSet)
        }
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

@Composable
private fun PredefinedStatusList(
    statuses: List<PredefinedStatus>,
    isBackupStatusAvailable: Boolean,
    onRevertStatus: () -> Unit,
    onSelectStatus: (PredefinedStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    if (statuses.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        if (isBackupStatusAvailable) {
            Text(
                text = stringResource(R.string.automatic_status_set),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        statuses.forEachIndexed { index, status ->
            PredefinedStatusRow(
                status = status,
                isBackupEntry = isBackupStatusAvailable && index == 0,
                onRevertStatus = onRevertStatus,
                onClick = { onSelectStatus(status) }
            )
        }
    }
}

@Composable
private fun EmojiAndMessageRow(
    emoji: String,
    message: String,
    onEmojiSelected: (String) -> Unit,
    onMessageChanged: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        EmojiButton(emoji = emoji, onEmojiSelected = onEmojiSelected)
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChanged,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.whats_your_status)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun EmojiButton(emoji: String, onEmojiSelected: (String) -> Unit) {
    val isPreview = LocalInspectionMode.current
    val rootView = LocalView.current
    var emojiPopup by remember { mutableStateOf<EmojiPopup?>(null) }
    val displayEmoji = emoji.ifEmpty { stringResource(R.string.default_emoji) }

    Card(
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .size(56.dp)
            .clickable { emojiPopup?.show() }
    ) {
        if (isPreview) {
            Text(
                text = displayEmoji,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .size(56.dp)
                    .padding(14.dp)
            )
        } else {
            AndroidView(
                modifier = Modifier.size(56.dp),
                factory = { ctx ->
                    EmojiEditText(ctx).apply {
                        setText(displayEmoji)
                        gravity = Gravity.CENTER
                        textSize = EMOJI_TEXT_SIZE_SP
                        background = null
                        isCursorVisible = false
                        val popup = EmojiPopup(
                            rootView = rootView,
                            editText = this,
                            onEmojiClickListener = {
                                onEmojiSelected(text.toString())
                                emojiPopup?.dismiss()
                                clearFocus()
                                ctx.getSystemService<InputMethodManager>()
                                    ?.hideSoftInputFromWindow(windowToken, 0)
                            }
                        )
                        installDisableKeyboardInput(popup)
                        installForceSingleEmoji()
                        emojiPopup = popup
                    }
                },
                update = { view ->
                    if (view.text.toString() != displayEmoji) {
                        view.setText(displayEmoji)
                    }
                }
            )
        }
    }
}

@Composable
private fun PredefinedStatusRow(
    status: PredefinedStatus,
    isBackupEntry: Boolean,
    onRevertStatus: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val clearAtLabel = remember(status) { resolveClearAtLabel(status, context) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = status.icon ?: "",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(42.dp)
        )
        if (isBackupEntry) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.previously_set),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (clearAtLabel.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.divider),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Text(
                        text = clearAtLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (isBackupEntry) {
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onRevertStatus,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.reset_status))
            }
        }
    }
}

private fun resolveClearAtLabel(status: PredefinedStatus, context: android.content.Context): String {
    val clearAt = status.clearAt ?: return context.getString(R.string.dontClear)
    return when (clearAt.type) {
        "period" -> when (clearAt.time) {
            "900" -> context.getString(R.string.fifteenMinutes)
            "1800" -> context.getString(R.string.thirtyMinutes)
            "3600" -> context.getString(R.string.oneHour)
            "14400" -> context.getString(R.string.fourHours)
            else -> ""
        }
        "end-of" -> if (clearAt.time == "day") {
            context.getString(R.string.today)
        } else {
            context.getString(R.string.thisWeek)
        }

        else -> ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearAfterDropdown(selectedPosition: Int, onPositionSelected: (Int) -> Unit) {
    val options = listOf(
        stringResource(R.string.dontClear),
        stringResource(R.string.fifteenMinutes),
        stringResource(R.string.thirtyMinutes),
        stringResource(R.string.oneHour),
        stringResource(R.string.fourHours),
        stringResource(R.string.today),
        stringResource(R.string.thisWeek)
    )
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(R.string.clear_status_message_after),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = options[selectedPosition],
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onPositionSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(onClear: () -> Unit, onSet: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Text(
                text = stringResource(R.string.clear_status_message),
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = onSet,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Text(
                text = stringResource(R.string.set_status_message),
                textAlign = TextAlign.Center
            )
        }
    }
}

private const val EMOJI_TEXT_SIZE_SP = 24f

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
