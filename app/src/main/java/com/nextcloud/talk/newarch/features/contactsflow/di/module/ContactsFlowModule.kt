package com.nextcloud.talk.newarch.features.contactsflow.di.module

import android.app.Application
import com.nextcloud.talk.newarch.domain.usecases.GetContactsUseCase
import com.nextcloud.talk.newarch.features.contactsflow.ContactsViewModelFactory
import com.nextcloud.talk.newarch.services.GlobalService
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val ContactsFlowModule = module {
    factory {
        createContactsViewModelFactory(
                androidApplication(), get(), get()
        )
    }
}

fun createContactsViewModelFactory(
        application: Application,
        getContactsUseCase: GetContactsUseCase,
        globalService: GlobalService
): ContactsViewModelFactory {
    return ContactsViewModelFactory(
            application, getContactsUseCase, globalService
    )
}