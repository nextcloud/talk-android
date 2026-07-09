/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import android.content.res.Configuration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.tags.ConversationTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationTagsRow(
    tags: List<ConversationTag>,
    selectedTagId: String?,
    onTagSelected: (String?) -> Unit,
    onManageTagsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.standard_padding)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.standard_half_padding))
    ) {
        item {
            TagFilterChip(
                selected = selectedTagId == null,
                label = stringResource(R.string.nc_conversation_tags_filter_all),
                onClick = { onTagSelected(null) },
                onLongClick = onManageTagsClick
            )
        }
        items(tags, key = { it.id }) { tag ->
            val isSelected = tag.id == selectedTagId
            val isFavorites = tag.type == ConversationTag.TYPE_FAVORITES
            TagFilterChip(
                selected = isSelected,
                label = if (isFavorites) stringResource(R.string.nc_conversation_tags_favorites) else tag.name,
                onClick = { onTagSelected(if (isSelected) null else tag.id) },
                onLongClick = onManageTagsClick
            )
        }
    }
}

/** Detects long-press via the chip's own interactionSource so its native ripple stays untouched. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterChip(selected: Boolean, label: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    val longPressTimeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis
    var suppressClick by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Press) {
                suppressClick = false
                delay(longPressTimeoutMillis)
                suppressClick = true
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            }
        }
    }

    FilterChip(
        selected = selected,
        onClick = {
            if (!suppressClick) onClick()
            suppressClick = false
        },
        label = { Text(label) },
        interactionSource = interactionSource
    )
}

private fun previewTags() =
    listOf(
        ConversationTag(id = "favorites", name = "", type = ConversationTag.TYPE_FAVORITES),
        ConversationTag(id = "1", name = "Work", sortOrder = 0),
        ConversationTag(id = "2", name = "Family", sortOrder = 1),
        ConversationTag(id = "3", name = "Projects", sortOrder = 2)
    )

@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConversationTagsRowPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        ConversationTagsRow(
            tags = previewTags(),
            selectedTagId = "2",
            onTagSelected = {},
            onManageTagsClick = {}
        )
    }
}
