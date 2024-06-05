/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import androidx.lifecycle.ViewModel
import com.nextcloud.talk.adapters.items.ContactItem
import com.nextcloud.talk.api.NcAPI
import javax.inject.Inject

class ContactsActivityViewModel @Inject constructor(private val api: NcAPI) : ViewModel()

sealed class ContactsUiState {
    object Loading : ContactsUiState()
    data class Success(val contacts: List<ContactItem>) : ContactsUiState()
    data class Error(val message: String) : ContactsUiState()
}
