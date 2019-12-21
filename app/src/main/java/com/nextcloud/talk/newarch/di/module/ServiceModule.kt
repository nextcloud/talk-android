package com.nextcloud.talk.newarch.di.module

import android.content.Context
import android.content.pm.ShortcutManager
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.GetConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.JoinConversationUseCase
import com.nextcloud.talk.newarch.utils.ConversationService
import com.nextcloud.talk.newarch.utils.ShortcutService
import okhttp3.OkHttpClient
import org.koin.dsl.module
import java.net.CookieManager

val ServiceModule = module {
    single { createConversationsService(get(), get(), get(), get(), get(), get()) }
    single { createShortcutService(get(), get(), get()) }

}

fun createConversationsService(usersRepository: UsersRepository, cookieManager: CookieManager,
                               okHttpClient: OkHttpClient, conversationsRepository: ConversationsRepository,
                               getConversationUseCase: GetConversationUseCase, joinConversationUseCase: JoinConversationUseCase): ConversationService {
    return ConversationService(usersRepository, cookieManager, okHttpClient, conversationsRepository, joinConversationUseCase, getConversationUseCase)
}

fun createShortcutService(context: Context, conversationsRepository: ConversationsRepository, conversationsService: ConversationService): ShortcutService {
    return ShortcutService(context, conversationsRepository, conversationsService)
}