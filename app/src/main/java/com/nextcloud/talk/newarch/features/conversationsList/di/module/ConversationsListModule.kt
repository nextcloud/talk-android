/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.newarch.features.conversationsList.di.module

import android.app.Application
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import com.nextcloud.talk.newarch.domain.usecases.DeleteConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetConversationsUseCase
import com.nextcloud.talk.newarch.domain.usecases.LeaveConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.SetConversationFavoriteValueUseCase
import com.nextcloud.talk.newarch.features.conversationsList.ConversationListViewModelFactory
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val ConversationsListModule = module {
    //viewModel { ConversationsListViewModel(get(), get()) }
    factory {
        createConversationListViewModelFactory(
                androidApplication(), get(), get(), get
        (), get(), get(), get()
        )
    }
}

fun createConversationListViewModelFactory(
        application: Application,
        getConversationsUseCase:
        GetConversationsUseCase,
        setConversationFavoriteValueUseCase: SetConversationFavoriteValueUseCase,
        leaveConversationUseCase: LeaveConversationUseCase,
        deleteConversationUseCase: DeleteConversationUseCase,
        conversationsRepository: ConversationsRepository,
        usersRepository: UsersRepository
): ConversationListViewModelFactory {
    return ConversationListViewModelFactory(
            application, getConversationsUseCase,
            setConversationFavoriteValueUseCase, leaveConversationUseCase, deleteConversationUseCase,
            conversationsRepository, usersRepository
    )
}