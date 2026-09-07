/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R
import com.nextcloud.talk.conversationcreation.ConversationPresetId
import com.nextcloud.talk.conversationcreation.ConversationPresetModel
import com.nextcloud.talk.conversationcreation.selectable
import com.nextcloud.talk.conversationcreation.viewmodel.ConversationCreationViewModel
import com.nextcloud.talk.conversationcreation.viewmodel.PresetsUiState

/**
 * The conversation types the server offers, as a grid of selectable cards.
 */
@Composable
fun ConversationPresets(conversationCreationViewModel: ConversationCreationViewModel) {
    when (val state = conversationCreationViewModel.presets.value) {
        is PresetsUiState.Loading -> PresetsLoading()
        is PresetsUiState.Error -> PresetsError { conversationCreationViewModel.loadPresets() }
        is PresetsUiState.Success -> PresetCards(
            presets = state.presets.selectable(),
            selected = conversationCreationViewModel.conversationPreset.value,
            onSelect = { conversationCreationViewModel.updateConversationPreset(it) }
        )
    }
}

@Composable
private fun PresetsLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun PresetsError(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.nc_conversation_types_error),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.nc_retry))
        }
    }
}

@Composable
private fun PresetCards(presets: List<ConversationPresetModel>, selected: String, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val rows = remember(presets) { presets.chunked(PRESET_COLUMNS) }
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { preset ->
                    val label = presetLabels[preset.identifier]
                    SelectableCard(
                        content = SelectableCardContent(
                            title = label?.let { stringResource(it.title) } ?: preset.name,
                            subtitle = label?.let { stringResource(it.description) } ?: preset.description,
                            icon = presetIcons[preset.identifier] ?: Icons.AutoMirrored.Outlined.Chat
                        ),
                        isSelected = preset.identifier == selected,
                        onClick = { onSelect(preset.identifier) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(PRESET_COLUMNS - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * What a selectable card shows.
 */
data class SelectableCardContent(val title: String, val subtitle: String, val icon: ImageVector)

@Composable
fun SelectableCard(
    content: SelectableCardContent,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = 1.dp

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = content.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = content.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = content.subtitle,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = DESCRIPTION_MAX_LINES,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val PRESET_COLUMNS = 2
private const val TITLE_MAX_LINES = 2
private const val DESCRIPTION_MAX_LINES = 4

private data class PresetLabel(@StringRes val title: Int, @StringRes val description: Int)

private val presetLabels = mapOf(
    ConversationPresetId.DEFAULT to PresetLabel(R.string.default_room, R.string.default_room_preset),
    ConversationPresetId.VOICE_ROOM to PresetLabel(R.string.voice_room, R.string.voice_room_preset),
    ConversationPresetId.CHANNEL to PresetLabel(R.string.nc_channel, R.string.nc_channel_description),
    ConversationPresetId.ANNOUNCEMENT to PresetLabel(
        R.string.nc_announcement,
        R.string.nc_announcement_description
    )
)

private val presetIcons = mapOf(
    ConversationPresetId.DEFAULT to Icons.AutoMirrored.Outlined.Chat,
    ConversationPresetId.VOICE_ROOM to Icons.AutoMirrored.Outlined.VolumeUp,
    ConversationPresetId.CHANNEL to Icons.Outlined.Podcasts,
    ConversationPresetId.ANNOUNCEMENT to Icons.Outlined.Campaign
)
