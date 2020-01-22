package com.nextcloud.talk.newarch.features.contactsflow

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nextcloud.talk.newarch.domain.usecases.GetContactsUseCase
import com.nextcloud.talk.newarch.services.GlobalService

class ContactsViewModelFactory constructor(
        private val application: Application,
        private val getContactsUseCase: GetContactsUseCase,
        private val globalService: GlobalService
) : ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return ContactsViewModel(
                application, getContactsUseCase, globalService
        ) as T
    }
}
