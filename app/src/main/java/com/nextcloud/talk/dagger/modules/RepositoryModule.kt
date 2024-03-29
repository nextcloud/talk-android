/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.dagger.modules

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.chat.data.ChatRepository
import com.nextcloud.talk.chat.data.network.NetworkChatRepositoryImpl
import com.nextcloud.talk.conversation.repository.ConversationRepository
import com.nextcloud.talk.conversation.repository.ConversationRepositoryImpl
import com.nextcloud.talk.conversationinfoedit.data.ConversationInfoEditRepository
import com.nextcloud.talk.conversationinfoedit.data.ConversationInfoEditRepositoryImpl
import com.nextcloud.talk.conversationlist.data.ConversationsListRepository
import com.nextcloud.talk.conversationlist.data.ConversationsListRepositoryImpl
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.storage.ArbitraryStoragesRepository
import com.nextcloud.talk.data.storage.ArbitraryStoragesRepositoryImpl
import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.data.user.UsersRepositoryImpl
import com.nextcloud.talk.invitation.data.InvitationsRepository
import com.nextcloud.talk.invitation.data.InvitationsRepositoryImpl
import com.nextcloud.talk.openconversations.data.OpenConversationsRepository
import com.nextcloud.talk.openconversations.data.OpenConversationsRepositoryImpl
import com.nextcloud.talk.polls.repositories.PollRepository
import com.nextcloud.talk.polls.repositories.PollRepositoryImpl
import com.nextcloud.talk.raisehand.RequestAssistanceRepository
import com.nextcloud.talk.raisehand.RequestAssistanceRepositoryImpl
import com.nextcloud.talk.remotefilebrowser.repositories.RemoteFileBrowserItemsRepository
import com.nextcloud.talk.remotefilebrowser.repositories.RemoteFileBrowserItemsRepositoryImpl
import com.nextcloud.talk.repositories.callrecording.CallRecordingRepository
import com.nextcloud.talk.repositories.callrecording.CallRecordingRepositoryImpl
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.repositories.conversations.ConversationsRepositoryImpl
import com.nextcloud.talk.repositories.reactions.ReactionsRepository
import com.nextcloud.talk.repositories.reactions.ReactionsRepositoryImpl
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepositoryImpl
import com.nextcloud.talk.shareditems.repositories.SharedItemsRepository
import com.nextcloud.talk.shareditems.repositories.SharedItemsRepositoryImpl
import com.nextcloud.talk.translate.repositories.TranslateRepository
import com.nextcloud.talk.translate.repositories.TranslateRepositoryImpl
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient

@Module
class RepositoryModule {

    @Provides
    fun provideConversationsRepository(ncApi: NcApi, userProvider: CurrentUserProviderNew): ConversationsRepository {
        return ConversationsRepositoryImpl(ncApi, userProvider)
    }

    @Provides
    fun provideSharedItemsRepository(ncApi: NcApi, dateUtils: DateUtils): SharedItemsRepository {
        return SharedItemsRepositoryImpl(ncApi, dateUtils)
    }

    @Provides
    fun provideUnifiedSearchRepository(ncApi: NcApi, userProvider: CurrentUserProviderNew): UnifiedSearchRepository {
        return UnifiedSearchRepositoryImpl(ncApi, userProvider)
    }

    @Provides
    fun provideDialogPollRepository(ncApi: NcApi, userProvider: CurrentUserProviderNew): PollRepository {
        return PollRepositoryImpl(ncApi, userProvider)
    }

    @Provides
    fun provideRemoteFileBrowserItemsRepository(
        okHttpClient: OkHttpClient,
        userProvider: CurrentUserProviderNew
    ): RemoteFileBrowserItemsRepository {
        return RemoteFileBrowserItemsRepositoryImpl(okHttpClient, userProvider)
    }

    @Provides
    fun provideUsersRepository(database: TalkDatabase): UsersRepository {
        return UsersRepositoryImpl(database.usersDao())
    }

    @Provides
    fun provideArbitraryStoragesRepository(database: TalkDatabase): ArbitraryStoragesRepository {
        return ArbitraryStoragesRepositoryImpl(database.arbitraryStoragesDao())
    }

    @Provides
    fun provideReactionsRepository(ncApi: NcApi, userProvider: CurrentUserProviderNew): ReactionsRepository {
        return ReactionsRepositoryImpl(ncApi, userProvider)
    }

    @Provides
    fun provideCallRecordingRepository(ncApi: NcApi, userProvider: CurrentUserProviderNew): CallRecordingRepository {
        return CallRecordingRepositoryImpl(ncApi, userProvider)
    }

    @Provides
    fun provideRequestAssistanceRepository(
        ncApi: NcApi,
        userProvider: CurrentUserProviderNew
    ): RequestAssistanceRepository {
        return RequestAssistanceRepositoryImpl(ncApi, userProvider)
    }

    @Provides
    fun provideOpenConversationsRepository(
        ncApi: NcApi,
        userProvider: CurrentUserProviderNew
    ): OpenConversationsRepository {
        return OpenConversationsRepositoryImpl(ncApi, userProvider)
    }

    @Provides
    fun translateRepository(ncApi: NcApi): TranslateRepository {
        return TranslateRepositoryImpl(ncApi)
    }

    @Provides
    fun provideConversationsListRepository(ncApi: NcApi): ConversationsListRepository {
        return ConversationsListRepositoryImpl(ncApi)
    }

    @Provides
    fun provideChatRepository(ncApi: NcApi): ChatRepository {
        return NetworkChatRepositoryImpl(ncApi)
    }

    @Provides
    fun provideConversationInfoEditRepository(
        ncApi: NcApi,
        userProvider: CurrentUserProviderNew
    ): ConversationInfoEditRepository {
        return ConversationInfoEditRepositoryImpl(ncApi, userProvider)
    }

    @Provides
    fun provideConversationRepository(ncApi: NcApi, userProvider: CurrentUserProviderNew): ConversationRepository {
        return ConversationRepositoryImpl(ncApi, userProvider)
    }

    @Provides
    fun provideInvitationsRepository(ncApi: NcApi): InvitationsRepository {
        return InvitationsRepositoryImpl(ncApi)
    }
}
