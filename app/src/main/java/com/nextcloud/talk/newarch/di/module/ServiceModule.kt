package com.nextcloud.talk.newarch.di.module

import android.content.Context
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.GetConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.JoinConversationUseCase
import com.nextcloud.talk.newarch.utils.GlobalService
import com.nextcloud.talk.newarch.utils.ShortcutService
import okhttp3.OkHttpClient
import org.koin.dsl.module
import java.net.CookieManager

val ServiceModule = module {
    single { createGlobalService(get(), get(), get(), get(), get(), get()) }
    single { createShortcutService(get(), get(), get()) }

}

fun createGlobalService(usersRepository: UsersRepository, cookieManager: CookieManager,
                        okHttpClient: OkHttpClient, conversationsRepository: ConversationsRepository,
                        getConversationUseCase: GetConversationUseCase, joinConversationUseCase: JoinConversationUseCase): GlobalService {
    return GlobalService(usersRepository, cookieManager, okHttpClient, conversationsRepository, joinConversationUseCase, getConversationUseCase)
}

fun createShortcutService(context: Context, conversationsRepository: ConversationsRepository, conversationsService: GlobalService): ShortcutService {
    return ShortcutService(context, conversationsRepository, conversationsService)
}