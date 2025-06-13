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
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                }
            }
        },
        actions = {
            VerticallyCenteredRow {
                IconButton(onClick = onStartSearch) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_icon))
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
