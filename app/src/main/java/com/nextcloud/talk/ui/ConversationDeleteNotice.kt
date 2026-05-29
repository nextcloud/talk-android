/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.preview.ComposePreviewUtils

@Composable
fun ConversationDeleteNoticeView(
    data: ConversationDeleteNoticeViewData,
    viewThemeUtils: ViewThemeUtils,
    onDeleteNow: () -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = remember { viewThemeUtils.getColorScheme(context) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(start = 8.dp, end = 0.dp)) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.baseline_info_24),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(24.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 8.dp)
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.nc_conversation_auto_delete_info,
                        data.retentionDays,
                        data.retentionDays
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            ConversationDeleteNoticeActions(
                isModeratorOrOwner = data.isModeratorOrOwner,
                onDeleteNow = onDeleteNow,
                onKeep = onKeep,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun ConversationDeleteNoticeActions(
    isModeratorOrOwner: Boolean,
    onDeleteNow: () -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        if (isModeratorOrOwner) {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_more_vert_24px),
                        contentDescription = null
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.nc_delete_now)) },
                        onClick = {
                            expanded = false
                            onDeleteNow()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.nc_keep))
                        },
                        onClick = {
                            expanded = false
                            onKeep()
                        }
                    )
                }
            }
        } else {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_baseline_close_24),
                    contentDescription = stringResource(R.string.nc_common_dismiss)
                )
            }
        }
    }
}

data class ConversationDeleteNoticeViewData(val retentionDays: Int, val isModeratorOrOwner: Boolean)

@Preview(name = "Dark Mode", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ConversationDeleteNoticePreviewDark() {
    ConversationDeleteNoticePreview()
}

@Preview(name = "R-t-L", locale = "ar")
@Composable
fun ConversationDeleteNoticePreviewRtl() {
    ConversationDeleteNoticePreview()
}

@Preview(name = "Light Mode / Read-only")
@Composable
fun ConversationDeleteNoticePreviewReadOnly() {
    ConversationDeleteNoticePreview(isModeratorOrOwner = false)
}

@Preview(name = "Dark Mode / Read-only", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ConversationDeleteNoticePreviewDarkReadOnly() {
    ConversationDeleteNoticePreview(isModeratorOrOwner = false)
}

@Preview(name = "R-t-L / Read-only", locale = "ar")
@Composable
fun ConversationDeleteNoticePreviewRtlReadOnly() {
    ConversationDeleteNoticePreview(isModeratorOrOwner = false)
}

@Suppress("MagicNumber")
@Preview(name = "Light Mode")
@Composable
fun ConversationDeleteNoticePreview(retentionDays: Int = 7, isModeratorOrOwner: Boolean = true) {
    val context = LocalContext.current
    val previewUtils = ComposePreviewUtils.getInstance(context)
    val viewThemeUtils = previewUtils.viewThemeUtils
    val colorScheme = viewThemeUtils.getColorScheme(context)

    MaterialTheme(colorScheme = colorScheme) {
        ConversationDeleteNoticeView(
            data = ConversationDeleteNoticeViewData(
                retentionDays = retentionDays,
                isModeratorOrOwner = isModeratorOrOwner
            ),
            viewThemeUtils = viewThemeUtils,
            onDeleteNow = {},
            onKeep = {},
            onDismiss = {}
        )
    }
}
