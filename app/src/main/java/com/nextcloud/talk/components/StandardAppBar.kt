/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.components

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardAppBar(
    title: String,
    menuItems: List<Pair<String, () -> Unit>>?,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors()
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    var expanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(text = title) },
        colors = colors,
        navigationIcon = {
            IconButton(
                onClick = { backDispatcher?.onBackPressed() }
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_back_black_24dp),
                    contentDescription = stringResource(R.string.back_button)
                )
            }
        },
        actions = {
            if (!menuItems.isNullOrEmpty()) {
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_more_vert_24px),
                            contentDescription = stringResource(R.string.nc_common_more_options)
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(color = colorResource(id = R.color.bg_default))
                    ) {
                        menuItems?.forEach { (label, action) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    action()
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Preview(name = "Light Mode")
@Composable
fun AppBarPreview() {
    StandardAppBar("title", null)
}

@Preview(name = "RTL / Arabic", locale = "ar")
@Composable
fun AppBarPreviewRtlPreview() {
    StandardAppBar("عنوان", null)
}
