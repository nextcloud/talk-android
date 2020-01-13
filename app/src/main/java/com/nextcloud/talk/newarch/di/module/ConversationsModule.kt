package com.nextcloud.talk.newarch.di.module

import android.app.Application
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.MessagesRepository
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import com.nextcloud.talk.newarch.domain.usecases.*
import com.nextcloud.talk.newarch.features.chat.ChatViewModelFactory
import com.nextcloud.talk.newarch.services.GlobalService
import org.koin.dsl.module

val ConversationsModule = module {
    single { createGetConversationUseCase(get(), get()) }
    single { createGetConversationsUseCase(get(), get()) }
    single { createSetConversationFavoriteValueUseCase(get(), get()) }
    single { createLeaveConversationUseCase(get(), get()) }
    single { createDeleteConversationUseCase(get(), get()) }
    single { createJoinConversationUseCase(get(), get()) }
    single { createExitConversationUseCase(get(), get()) }
    factory { createChatViewModelFactory(get(), get(), get(), get(), get(), get()) }
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