/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.dialog;

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.nextcloud.talk.R

@Composable
fun IntroduceUnifiedPushDialog(
    onResponse: (Boolean) -> Unit
) {
    var showDialog by remember { mutableStateOf(true) }
    if (showDialog) {
        AlertDialog(
            confirmButton = {
                TextButton(onClick = {
                    onResponse(true)
                    showDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            onDismissRequest = {
                onResponse(false)
                showDialog = false
            },
            dismissButton = {
                TextButton(onClick = {
                    onResponse(false)
                    showDialog = false
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = {
                Text(stringResource(R.string.unifiedpush))
            },
            text = {
                Text(stringResource(R.string.nc_dialog_introduce_unifiedpush_selection))
            },
        )
    }
}
