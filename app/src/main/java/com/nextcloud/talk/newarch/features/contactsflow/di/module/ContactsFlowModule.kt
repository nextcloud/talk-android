/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 */

package com.nextcloud.talk.newarch.features.contactsflow.di.module

import android.app.Application
import com.nextcloud.talk.newarch.domain.usecases.AddParticipantToConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.CreateConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetContactsUseCase
import com.nextcloud.talk.newarch.domain.usecases.SetConversationPasswordUseCase
import com.nextcloud.talk.newarch.features.contactsflow.contacts.ContactsViewModelFactory
import com.nextcloud.talk.newarch.features.contactsflow.groupconversation.GroupConversationViewModelFactory
import com.nextcloud.talk.newarch.services.GlobalService
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val ContactsFlowModule = module {
    factory {
        createContactsViewModelFactory(
                androidApplication(), get(), get(), get(), get()
        )
    }
    factory {
        createGroupConversationViewModelFactory(
                androidApplication(), get(), get(), get()
        )
    }
}

fun createGroupConversationViewModelFactory(
        application: Application,
        createConversationUseCase: CreateConversationUseCase,
        setConversationPasswordUseCase: SetConversationPasswordUseCase,
        globalService: GlobalService
): GroupConversationViewModelFactory {
    return GroupConversationViewModelFactory(application, createConversationUseCase, setConversationPasswordUseCase, globalService)
}

fun createContactsViewModelFactory(
        application: Application,
        getContactsUseCase: GetContactsUseCase,
        createConversationUseCase: CreateConversationUseCase,
        addParticipantToConversationUseCase: AddParticipantToConversationUseCase,
        globalService: GlobalService
): ContactsViewModelFactory {
    return ContactsViewModelFactory(
            application, getContactsUseCase, createConversationUseCase, addParticipantToConversationUseCase, globalService
    )
}