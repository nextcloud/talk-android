/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chooseaccount.viewmodel

import com.nextcloud.talk.chooseaccount.data.StatusRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.status.StatusDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class StatusMessageViewModelTest {

    private lateinit var viewModel: StatusMessageViewModel

    private val status = StatusDto().apply { message = "In a meeting" }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val user = User(id = 1, username = "user", token = "token", baseUrl = "https://example.com")
        viewModel = StatusMessageViewModel(mock<StatusRepository>(), user)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a recreated sheet keeps the unsaved edits`() {
        viewModel.initSheetIfNeeded(status)
        viewModel.updateMessage("Edited")

        viewModel.initSheetIfNeeded(status)

        assertEquals("Edited", viewModel.message.value)
    }

    @Test
    fun `a sheet opened after closing starts from the current status`() {
        viewModel.initSheetIfNeeded(status)
        viewModel.updateMessage("Edited")
        viewModel.onSheetClosed()

        viewModel.initSheetIfNeeded(status)

        assertEquals("In a meeting", viewModel.message.value)
    }
}
