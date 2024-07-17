/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.runner.AndroidJUnit4
import com.nextcloud.talk.data.database.model.ChatBlockEntity
import com.nextcloud.talk.data.source.local.TalkDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatBlocksDaoTest {
    private lateinit var chatBlocksDao: ChatBlocksDao
    private lateinit var db: TalkDatabase
    private val tag = ChatBlocksDaoTest::class.java.simpleName

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            TalkDatabase::class.java
        ).build()
        chatBlocksDao = db.chatBlocksDao()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun testGetConnectedChatBlocks() =
        runTest {

            val searchedChatBlock = ChatBlockEntity(
                internalConversationId = "1",
                oldestMessageId = 50,
                newestMessageId = 60,
                hasHistory = true
            )

            val chatBlockTooOld = ChatBlockEntity(
                internalConversationId = "1",
                oldestMessageId = 10,
                newestMessageId = 20,
                hasHistory = true
            )

            val chatBlockOverlap1 = ChatBlockEntity(
                internalConversationId = "1",
                oldestMessageId = 45,
                newestMessageId = 55,
                hasHistory = true
            )

            val chatBlockWithin = ChatBlockEntity(
                internalConversationId = "1",
                oldestMessageId = 52,
                newestMessageId = 58,
                hasHistory = true
            )

            val chatBlockOverall = ChatBlockEntity(
                internalConversationId = "1",
                oldestMessageId = 1,
                newestMessageId = 99,
                hasHistory = true
            )

            val chatBlockOverlap2 = ChatBlockEntity(
                internalConversationId = "1",
                oldestMessageId = 59,
                newestMessageId = 70,
                hasHistory = true
            )

            val chatBlockTooNew = ChatBlockEntity(
                internalConversationId = "1",
                oldestMessageId = 80,
                newestMessageId = 90,
                hasHistory = true
            )

            val chatBlockWithinButOtherConversation = ChatBlockEntity(
                internalConversationId = "2",
                oldestMessageId = 53,
                newestMessageId = 57,
                hasHistory = true
            )

            chatBlocksDao.upsertChatBlock(searchedChatBlock)

            chatBlocksDao.upsertChatBlock(chatBlockTooOld)
            chatBlocksDao.upsertChatBlock(chatBlockOverlap1)
            chatBlocksDao.upsertChatBlock(chatBlockWithin)
            chatBlocksDao.upsertChatBlock(chatBlockOverall)
            chatBlocksDao.upsertChatBlock(chatBlockOverlap2)
            chatBlocksDao.upsertChatBlock(chatBlockTooNew)
            chatBlocksDao.upsertChatBlock(chatBlockWithinButOtherConversation)

            val results = chatBlocksDao.getConnectedChatBlocks(
                "1",
                searchedChatBlock.oldestMessageId,
                searchedChatBlock.newestMessageId
            )

            assertEquals(5, results.first().size)
        }
}
