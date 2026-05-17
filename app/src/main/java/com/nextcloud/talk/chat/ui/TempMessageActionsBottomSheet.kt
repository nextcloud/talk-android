/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempMessageActionsBottomSheet(
    showResend: Boolean,
    showEdit: Boolean,
    showDelete: Boolean,
    onResend: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        TempMessageActionsContent(
            showResend = showResend,
            showEdit = showEdit,
            showDelete = showDelete,
            onResend = onResend,
            onEdit = onEdit,
            onDelete = onDelete,
            onCopy = onCopy,
            onDismiss = onDismiss
        )
    }
}

@Suppress("LongParameterList")
@Composable
internal fun TempMessageActionsContent(
    showResend: Boolean,
    showEdit: Boolean,
    showDelete: Boolean,
    onResend: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        if (showResend) {
            MessageActionItem(
                iconRes = R.drawable.ic_send_24px,
                text = stringResource(R.string.resend_message),
                onClick = {
                    onResend()
                    onDismiss()
                }
            )
        }
        MessageActionItem(
            iconRes = R.drawable.ic_content_copy,
            text = stringResource(R.string.nc_copy_message),
            onClick = {
                onCopy()
                onDismiss()
            }
        )
        if (showEdit) {
            MessageActionItem(
                iconRes = R.drawable.ic_edit_24,
                text = stringResource(R.string.nc_edit_message),
                onClick = {
                    onEdit()
                    onDismiss()
                }
            )
        }
        if (showDelete) {
            MessageActionItem(
                iconRes = R.drawable.ic_delete,
                text = stringResource(R.string.nc_delete_message),
                onClick = {
                    onDelete()
                    onDismiss()
                }
            )
        }
    }
}

@Preview(showBackground = true, name = "Light — all items")
@Preview(showBackground = true, name = "Dark — all items", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewTempMessageActionsContent() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            TempMessageActionsContent(
                showResend = true,
                showEdit = true,
                showDelete = true,
                onResend = {},
                onEdit = {},
                onDelete = {},
                onCopy = {},
                onDismiss = {}
            )
        }
    }
}
