/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.account.viewmodels

import com.nextcloud.talk.account.data.LoginRepository
import com.nextcloud.talk.account.data.model.LoginCompletion
import com.nextcloud.talk.account.data.model.LoginResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserLoginActivityViewModelTest {

    private val repository: LoginRepository = mock()
    private lateinit var viewModel: BrowserLoginActivityViewModel

    private val loginResponse = LoginResponse(token = "token", pollUrl = "https://example.com/poll", loginUrl = URL)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = BrowserLoginActivityViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starting the login again continues the started login`() {
        whenever { repository.startLoginFlow(any(), any(), anyOrNull()) }.thenReturn(loginResponse)

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
        whenever { repository.startOTPLoginFlow(any(), any(), anyOrNull()) }.thenReturn(null)

        viewModel.loginWithOTPQR("qr")
        viewModel.loginWithOTPQR("qr")

        verifyBlocking(repository, times(1)) { startOTPLoginFlow(any(), any(), anyOrNull()) }
    }

    @Test
    fun `browser login starts polling without waiting for an activity restart`() {
        whenever { repository.startLoginFlow(any(), any(), anyOrNull()) }.thenReturn(loginResponse)
        whenever { repository.pollLogin(loginResponse) }.thenReturn(null)

        viewModel.startWebBrowserLogin(BASE_URL)

        verifyBlocking(repository, times(1)) { pollLogin(loginResponse) }
    }

    @Test
    fun `returning from the browser repeatedly does not consume credentials twice`() {
        val completion = LoginCompletion(200, BASE_URL, "user", "app-password")
        whenever { repository.startLoginFlow(any(), any(), anyOrNull()) }.thenReturn(loginResponse)
        whenever { repository.pollLogin(loginResponse) }.thenReturn(completion)
        whenever { repository.parseAndLogin(completion) }.thenReturn(LoginRepository.LoginResult.ExistingAccount)

        viewModel.startWebBrowserLogin(BASE_URL)
        repeat(3) { viewModel.handleWebBrowserLogin() }

        verifyBlocking(repository, times(1)) { pollLogin(loginResponse) }
        verifyBlocking(repository, times(1)) { parseAndLogin(completion) }
        assertEquals(
            BrowserLoginActivityViewModel.PostLoginViewState.PostLoginRestartApp,
            viewModel.postLoginState.value
        )
    }

    @Test
    fun `a login refused because of too many failed logins is reported as such`() {
        whenever { repository.startOTPLoginFlow(any(), any(), anyOrNull()) }
            .thenReturn(LoginCompletion(LoginRepository.HTTP_TOO_MANY_REQUESTS, BASE_URL, "user", ""))

        viewModel.loginWithOTPQR("qr")

        assertEquals(
            BrowserLoginActivityViewModel.PostLoginViewState.PostLoginTooManyLoginAttempts,
            viewModel.postLoginState.value
        )
        verifyBlocking(repository, never()) { parseAndLogin(any()) }
    }

    @Test
    fun `the browser launch is not replayed when the activity resumes`() {
        whenever { repository.startLoginFlow(any(), any(), anyOrNull()) }.thenReturn(loginResponse)

        viewModel.startWebBrowserLogin(BASE_URL)
        viewModel.onBrowserOpened()
        viewModel.startWebBrowserLogin(BASE_URL)

        assertEquals(
            BrowserLoginActivityViewModel.InitialLoginViewState.None,
            viewModel.initialLoginRequestState.value
        )
        verifyBlocking(repository, times(1)) { startLoginFlow(any(), any(), anyOrNull()) }
    }

    @Test
    fun `cancelled polling cannot complete the login`() {
        val result = CompletableDeferred<LoginCompletion?>()
        whenever { repository.startLoginFlow(any(), any(), anyOrNull()) }.thenReturn(loginResponse)
        whenever { repository.pollLogin(loginResponse) }.doSuspendableAnswer { result.await() }

        viewModel.startWebBrowserLogin(BASE_URL)
        verifyBlocking(repository, times(1)) { pollLogin(loginResponse) }
        viewModel.cancelLogin()
        result.complete(LoginCompletion(200, BASE_URL, "user", "app-password"))

        verifyBlocking(repository, never()) { parseAndLogin(any()) }
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.None, viewModel.postLoginState.value)
    }

    @Test
    fun `returning after a failed poll retries the same login session`() {
        val completion = LoginCompletion(200, BASE_URL, "user", "app-password")
        whenever { repository.startLoginFlow(any(), any(), anyOrNull()) }.thenReturn(loginResponse)
        whenever { repository.pollLogin(loginResponse) }.thenReturn(null, completion)
        whenever { repository.parseAndLogin(completion) }.thenReturn(LoginRepository.LoginResult.ExistingAccount)

        viewModel.startWebBrowserLogin(BASE_URL)
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.PostLoginError, viewModel.postLoginState.value)
        viewModel.onBrowserOpened()
        viewModel.handleWebBrowserLogin()

        verifyBlocking(repository, times(1)) { startLoginFlow(any(), any(), anyOrNull()) }
        verifyBlocking(repository, times(2)) { pollLogin(loginResponse) }
        assertEquals(
            BrowserLoginActivityViewModel.PostLoginViewState.PostLoginRestartApp,
            viewModel.postLoginState.value
        )
        assertEquals(
            BrowserLoginActivityViewModel.InitialLoginViewState.None,
            viewModel.initialLoginRequestState.value
        )
    }

    @Test
    fun `pending login stops polling after twenty minutes`() =
        runTest {
            whenever { repository.startLoginFlow(any(), any(), anyOrNull()) }.thenReturn(loginResponse)
            whenever { repository.pollLogin(loginResponse) }.doSuspendableAnswer { awaitCancellation() }

            viewModel.startWebBrowserLogin(BASE_URL)
            repeat(3) { viewModel.handleWebBrowserLogin() }
            verifyBlocking(repository, times(1)) { pollLogin(loginResponse) }
            advanceTimeBy(20 * 60 * 1000L)
            runCurrent()

            assertEquals(
                BrowserLoginActivityViewModel.PostLoginViewState.PostLoginError,
                viewModel.postLoginState.value
            )
            verifyBlocking(repository, never()) { parseAndLogin(any()) }
        }

    @Test
    fun `cancelling an initial request does not open the browser later`() {
        val result = CompletableDeferred<LoginResponse?>()
        whenever { repository.startLoginFlow(any(), any(), anyOrNull()) }.doSuspendableAnswer { result.await() }

        viewModel.startWebBrowserLogin(BASE_URL)
        viewModel.cancelLogin()
        result.complete(loginResponse)

        assertEquals(
            BrowserLoginActivityViewModel.InitialLoginViewState.None,
            viewModel.initialLoginRequestState.value
        )
        verifyBlocking(repository, never()) { pollLogin(any()) }
    }

    companion object {
        private const val BASE_URL = "https://example.com"
        private const val URL = "https://example.com/login"
    }
}
