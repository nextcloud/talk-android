/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import kotlinx.coroutines.launch
import javax.inject.Inject

class ContactsActivityViewModel @Inject constructor(private val ncApiCoroutines: NcApiCoroutines) : ViewModel() {

    var contactsViewState by mutableStateOf<ContactsUiState>(ContactsUiState.None)

    fun getContactsFromSearchParams(
        baseUrl: String,
        ocsApiVersion: String,
        shareList: List<String>,
        options: Map<String, Any>
    )  {
        contactsViewState = ContactsUiState.Loading
        viewModelScope.launch {
            try {
                val contacts = ncApiCoroutines.getContactsWithSearchParam(
                    baseUrl,
                    ocsApiVersion,
                    shareList,
                    options
                )
                val contactsList: List<AutocompleteUser>? = contacts.data
            } catch (exception: Exception) {
                exception.message?.let { ContactsUiState.Error(it) }
            }
        }
    }
}

sealed class ContactsUiState {

    object None : ContactsUiState()
    object Loading : ContactsUiState()
    data class Success(val contacts: List<AutocompleteUser>?) : ContactsUiState()
    data class Error(val message: String) : ContactsUiState()
}
