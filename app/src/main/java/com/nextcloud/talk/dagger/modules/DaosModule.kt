/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.dagger.modules

import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.source.local.TalkDatabase
import dagger.Module
import dagger.Provides

@Module
internal object DaosModule {
    @Provides
    fun providesConversationsDao(database: TalkDatabase): ConversationsDao = database.conversationsDao()

    @Provides
    fun providesChatDao(database: TalkDatabase): ChatMessagesDao = database.chatMessagesDao()

    @Provides
    fun providesChatBlocksDao(database: TalkDatabase): ChatBlocksDao = database.chatBlocksDao()
}
