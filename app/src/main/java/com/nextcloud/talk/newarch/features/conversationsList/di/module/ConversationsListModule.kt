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

import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.di.module.createApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.NextcloudTalkRepository
import com.nextcloud.talk.newarch.domain.usecases.GetConversationsUseCase
import com.nextcloud.talk.newarch.features.conversationsList.ConversationListViewModelFactory
import com.nextcloud.talk.utils.database.user.UserUtils
import org.koin.dsl.module

val ConversationsListModule = module {
  single { createGetConversationsUseCase(get(), createApiErrorHandler()) }
  //viewModel { ConversationsListViewModel(get(), get()) }
  factory { createConversationListViewModelFactory(get(), get()) }
}

fun createGetConversationsUseCase(
  nextcloudTalkRepository: NextcloudTalkRepository,
  apiErrorHandler: ApiErrorHandler
): GetConversationsUseCase {
  return GetConversationsUseCase(nextcloudTalkRepository, apiErrorHandler)
}

fun createConversationListViewModelFactory(conversationsUseCase: GetConversationsUseCase,
  userUtils: UserUtils): ConversationListViewModelFactory {
  return ConversationListViewModelFactory(conversationsUseCase, userUtils)
}