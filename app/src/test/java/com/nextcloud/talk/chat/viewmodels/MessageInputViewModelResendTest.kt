/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.viewmodels

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.nextcloud.talk.chat.data.ChatMessageRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Messages whose send failed, e.g. while the server couldn't be reached, are handed to SendMessageWorker again, so they
 * are sent like the messages queued while the device was offline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class MessageInputViewModelResendTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository: ChatMessageRepository = mock()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun `every failed message gets send work again`() {
        wheneverBlocking { repository.markFailedMessagesForResend() }.thenReturn(listOf("ref-a", "ref-b"))
        val viewModel = MessageInputViewModel().apply { setData(repository) }

        viewModel.resendFailedMessages(ACCOUNT_ID, ROOM_TOKEN)
        shadowOf(Looper.getMainLooper()).idle()

        val sendWork = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("send_message_queue_$ACCOUNT_ID@$ROOM_TOKEN").get()
        assertEquals(2, sendWork.size)
    }

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val ROOM_TOKEN = "room1"
    }
}
