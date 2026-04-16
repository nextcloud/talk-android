/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.components

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.components.VerticallyCenteredRow
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsAppBar(isAddParticipants: Boolean, autocompleteUsers: List<AutocompleteUser>, onStartSearch: () -> Unit) {
    val context = LocalContext.current
    TopAppBar(
        modifier = Modifier
            .height(60.dp),
        title = {
            VerticallyCenteredRow {
                Text(
                    text = if (isAddParticipants) {
                        stringResource(R.string.nc_participants_add)
                    } else {
                        stringResource(R.string.nc_new_conversation)
                    }
                )
            }
        },
        navigationIcon = {
            VerticallyCenteredRow {
                IconButton(onClick = { (context as? Activity)?.finish() }) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.ic_arrow_back_black_24dp),
                        contentDescription = stringResource(R.string.back_button)
                    )
                }
            }
        },
        actions = {
            VerticallyCenteredRow {
                IconButton(onClick = onStartSearch) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.ic_search_grey),
                        contentDescription = stringResource(R.string.search_icon)
                    )
                }
                if (isAddParticipants) {
                    Text(
                        text = stringResource(id = R.string.nc_contacts_done),
                        modifier = Modifier.clickable {
                            val resultIntent = Intent().apply {
                                putParcelableArrayListExtra("selectedParticipants", ArrayList(autocompleteUsers))
                            }
                            (context as? Activity)?.setResult(Activity.RESULT_OK, resultIntent)
                            (context as? Activity)?.finish()
                        }
                    )
                }
            }
        }
    )
}

@Preview(name = "Light Mode")
@Preview(
    name = "Dark Mode",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Preview(name = "RTL / Arabic", locale = "ar")
@Composable
fun ContactsAppBarNewConversationPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            ContactsAppBar(
                isAddParticipants = false,
                autocompleteUsers = emptyList(),
                onStartSearch = {}
            )
        }
    }
}
