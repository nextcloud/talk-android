/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.account.viewmodels

import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.account.data.LoginRepository
import com.nextcloud.talk.account.data.PendingBrowserLoginStore
import com.nextcloud.talk.account.data.model.LoginCompletion
import com.nextcloud.talk.account.data.model.LoginResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
import org.mockito.kotlin.wheneverBlocking

@Suppress("TooManyFunctions")
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
        wheneverBlocking { newRepository.pollLogin(any(), any(), any()) }.thenReturn(null)
        val newViewModel = BrowserLoginActivityViewModel(newRepository)

        newViewModel.resumeWebBrowserLogin()

        verify(newRepository).resumeLoginFlow(true, ACCOUNT)
        verifyBlocking(newRepository) { pollLogin(eq(loginResponse), any(), any()) }
        verifyBlocking(newRepository, never()) { startLoginFlow(any(), any(), anyOrNull()) }
    }

    @Test
    fun `resuming twice polls once`() {
        startAndLoseActivity()
        val newRepository: LoginRepository = mock()
        wheneverBlocking { newRepository.pollLogin(any(), any(), any()) }.thenReturn(null)
        val newViewModel = BrowserLoginActivityViewModel(newRepository)

        assertTrue(newViewModel.resumeWebBrowserLogin())
        assertTrue(newViewModel.resumeWebBrowserLogin())

        verifyBlocking(newRepository, times(1)) { pollLogin(any(), any(), any()) }
    }

    @Test
    fun `the pending login is gone after the poll ended`() {
        startAndLoseActivity()
        wheneverBlocking { repository.pollLogin(any(), any(), any()) }.thenReturn(null)

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

        assertFalse(newViewModel.resumeWebBrowserLogin())

        assertNull(PendingBrowserLoginStore.active())
        verifyBlocking(newRepository, never()) { pollLogin(any(), any(), any()) }
    }

    @Test
    fun `without a saved login the login can be started again`() {
        wheneverBlocking { repository.startLoginFlow(any(), any(), anyOrNull()) }.thenReturn(loginResponse)

        assertFalse(viewModel.resumeWebBrowserLogin())
        viewModel.startWebBrowserLogin(BASE_URL, reAuth = true, accountToReauthorize = ACCOUNT)

        verifyBlocking(repository) { startLoginFlow(BASE_URL, true, ACCOUNT) }
        assertEquals(BASE_URL, PendingBrowserLoginStore.active()!!.baseUrl)
    }

    private val confirmed = LoginCompletion(HTTP_OK, BASE_URL, "user", "app-password")

    private fun repositoryAcceptingLogin(): LoginRepository {
        val newRepository: LoginRepository = mock()
        wheneverBlocking { newRepository.parseAndLogin(any()) }.thenReturn(LoginRepository.LoginResult.ExistingAccount)
        return newRepository
    }

    @Test
    fun `a login confirmed while its view model is canceled is finished by the next view model`() {
        startAndLoseActivity()
        val first = viewModel
        // The blocking request returns the login while the screen is already destroyed: withContext hands over the
        // login to onConfirmed, then throws the cancellation.
        wheneverBlocking { repository.pollLogin(any(), any(), any()) }.doSuspendableAnswer { invocation ->
            invocation.getArgument<(LoginCompletion) -> Unit>(1)(confirmed)
            first.viewModelScope.cancel()
            throw CancellationException("view model cleared")
        }
        first.handleWebBrowserLogin()
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.None, first.postLoginState.value)

        val newRepository = repositoryAcceptingLogin()
        val second = BrowserLoginActivityViewModel(newRepository)
        assertTrue(second.resumeWebBrowserLogin())

        verifyBlocking(newRepository, never()) { pollLogin(any(), any(), any()) }
        verifyBlocking(newRepository) { parseAndLogin(confirmed) }
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.PostLoginRestartApp, second.postLoginState.value)
    }

    @Test
    fun `a confirmed login that no screen showed is finished by the next view model`() {
        startAndLoseActivity()
        wheneverBlocking { repository.pollLogin(any(), any(), any()) }.doSuspendableAnswer { invocation ->
            invocation.getArgument<(LoginCompletion) -> Unit>(1)(confirmed)
            confirmed
        }
        wheneverBlocking { repository.parseAndLogin(any()) }.thenReturn(LoginRepository.LoginResult.ExistingAccount)
        viewModel.handleWebBrowserLogin()
        assertEquals(
            BrowserLoginActivityViewModel.PostLoginViewState.PostLoginRestartApp,
            viewModel.postLoginState.value
        )

        // The screen was destroyed before it showed the result.
        val newRepository = repositoryAcceptingLogin()
        val second = BrowserLoginActivityViewModel(newRepository)
        assertTrue(second.resumeWebBrowserLogin())

        verifyBlocking(newRepository, never()) { pollLogin(any(), any(), any()) }
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.PostLoginRestartApp, second.postLoginState.value)

        // Once the screen showed it, the login is over.
        second.onPostLoginShown(second.postLoginState.value)
        assertNull(PendingBrowserLoginStore.active())
    }

    @Test
    fun `a confirmed login outlives the lifetime of the login flow`() {
        var now = 1_000L
        PendingBrowserLoginStore.clock = { now }
        startAndLoseActivity()
        PendingBrowserLoginStore.complete(loginResponse, confirmed)

        // The screen is destroyed before it shows the result, and the app is opened again after the 20 minutes.
        now += PendingBrowserLoginStore.LIFETIME_MILLIS
        val newRepository = repositoryAcceptingLogin()
        val second = BrowserLoginActivityViewModel(newRepository)

        assertTrue(second.resumeWebBrowserLogin())
        verifyBlocking(newRepository) { parseAndLogin(confirmed) }
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.PostLoginRestartApp, second.postLoginState.value)
    }

    @Test
    fun `a waiting poll uses the login that a poll still in flight confirmed`() {
        startAndLoseActivity()
        val stillNotFound = CompletableDeferred<Unit>()
        val newRepository = repositoryAcceptingLogin()
        wheneverBlocking { newRepository.pollLogin(any(), any(), any()) }.doSuspendableAnswer { invocation ->
            val keepPolling = invocation.getArgument<() -> Boolean>(2)
            stillNotFound.await()
            if (keepPolling()) error("The poll must stop, the login is confirmed")
            null
        }
        val second = BrowserLoginActivityViewModel(newRepository)
        second.resumeWebBrowserLogin()

        // The request of the old view model returns the login now.
        PendingBrowserLoginStore.complete(loginResponse, confirmed)
        stillNotFound.complete(Unit)

        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.PostLoginRestartApp, second.postLoginState.value)
    }

    @Test
    fun `a screen showing its result does not end a newer login`() {
        startAndLoseActivity()
        wheneverBlocking { repository.pollLogin(any(), any(), any()) }.thenReturn(null)
        viewModel.handleWebBrowserLogin()
        val newer = LoginResponse(token = "newer", pollUrl = "https://example.com/poll2", loginUrl = URL)
        PendingBrowserLoginStore.save(newer, BASE_URL, false, null)

        viewModel.onPostLoginShown(viewModel.postLoginState.value)

        assertEquals(newer, PendingBrowserLoginStore.active()?.response)
    }

    @Test
    fun `a login confirmed after cancel is not kept`() {
        startAndLoseActivity()
        wheneverBlocking { repository.pollLogin(any(), any(), any()) }.doSuspendableAnswer { invocation ->
            viewModel.cancelLogin()
            invocation.getArgument<(LoginCompletion) -> Unit>(1)(confirmed)
            // The real poll returns the login it got, although the login was canceled meanwhile.
            confirmed
        }
        wheneverBlocking { repository.parseAndLogin(any()) }.thenReturn(LoginRepository.LoginResult.ExistingAccount)
        viewModel.handleWebBrowserLogin()

        verifyBlocking(repository, never()) { parseAndLogin(any()) }
        assertEquals(BrowserLoginActivityViewModel.PostLoginViewState.None, viewModel.postLoginState.value)
        assertNull(PendingBrowserLoginStore.active())
        assertFalse(BrowserLoginActivityViewModel(repositoryAcceptingLogin()).resumeWebBrowserLogin())
    }

    @Test
    fun `cancel erases a confirmed login that no screen showed`() {
        startAndLoseActivity()
        wheneverBlocking { repository.pollLogin(any(), any(), any()) }.doSuspendableAnswer { invocation ->
            invocation.getArgument<(LoginCompletion) -> Unit>(1)(confirmed)
            confirmed
        }
        wheneverBlocking { repository.parseAndLogin(any()) }.thenReturn(LoginRepository.LoginResult.ExistingAccount)
        viewModel.handleWebBrowserLogin()

        viewModel.cancelLogin()

        assertNull(PendingBrowserLoginStore.active())
        assertFalse(BrowserLoginActivityViewModel(repositoryAcceptingLogin()).resumeWebBrowserLogin())
    }

    @Test
    fun `a login refused because of too many failed logins is reported as such`() {
        wheneverBlocking { repository.startOTPLoginFlow(any(), any(), anyOrNull()) }
            .thenReturn(LoginCompletion(LoginRepository.HTTP_TOO_MANY_REQUESTS, BASE_URL, "user", ""))

        viewModel.loginWithOTPQR("qr")

        assertEquals(
            BrowserLoginActivityViewModel.PostLoginViewState.PostLoginTooManyLoginAttempts,
            viewModel.postLoginState.value
        )
        verifyBlocking(repository, never()) { parseAndLogin(any()) }
    }

    companion object {
        private const val ACCOUNT = 7L
        private const val HTTP_OK = 200
        private const val BASE_URL = "https://example.com"
        private const val URL = "https://example.com/login"
    }
}
