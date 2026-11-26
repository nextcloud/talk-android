/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nextcloud.talk.R

/** The delete action of a selection; it asks for a confirmation before [onDelete] is called. */
@Composable
internal fun SelectionDeleteAction(selectedCount: Int, onDelete: () -> Unit) {
    var showConfirmation by remember { mutableStateOf(false) }
    IconButton(onClick = { showConfirmation = true }) {
        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.nc_delete))
    }
    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            icon = { Icon(painterResource(R.drawable.ic_delete_black_24dp), contentDescription = null) },
            title = {
                Text(pluralStringResource(R.plurals.nc_delete_selected_messages, selectedCount, selectedCount))
            },
            text = { Text(stringResource(R.string.nc_delete_selected_messages_are_you_sure)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmation = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.nc_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text(stringResource(R.string.nc_cancel))
                }
            }
        )
    }
}
