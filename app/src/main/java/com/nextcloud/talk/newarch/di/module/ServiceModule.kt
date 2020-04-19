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

package com.nextcloud.talk.newarch.di.module

import android.content.Context
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.MessagesRepository
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.GetConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.JoinConversationUseCase
import com.nextcloud.talk.newarch.services.GlobalService
import com.nextcloud.talk.newarch.services.shortcuts.ShortcutService
import com.nextcloud.talk.newarch.utils.NetworkComponents
import okhttp3.OkHttpClient
import org.koin.dsl.module
import java.net.CookieManager

val ServiceModule = module {
    single { createGlobalService(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { createShortcutService(get(), get(), get()) }
}

fun createGlobalService(usersRepository: UsersRepository, cookieManager: CookieManager,
                        okHttpClient: OkHttpClient, apiErrorHandler: ApiErrorHandler, conversationsRepository: ConversationsRepository,
                        messagesRepository: MessagesRepository, networkComponents: NetworkComponents, getConversationUseCase: GetConversationUseCase, joinConversationUseCase: JoinConversationUseCase): GlobalService {
    return GlobalService(usersRepository, cookieManager, okHttpClient, apiErrorHandler, conversationsRepository, messagesRepository, networkComponents, joinConversationUseCase, getConversationUseCase)
}

fun createShortcutService(context: Context, conversationsRepository: ConversationsRepository, conversationsService: GlobalService): ShortcutService {
    return ShortcutService(context, conversationsRepository, conversationsService)
}