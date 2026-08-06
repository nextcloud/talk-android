/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import androidx.lifecycle.ViewModelStore
import com.nextcloud.talk.contacts.apiService.FakeItem
import com.nextcloud.talk.contacts.repository.FakeRepositoryError
import com.nextcloud.talk.contacts.repository.FakeRepositorySuccess
import com.nextcloud.talk.data.user.UsersDao
import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.data.user.UsersRepositoryImpl
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.database.user.CurrentUserProvider
import com.nextcloud.talk.utils.database.user.CurrentUserProviderImpl
import com.nextcloud.talk.utils.preview.DummyUserDaoImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
    private val viewModelStore = ViewModelStore()

    val dispatcher: TestDispatcher = UnconfinedTestDispatcher()

    val usersDao: UsersDao
        get() = DummyUserDaoImpl()

    val userRepository: UsersRepository
        get() = UsersRepositoryImpl(usersDao)

    val userManager: UserManager
        get() = UserManager(userRepository)

    val userProvider: CurrentUserProvider
        get() = CurrentUserProviderImpl(userManager)

    private var viewModelCount = 0

    private fun createViewModel(repo: ContactsRepository): ContactsViewModel {
        val provider = userProvider
        // CurrentUserProviderImpl feeds its currentUserFlow from a real Dispatchers.IO
        // scope, independent of the test dispatcher's virtual time. Waiting for it here
        // (real blocking wait, since this isn't inside runTest) ensures the value is
        // already cached by the time the ViewModel's viewModelScope collects it.
        runBlocking { provider.getCurrentUser() }
        return ContactsViewModel(repo, provider).also {
            viewModelStore.put("contactsViewModel${viewModelCount++}", it)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = createViewModel(repository)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `fetch contacts`() =
        runTest {
            viewModel = createViewModel(repository)
            viewModel.getContactsFromSearchParams()
            assert(viewModel.contactsViewState.value is ContactsViewModel.ContactsUiState.Success)
            val successState = viewModel.contactsViewState.value as ContactsViewModel.ContactsUiState.Success
            assert(successState.contacts == FakeItem.contacts)
        }

    @Test
    fun `test error contacts state`() =
        runTest {
            viewModel = createViewModel(FakeRepositoryError())
            viewModel.getContactsFromSearchParams()
            assert(viewModel.contactsViewState.value is ContactsViewModel.ContactsUiState.Error)
            val errorState = viewModel.contactsViewState.value as ContactsViewModel.ContactsUiState.Error
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
            assert(viewModel.roomViewState.value is ContactsViewModel.RoomUiState.None)
        }

    @Test
    fun `test success room state`() =
        runTest {
            viewModel.createRoom("1", "users", "s@gmail.com", null)
            assert(viewModel.roomViewState.value is ContactsViewModel.RoomUiState.Success)
            val successState = viewModel.roomViewState.value as ContactsViewModel.RoomUiState.Success
            assert(successState.conversation == FakeItem.roomOverall.ocs!!.data)
        }

    @Test
    fun `test failure room state`() =
        runTest {
            viewModel = createViewModel(FakeRepositoryError())
            viewModel.createRoom("1", "users", "s@gmail.com", null)
            assert(viewModel.roomViewState.value is ContactsViewModel.RoomUiState.Error)
            val errorState = viewModel.roomViewState.value as ContactsViewModel.RoomUiState.Error
            assert(errorState.message == "unable to create room")
        }

    @Test
    fun `test image uri`() {
        val expectedImageUri = "https://mydomain.com/index.php/avatar/vidya/512"
        val imageUri = viewModel.getImageUri("vidya", false, false)
        assert(imageUri == expectedImageUri)
    }

    @Test
    fun `test error image uri`() {
        val expectedImageUri = "https://mydoman.com/index.php/avatar/vidya/512"
        val imageUri = viewModel.getImageUri("vidya", false, false)
        assert(imageUri != expectedImageUri)
    }
}
