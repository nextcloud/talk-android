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
import com.nextcloud.talk.models.RetrofitBucket
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ContactsActivityViewModel @Inject constructor(
    private val ncApiCoroutines: NcApiCoroutines,
    private val userManager: UserManager
) : ViewModel() {

    private val _contactsViewState = MutableStateFlow<ContactsUiState>(ContactsUiState.None)
    val contactsViewState: StateFlow<ContactsUiState> = _contactsViewState

    init {
        getContactsFromSearchParams()
    }

    private fun getContactsFromSearchParams() {
        val currentUser = userManager.currentUser.blockingGet()
        val credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)
        val retrofitBucket: RetrofitBucket =
            ApiUtils.getRetrofitBucketForContactsSearchFor14(currentUser!!.baseUrl!!, null)
        val modifiedQueryMap: HashMap<String, Any> = HashMap(retrofitBucket.queryMap)
        modifiedQueryMap["limit"] = 50
        val shareTypesList: ArrayList<String> = ArrayList()
        shareTypesList.add("0")
        shareTypesList.add("1")

        modifiedQueryMap["shareTypes[]"] = shareTypesList
        _contactsViewState.value = ContactsUiState.Loading
        viewModelScope.launch {
            try {
                val contacts = ncApiCoroutines.getContactsWithSearchParam(
                    credentials,
                    retrofitBucket.url,
                    shareTypesList,
                    modifiedQueryMap
                )

                val contactsList: List<AutocompleteUser>? = contacts.ocs!!.data
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
