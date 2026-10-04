/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.account.viewmodels

import com.nextcloud.talk.account.data.LoginRepository
import com.nextcloud.talk.account.data.PendingBrowserLoginStore
import com.nextcloud.talk.account.data.model.LoginResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserLoginActivityViewModelTest {

    private val repository: LoginRepository = mock()
    private lateinit var viewModel: BrowserLoginActivityViewModel

    private val loginResponse = LoginResponse(token = "token", pollUrl = "https://example.com/poll", loginUrl = URL)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        PendingBrowserLoginStore.clear()
        viewModel = BrowserLoginActivityViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        PendingBrowserLoginStore.clock = System::currentTimeMillis
        PendingBrowserLoginStore.clear()
    }

    @Test
    fun `starting the login again continues the started login`() {
        wheneverBlocking { repository.startLoginFlow(any(), any(), anyOrNull()) }.thenReturn(loginResponse)

        viewModel.startWebBrowserLogin(BASE_URL)
        viewModel.startWebBrowserLogin(BASE_URL)

        verifyBlocking(repository, times(1)) { startLoginFlow(any(), any(), anyOrNull()) }
        assertEquals(
            BrowserLoginActivityViewModel.InitialLoginViewState.InitialLoginRequestSuccess(URL),
            viewModel.initialLoginRequestState.value
        )
    }

    @Test
    fun `a QR code login is not started twice either`() {
        wheneverBlocking { repository.startOTPLoginFlow(any(), any(), anyOrNull()) }.thenReturn(null)

        viewModel.loginWithOTPQR("qr")
        viewModel.loginWithOTPQR("qr")

        verifyBlocking(repository, times(1)) { startOTPLoginFlow(any(), any(), anyOrNull()) }
    }

    private fun startAndLoseActivity() {
        wheneverBlocking { repository.startLoginFlow(any(), any(), anyOrNull()) }.thenReturn(loginResponse)
        viewModel.startWebBrowserLogin(BASE_URL, reAuth = true, accountToReauthorize = ACCOUNT)
    }

    @Test
    fun `a started login stays pending for a new view model`() {
        startAndLoseActivity()

        val pending = PendingBrowserLoginStore.active()
        assertNotNull(pending)
        assertEquals(loginResponse, pending!!.response)
        assertEquals(true, pending.reAuth)
        assertEquals(ACCOUNT, pending.accountToReauthorize)
    }

    @Test
    fun `resuming polls the saved response without a new login request`() {
        startAndLoseActivity()
        val newRepository: LoginRepository = mock()
        wheneverBlocking { newRepository.pollLogin(any()) }.thenReturn(null)
        val newViewModel = BrowserLoginActivityViewModel(newRepository)

        newViewModel.resumeWebBrowserLogin()

        verify(newRepository).resumeLoginFlow(true, ACCOUNT)
        verifyBlocking(newRepository) { pollLogin(loginResponse) }
        verifyBlocking(newRepository, never()) { startLoginFlow(any(), any(), anyOrNull()) }
    }

    @Test
    fun `resuming twice polls once`() {
        startAndLoseActivity()
        val newRepository: LoginRepository = mock()
        wheneverBlocking { newRepository.pollLogin(any()) }.thenReturn(null)
        val newViewModel = BrowserLoginActivityViewModel(newRepository)

        newViewModel.resumeWebBrowserLogin()
        newViewModel.resumeWebBrowserLogin()

        verifyBlocking(newRepository, times(1)) { pollLogin(any()) }
    }

    @Test
    fun `the pending login is gone after the poll ended`() {
        startAndLoseActivity()
        wheneverBlocking { repository.pollLogin(any()) }.thenReturn(null)

        viewModel.handleWebBrowserLogin()

        assertNull(PendingBrowserLoginStore.active())
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.PostLoginError, viewModel.postLoginState.value)
    }

    @Test
    fun `the pending login is gone after cancel`() {
        startAndLoseActivity()

        viewModel.cancelLogin()

        assertNull(PendingBrowserLoginStore.active())
        verify(repository).cancelLoginFlow()
    }

    @Test
    fun `an expired login is not resumed`() {
        var now = 1_000L
        PendingBrowserLoginStore.clock = { now }
        startAndLoseActivity()
        now += PendingBrowserLoginStore.LIFETIME_MILLIS
        val newRepository: LoginRepository = mock()
        val newViewModel = BrowserLoginActivityViewModel(newRepository)

        newViewModel.resumeWebBrowserLogin()

        assertNull(PendingBrowserLoginStore.active())
        verifyBlocking(newRepository, never()) { pollLogin(any()) }
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.PostLoginError, newViewModel.postLoginState.value)
    }

    companion object {
        private const val ACCOUNT = 7L
        private const val BASE_URL = "https://example.com"
        private const val URL = "https://example.com/login"
    }
}
