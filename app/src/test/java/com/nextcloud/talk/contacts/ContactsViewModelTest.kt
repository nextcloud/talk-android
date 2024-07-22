/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import com.nextcloud.talk.contacts.apiService.FakeItem
import com.nextcloud.talk.contacts.repository.FakeRepositorySuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {
    private lateinit var viewModel: ContactsViewModel
    private val repository: ContactsRepository = FakeRepositorySuccess()

    val dispatcher: TestDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Before
    fun setUp()  {
        viewModel = ContactsViewModel(repository)
    }

    @Test
    fun `fetch contacts`() =
        runTest {
            viewModel.getContactsFromSearchParams()
            assert(viewModel.contactsViewState.value is ContactsUiState.Success)
            val successState = viewModel.contactsViewState.value as ContactsUiState.Success
            assert(successState.contacts == FakeItem.contacts)
        }
}
