/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.account.viewmodels

import com.nextcloud.talk.account.data.LoginRepository
import com.nextcloud.talk.account.data.PendingBrowserLoginStore
import com.nextcloud.talk.account.data.io.LocalLoginDataSource
import com.nextcloud.talk.account.data.model.LoginCompletion
import com.nextcloud.talk.account.data.model.LoginResponse
import com.nextcloud.talk.account.data.network.NetworkLoginDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import java.io.IOException

@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserLoginActivityViewModelTest {

    private val repository: LoginRepository = mock()
    private lateinit var viewModel: BrowserLoginActivityViewModel

    private val scheduler = TestCoroutineScheduler()
    private val network: NetworkLoginDataSource = mock()
    private val local: LocalLoginDataSource = mock()

    private val loginResponse = LoginResponse(token = "token", pollUrl = "https://example.com/poll", loginUrl = URL)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
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
        wheneverBlocking { newRepository.pollLogin(any(), any()) }.thenReturn(null)
        val newViewModel = BrowserLoginActivityViewModel(newRepository)

        newViewModel.resumeWebBrowserLogin()

        verify(newRepository).resumeLoginFlow(true, ACCOUNT)
        verifyBlocking(newRepository) { pollLogin(eq(loginResponse), any()) }
        verifyBlocking(newRepository, never()) { startLoginFlow(any(), any(), anyOrNull()) }
    }

    @Test
    fun `resuming twice polls once`() {
        startAndLoseActivity()
        val newRepository: LoginRepository = mock()
        wheneverBlocking { newRepository.pollLogin(any(), any()) }.thenReturn(null)
        val newViewModel = BrowserLoginActivityViewModel(newRepository)

        newViewModel.resumeWebBrowserLogin()
        newViewModel.resumeWebBrowserLogin()

        verifyBlocking(newRepository, times(1)) { pollLogin(any(), any()) }
    }

    @Test
    fun `the pending login is gone after the poll ended`() {
        startAndLoseActivity()
        wheneverBlocking { repository.pollLogin(any(), any()) }.thenReturn(null)

        viewModel.handleWebBrowserLogin()

        assertNull(PendingBrowserLoginStore.active())
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.PostLoginError, viewModel.postLoginState.value)
    }

    @Test
    fun `a login request answered after cancel is not saved`() {
        val answer = CompletableDeferred<LoginResponse?>()
        wheneverBlocking { repository.startLoginFlow(any(), any(), anyOrNull()) }.doSuspendableAnswer { answer.await() }
        viewModel.startWebBrowserLogin(BASE_URL)

        viewModel.cancelLogin()
        answer.complete(loginResponse)

        assertNull(PendingBrowserLoginStore.active())
        assertEquals(BrowserLoginActivityViewModel.InitialLoginViewState.None, viewModel.initialLoginRequestState.value)
    }

    @Test
    fun `cancel ends the pending login, also when the poll answers after it`() {
        startAndLoseActivity()
        val answer = CompletableDeferred<LoginCompletion?>()
        wheneverBlocking { repository.pollLogin(any(), any()) }.doSuspendableAnswer { answer.await() }
        viewModel.handleWebBrowserLogin()

        viewModel.cancelLogin()
        answer.complete(LoginCompletion(HTTP_OK, BASE_URL, "user", "app-password"))

        verify(repository).cancelLoginFlow()
        verifyBlocking(repository, never()) { parseAndLogin(any()) }
        assertNull(PendingBrowserLoginStore.active())
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.None, viewModel.postLoginState.value)
    }

    @Test
    fun `a poll that ended for good ends the pending login with an error`() {
        startAndLoseActivity()
        wheneverBlocking { repository.pollLogin(any(), any()) }.thenThrow(IllegalArgumentException("bad poll url"))

        viewModel.handleWebBrowserLogin()

        assertNull(PendingBrowserLoginStore.active())
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.PostLoginError, viewModel.postLoginState.value)
    }

    @Test
    fun `a canceled poll keeps the login pending`() {
        startAndLoseActivity()
        wheneverBlocking { repository.pollLogin(any(), any()) }.thenThrow(CancellationException("view model cleared"))

        viewModel.handleWebBrowserLogin()

        assertNotNull(PendingBrowserLoginStore.active())
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.None, viewModel.postLoginState.value)
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
        verifyBlocking(newRepository, never()) { pollLogin(any(), any()) }
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.PostLoginError, newViewModel.postLoginState.value)
    }

    // A real repository on virtual time: the poll retries and pauses as in the app.
    private fun startPollingWithRealRepository(): LoginCompletion {
        startAndLoseActivity()
        val completion = LoginCompletion(HTTP_OK, BASE_URL, "user", "app-password")
        val realRepository = LoginRepository(network, local, StandardTestDispatcher(scheduler))
        viewModel = BrowserLoginActivityViewModel(realRepository)
        wheneverBlocking { local.checkIfUserIsScheduledForDeletion(any()) }.thenReturn(false)
        wheneverBlocking { local.checkIfUserExists(any()) }.thenReturn(true)
        wheneverBlocking { local.updateUser(any(), anyOrNull()) }.thenReturn(true)
        return completion
    }

    @Test
    fun `a network failure during the poll is retried until the login completes`() {
        val completion = startPollingWithRealRepository()
        whenever(network.performLoginFlowV2(any()))
            .thenThrow(IOException("no network"))
            .thenThrow(IOException("no network"))
            .thenReturn(completion)

        viewModel.resumeWebBrowserLogin()
        scheduler.runCurrent()

        // Still waiting after the first failure: the login is pending and has no error.
        assertNotNull(PendingBrowserLoginStore.active())
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.None, viewModel.postLoginState.value)

        scheduler.advanceUntilIdle()

        assertNull(PendingBrowserLoginStore.active())
        assertEquals(
            BrowserLoginActivityViewModel.PostLoginViewState.PostLoginRestartApp,
            viewModel.postLoginState.value
        )
        verify(network, times(3)).performLoginFlowV2(any())
    }

    @Test
    fun `a canceled login stops polling during the retries`() {
        startPollingWithRealRepository()
        whenever(network.performLoginFlowV2(any())).thenThrow(IOException("no network"))
        viewModel.resumeWebBrowserLogin()
        scheduler.runCurrent()

        viewModel.cancelLogin()
        scheduler.advanceUntilIdle()

        verify(network, times(1)).performLoginFlowV2(any())
        verifyBlocking(local, never()) { checkIfUserExists(any()) }
        assertNull(PendingBrowserLoginStore.active())
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.None, viewModel.postLoginState.value)
    }

    @Test
    fun `a login that expires during the retries ends with an error`() {
        var now = 1_000L
        PendingBrowserLoginStore.clock = { now }
        startPollingWithRealRepository()
        whenever(network.performLoginFlowV2(any())).thenAnswer {
            now += PendingBrowserLoginStore.LIFETIME_MILLIS
            throw IOException("no network")
        }

        viewModel.resumeWebBrowserLogin()
        scheduler.advanceUntilIdle()

        verify(network, times(1)).performLoginFlowV2(any())
        assertNull(PendingBrowserLoginStore.active())
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.PostLoginError, viewModel.postLoginState.value)
    }

    @Test
    fun `an unusable poll answer ends the login with an error without retry`() {
        startPollingWithRealRepository()
        whenever(network.performLoginFlowV2(any())).thenReturn(null)

        viewModel.resumeWebBrowserLogin()
        scheduler.advanceUntilIdle()

        verify(network, times(1)).performLoginFlowV2(any())
        assertNull(PendingBrowserLoginStore.active())
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.PostLoginError, viewModel.postLoginState.value)
    }

    companion object {
        private const val ACCOUNT = 7L
        private const val HTTP_OK = 200
        private const val BASE_URL = "https://example.com"
        private const val URL = "https://example.com/login"
    }
}
