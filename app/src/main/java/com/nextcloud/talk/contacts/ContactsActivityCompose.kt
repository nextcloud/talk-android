/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import autodagger.AutoInjector
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.components.SetStatusBarColor
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ContactsActivityCompose : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var contactsViewModel: ContactsViewModel

    @SuppressLint("UnrememberedMutableState")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        contactsViewModel = ViewModelProvider(this, viewModelFactory)[ContactsViewModel::class.java]
        setContent {
            val isAddParticipants = intent.getBooleanExtra("isAddParticipants", false)
            contactsViewModel.updateIsAddParticipants(isAddParticipants)
            if (isAddParticipants) {
                contactsViewModel.updateShareTypes(
                    listOf(
                        ShareType.Group.shareType,
                        ShareType.Email.shareType,
                        ShareType.Circle.shareType
                    )
                )
                contactsViewModel.getContactsFromSearchParams()
            }
            val colorScheme = viewThemeUtils.getColorScheme(this)
            val uiState = contactsViewModel.contactsViewState.collectAsStateWithLifecycle()
            val selectedParticipants = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra("selectedParticipants", AutocompleteUser::class.java)
                        ?: emptyList()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra("selectedParticipants") ?: emptyList()
                }
            }.toSet().toMutableList()
            contactsViewModel.updateSelectedParticipants(selectedParticipants)

            MaterialTheme(
                colorScheme = colorScheme
            ) {
                ContactsScreen(
                    contactsViewModel = contactsViewModel,
                    uiState = uiState.value
                )
            }

            SetStatusBarColor()
        }
    }
}

class CompanionClass {
    companion object {
        internal val TAG = ContactsActivityCompose::class.simpleName
        internal const val ROOM_TYPE_ONE_ONE = "1"
    }
}
