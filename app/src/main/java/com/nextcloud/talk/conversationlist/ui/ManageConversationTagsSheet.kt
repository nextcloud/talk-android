/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R
import com.nextcloud.talk.conversationtags.viewmodels.ConversationTagsViewModel.TagActionUiState
import com.nextcloud.talk.models.json.tags.ConversationTag

@Suppress("LongMethod")
@Composable
fun ManageConversationTagsSheetContent(
    tags: List<ConversationTag>,
    tagActionState: TagActionUiState,
    callbacks: ManageConversationTagsCallbacks
) {
    val (onCreateTag, onRenameTag, onDeleteTag, onMoveTag, onResetActionState) = callbacks
    var newTagName by remember { mutableStateOf("") }
    var editingTagId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    var tagPendingDelete by remember { mutableStateOf<ConversationTag?>(null) }

    LaunchedEffect(tagActionState) {
        if (tagActionState is TagActionUiState.Success) {
            editingTagId = null
            newTagName = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(dimensionResource(R.dimen.standard_dialog_padding))
    ) {
        Text(
            text = stringResource(R.string.nc_conversation_tags_manage),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (tagActionState is TagActionUiState.Error) {
            Text(
                text = tagErrorMessage(tagActionState.errorType),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimensionResource(R.dimen.standard_half_padding)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        val customTags = tags.filter { it.type == ConversationTag.TYPE_CUSTOM }

        if (customTags.isEmpty()) {
            Text(
                text = stringResource(R.string.nc_conversation_tags_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensionResource(R.dimen.standard_half_padding)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        tags.forEachIndexed { index, tag ->
            val isFavorites = tag.type == ConversationTag.TYPE_FAVORITES
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensionResource(R.dimen.standard_half_padding)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (editingTagId == tag.id) {
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = {
                            editingName = it
                            onResetActionState()
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    TextButton(onClick = { onRenameTag(tag.id, editingName) }) {
                        Text(stringResource(R.string.nc_conversation_tags_save))
                    }
                } else {
                    Text(
                        text = if (isFavorites) stringResource(R.string.nc_conversation_tags_favorites) else tag.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { onMoveTag(tag.id, -1) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null)
                    }
                    IconButton(onClick = { onMoveTag(tag.id, 1) }, enabled = index < tags.lastIndex) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
                    }
                    if (isFavorites) {
                        // Empty, disabled placeholders reserve the same width as the edit/delete
                        // buttons on the other rows, so the up/down arrows line up across all rows.
                        IconButton(onClick = {}, enabled = false) {}
                        IconButton(onClick = {}, enabled = false) {}
                    } else {
                        IconButton(
                            onClick = {
                                editingTagId = tag.id
                                editingName = tag.name
                                onResetActionState()
                            }
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.nc_conversation_tags_rename)
                            )
                        }
                        IconButton(onClick = { tagPendingDelete = tag }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.nc_conversation_tags_delete)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(R.dimen.standard_half_padding)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newTagName,
                onValueChange = {
                    newTagName = it
                    onResetActionState()
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.nc_conversation_tags_name_hint)) },
                placeholder = { Text(stringResource(R.string.nc_conversation_tags_new)) }
            )
            IconButton(
                onClick = {
                    if (newTagName.isNotBlank()) {
                        onCreateTag(newTagName)
                    }
                }
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.nc_conversation_tags_create))
            }
        }
    }

    val tagToDelete = tagPendingDelete
    if (tagToDelete != null) {
        AlertDialog(
            onDismissRequest = { tagPendingDelete = null },
            title = { Text(stringResource(R.string.nc_conversation_tags_delete_confirm_title)) },
            text = { Text(stringResource(R.string.nc_conversation_tags_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTag(tagToDelete.id)
                        tagPendingDelete = null
                    }
                ) {
                    Text(stringResource(R.string.nc_conversation_tags_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { tagPendingDelete = null }) {
                    Text(stringResource(R.string.nc_cancel))
                }
            }
        )
    }
}

@Composable
private fun tagErrorMessage(errorType: String?): String =
    when (errorType) {
        "name" -> stringResource(R.string.nc_conversation_tags_error_name)
        "limit" -> stringResource(R.string.nc_conversation_tags_error_limit)
        else -> stringResource(R.string.nc_conversation_tags_error_generic)
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
private fun ManageConversationTagsSheetPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ManageConversationTagsSheetContent(
                tags = previewTags(),
                tagActionState = TagActionUiState.None,
                callbacks = ManageConversationTagsCallbacks(
                    onCreateTag = {},
                    onRenameTag = { _, _ -> },
                    onDeleteTag = {},
                    onMoveTag = { _, _ -> },
                    onResetActionState = {}
                )
            )
        }
    }
}
