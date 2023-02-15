/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.dagger.modules

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.storage.ArbitraryStoragesRepository
import com.nextcloud.talk.data.storage.ArbitraryStoragesRepositoryImpl
import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.data.user.UsersRepositoryImpl
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
    fun provideRemoteFileBrowserItemsRepository(okHttpClient: OkHttpClient, userProvider: CurrentUserProviderNew):
        RemoteFileBrowserItemsRepository {
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
    fun provideRequestAssistanceRepository(ncApi: NcApi, userProvider: CurrentUserProviderNew):
        RequestAssistanceRepository {
        return RequestAssistanceRepositoryImpl(ncApi, userProvider)
    }
}
