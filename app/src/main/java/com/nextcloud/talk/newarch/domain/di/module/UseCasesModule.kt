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

package com.nextcloud.talk.newarch.domain.di.module

import android.app.Application
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.MessagesRepository
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import com.nextcloud.talk.newarch.domain.usecases.*
import com.nextcloud.talk.newarch.features.chat.ChatViewModelFactory
import com.nextcloud.talk.newarch.services.GlobalService
import org.koin.dsl.module

val UseCasesModule = module {
    single { createGetConversationUseCase(get(), get()) }
    single { createGetConversationsUseCase(get(), get()) }
    single { createSetConversationFavoriteValueUseCase(get(), get()) }
    single { createLeaveConversationUseCase(get(), get()) }
    single { createDeleteConversationUseCase(get(), get()) }
    single { createJoinConversationUseCase(get(), get()) }
    single { createExitConversationUseCase(get(), get()) }
    single { createGetProfileUseCase(get(), get()) }
    single { createGetSignalingUseCase(get(), get()) }
    single { createGetCapabilitiesUseCase(get(), get()) }
    factory { createChatViewModelFactory(get(), get(), get(), get(), get(), get()) }
}

fun createGetCapabilitiesUseCase(nextcloudTalkRepository: NextcloudTalkRepository,
                                 apiErrorHandler: ApiErrorHandler
): GetCapabilitiesUseCase {
    return GetCapabilitiesUseCase(nextcloudTalkRepository, apiErrorHandler)
}

fun createGetSignalingSettingsUseCase(nextcloudTalkRepository: NextcloudTalkRepository,
                                      apiErrorHandler: ApiErrorHandler
): GetSignalingSettingsUseCase {
    return GetSignalingSettingsUseCase(nextcloudTalkRepository, apiErrorHandler)
}

fun createGetSignalingUseCase(nextcloudTalkRepository: NextcloudTalkRepository,
                            apiErrorHandler: ApiErrorHandler
): GetSignalingSettingsUseCase {
    return GetSignalingSettingsUseCase(nextcloudTalkRepository, apiErrorHandler)
}

fun createGetProfileUseCase(nextcloudTalkRepository: NextcloudTalkRepository,
                            apiErrorHandler: ApiErrorHandler
): GetProfileUseCase {
    return GetProfileUseCase(nextcloudTalkRepository, apiErrorHandler)
}

fun createSetConversationFavoriteValueUseCase(
        nextcloudTalkRepository: NextcloudTalkRepository,
        apiErrorHandler: ApiErrorHandler
): SetConversationFavoriteValueUseCase {
    return SetConversationFavoriteValueUseCase(nextcloudTalkRepository, apiErrorHandler)
}

fun createGetConversationUseCase(
        nextcloudTalkRepository: NextcloudTalkRepository,
        apiErrorHandler: ApiErrorHandler
): GetConversationUseCase {
    return GetConversationUseCase(nextcloudTalkRepository, apiErrorHandler)
}

fun createGetConversationsUseCase(
        nextcloudTalkRepository: NextcloudTalkRepository,
        apiErrorHandler: ApiErrorHandler
): GetConversationsUseCase {
    return GetConversationsUseCase(nextcloudTalkRepository, apiErrorHandler)
}

fun createLeaveConversationUseCase(
        nextcloudTalkRepository: NextcloudTalkRepository,
        apiErrorHandler: ApiErrorHandler
): LeaveConversationUseCase {
    return LeaveConversationUseCase(nextcloudTalkRepository, apiErrorHandler)
}

fun createDeleteConversationUseCase(
        nextcloudTalkRepository: NextcloudTalkRepository,
        apiErrorHandler: ApiErrorHandler
): DeleteConversationUseCase {
    return DeleteConversationUseCase(nextcloudTalkRepository, apiErrorHandler)
}

fun createJoinConversationUseCase(nextcloudTalkRepository: NextcloudTalkRepository, apiErrorHandler: ApiErrorHandler): JoinConversationUseCase {
    return JoinConversationUseCase(nextcloudTalkRepository, apiErrorHandler)
}

fun createExitConversationUseCase(nextcloudTalkRepository: NextcloudTalkRepository, apiErrorHandler: ApiErrorHandler): ExitConversationUseCase {
    return ExitConversationUseCase(nextcloudTalkRepository, apiErrorHandler)
}

fun createChatViewModelFactory(application: Application, joinConversationUseCase: JoinConversationUseCase, exitConversationUseCase: ExitConversationUseCase, conversationsRepository: ConversationsRepository, messagesRepository: MessagesRepository, globalService: GlobalService): ChatViewModelFactory {
    return ChatViewModelFactory(application, joinConversationUseCase, exitConversationUseCase, conversationsRepository, messagesRepository, globalService)
}
