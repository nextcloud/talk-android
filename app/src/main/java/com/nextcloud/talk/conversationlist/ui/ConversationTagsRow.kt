/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import android.content.res.Configuration
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.tags.ConversationTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationTagsRow(
    tags: List<ConversationTag>,
    selectedTagId: String?,
    onTagSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.standard_padding)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.standard_half_padding))
    ) {
        item {
            FilterChip(
                selected = selectedTagId == null,
                onClick = { onTagSelected(null) },
                label = { Text(stringResource(R.string.nc_conversation_tags_filter_all)) }
            )
        }
        items(tags, key = { it.id }) { tag ->
            val isSelected = tag.id == selectedTagId
            FilterChip(
                selected = isSelected,
                onClick = { onTagSelected(if (isSelected) null else tag.id) },
                label = { Text(tag.name) }
            )
        }
    }
}

private fun previewTags() =
    listOf(
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
            onTagSelected = {}
        )
    }
}
