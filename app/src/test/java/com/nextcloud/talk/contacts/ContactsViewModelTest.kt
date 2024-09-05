/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import com.nextcloud.talk.contacts.apiService.FakeItem
import com.nextcloud.talk.contacts.repository.FakeRepositoryError
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
    fun setUp() {
        viewModel = ContactsViewModel(repository)
    }

    @Test
    fun `fetch contacts`() =
        runTest {
            viewModel = ContactsViewModel(repository)
            viewModel.getContactsFromSearchParams()
            assert(viewModel.contactsViewState.value is ContactsUiState.Success)
            val successState = viewModel.contactsViewState.value as ContactsUiState.Success
            assert(successState.contacts == FakeItem.contacts)
        }

    @Test
    fun `test error contacts state`() =
        runTest {
            viewModel = ContactsViewModel(FakeRepositoryError())
            assert(viewModel.contactsViewState.value is ContactsUiState.Error)
            val errorState = viewModel.contactsViewState.value as ContactsUiState.Error
            assert(errorState.message == "unable to fetch contacts")
        }

    @Test
    fun `update search query`() {
        viewModel.updateSearchQuery("Ma")
        assert(viewModel.searchQuery.value == "Ma")
    }

    @Test
    fun `initial search query is empty string`() {
        viewModel.updateSearchQuery("")
        assert(viewModel.searchQuery.value == "")
    }

    @Test
    fun `initial shareType is User`() {
        assert(viewModel.shareTypeList.contains(ShareType.User.shareType))
    }

    @Test
    fun `update shareTypes`() {
        viewModel.updateShareTypes(listOf(ShareType.Group.shareType))
        assert(viewModel.shareTypeList.contains(ShareType.Group.shareType))
    }

    @Test
    fun `initial room state is none`() =
        runTest {
            assert(viewModel.roomViewState.value is RoomUiState.None)
        }

    @Test
    fun `test success room state`() =
        runTest {
            viewModel.createRoom("1", "users", "s@gmail.com", null)
            assert(viewModel.roomViewState.value is RoomUiState.Success)
            val successState = viewModel.roomViewState.value as RoomUiState.Success
            assert(successState.conversation == FakeItem.roomOverall.ocs!!.data)
        }

    @Test
    fun `test failure room state`() =
        runTest {
            viewModel = ContactsViewModel(FakeRepositoryError())
            viewModel.createRoom("1", "users", "s@gmail.com", null)
            assert(viewModel.roomViewState.value is RoomUiState.Error)
            val errorState = viewModel.roomViewState.value as RoomUiState.Error
            assert(errorState.message == "unable to create room")
        }

    @Test
    fun `test image uri`() {
        val expectedImageUri = "https://mydomain.com/index.php/avatar/vidya/512"
        val imageUri = viewModel.getImageUri("vidya", false)
        assert(imageUri == expectedImageUri)
    }

    @Test
    fun `test error image uri`() {
        val expectedImageUri = "https://mydoman.com/index.php/avatar/vidya/512"
        val imageUri = viewModel.getImageUri("vidya", false)
        assert(imageUri != expectedImageUri)
    }
}
