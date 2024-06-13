/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ContactsActivityViewModel @Inject constructor(private val ncApiCoroutines: NcApiCoroutines) : ViewModel() {

    private val _contactsViewState = MutableStateFlow<ContactsUiState>(ContactsUiState.None)
    val contactsViewState: StateFlow<ContactsUiState> = _contactsViewState

    fun getContactsFromSearchParams(
        baseUrl: String,
        ocsApiVersion: String,
        shareList: List<String>,
        options: Map<String, Any>
    ) {
        _contactsViewState.value = ContactsUiState.Loading
        viewModelScope.launch {
            try {
                val contacts = ncApiCoroutines.getContactsWithSearchParam(
                    baseUrl,
                    ocsApiVersion,
                    shareList,
                    options
                )
                val contactsList: List<AutocompleteUser>? = contacts.data
                _contactsViewState.value = ContactsUiState.Success(contactsList)
            } catch (exception: Exception) {
                _contactsViewState.value = ContactsUiState.Error(exception.message ?: "")
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
