/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CallToAction
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R

/** Size of the view mode button, so the call header keeps its height while the button is hidden. */
val CALL_VIEW_MODE_BUTTON_SIZE = 40.dp

/** Button in the call header that opens a menu to switch between the grid and the speaker view. */
@Composable
fun CallViewModeButton(viewMode: CallViewMode, onViewModeSelected: (CallViewMode) -> Unit) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { isMenuExpanded = true },
            modifier = Modifier.size(CALL_VIEW_MODE_BUTTON_SIZE)
        ) {
            Icon(
                imageVector = viewMode.icon,
                contentDescription = stringResource(R.string.nc_call_view_mode),
                tint = Color.White
            )
        }

        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            CallViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.titleRes)) },
                    leadingIcon = { Icon(imageVector = mode.icon, contentDescription = null) },
                    trailingIcon = if (mode == viewMode) {
                        { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        isMenuExpanded = false
                        onViewModeSelected(mode)
                    }
                )
            }
        }
    }
}

private val CallViewMode.icon: ImageVector
    get() = when (this) {
        CallViewMode.GRID -> Icons.Filled.GridView
        CallViewMode.SPEAKER -> Icons.Filled.AccountBox
        CallViewMode.SPEAKER_WITH_PARTICIPANT_BAR -> Icons.Filled.CallToAction
    }

@get:StringRes
private val CallViewMode.titleRes: Int
    get() = when (this) {
        CallViewMode.GRID -> R.string.nc_call_view_mode_grid
        CallViewMode.SPEAKER -> R.string.nc_call_view_mode_speaker
        CallViewMode.SPEAKER_WITH_PARTICIPANT_BAR -> R.string.nc_call_view_mode_speaker_with_participant_bar
    }

@Preview
@Composable
fun CallViewModeButtonPreview() {
    CallViewModeButton(viewMode = CallViewMode.SPEAKER_WITH_PARTICIPANT_BAR, onViewModeSelected = {})
}
