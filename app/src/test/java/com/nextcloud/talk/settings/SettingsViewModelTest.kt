/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings

import android.content.Context
import android.content.res.Resources
import androidx.lifecycle.ViewModelStore
import androidx.work.WorkManager
import com.nextcloud.talk.R
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.logger.Level
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.logger.LogsRepository
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.userprofile.UserProfileDataDto
import com.nextcloud.talk.settings.data.SettingsRepository
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions")
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val ioDispatcher = StandardTestDispatcher(dispatcher.scheduler)
    private val viewModelStore = ViewModelStore()
    private val appScope = CoroutineScope(SupervisorJob())

    private val user = User(
        id = USER_ID,
        userId = "jane",
        username = "jane",
        baseUrl = "https://cloud.example.com",
        token = "token",
        displayName = "Jane",
        capabilities = capabilities(readStatusPrivate = false)
    )
    private val userFlow = MutableStateFlow<User?>(user)

    private lateinit var appPreferences: AppPreferences
    private lateinit var userManager: UserManager
    private lateinit var workManager: WorkManager
    private lateinit var context: Context
    private val repository = FakeSettingsRepository()
    private val accountRemover = mock<AccountRemover>()
    private var deviceState = DEVICE_STATE

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        appPreferences = mock {
            on { proxyType } doReturn NO_PROXY
            on { theme } doReturn "night_yes"
            on { screenLockTimeout } doReturn "30"
            on { showRegularNotificationWarning } doReturn true
        }
        userManager = mock {
            on { userFlow(USER_ID) } doReturn userFlow
        }
        userManager.stub {
            onBlocking { getUsers() } doReturn listOf(user)
        }
        workManager = mock()
        val resources = mock<Resources> {
            on { getBoolean(R.bool.is_branded_client) } doReturn false
        }
        context = mock {
            on { this.resources } doReturn resources
            on { getString(R.string.nc_no_proxy) } doReturn NO_PROXY
            on { getString(R.string.nc_settings_http_value) } doReturn "3128"
            on { getString(R.string.nc_settings_socks_value) } doReturn "1080"
            on { getString(R.string.nc_screen_lock_timeout_sixty) } doReturn "60"
        }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        appScope.cancel()
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        initialUser: User = user,
        highlightNotificationIssues: Boolean = false
    ): SettingsViewModel {
        val networkMonitor = mock<NetworkMonitor> { on { isOnline } doReturn MutableStateFlow(true) }
        val logsRepository = mock<LogsRepository> { on { minimumLevel } doReturn Level.INFO }
        return SettingsViewModel(
            appPreferences = appPreferences,
            userManager = userManager,
            settingsRepository = repository,
            logsRepository = logsRepository,
            deviceStateReader = mock { on { read() } doAnswer { deviceState } },
            accountRemover = accountRemover,
            workManager = workManager,
            logger = mock<Logger>(),
            networkMonitor = networkMonitor,
            appScope = appScope,
            ioDispatcher = ioDispatcher,
            context = context,
            initialUser = initialUser,
            highlightNotificationIssues = highlightNotificationIssues
        ).also { viewModelStore.put("settings", it) }
    }

    @Test
    fun `initial state reflects the stored preferences and the account`() {
        val state = createViewModel(highlightNotificationIssues = true).uiState.value

        assertEquals("night_yes", state.appearance.theme)
        assertEquals("30", state.privacy.screenLockTimeout)
        assertTrue(state.notifications.showNotificationWarning)
        assertEquals("Jane", state.account.displayName)
        assertTrue(state.privacy.readStatusAvailable)
        assertTrue(state.privacy.readStatusShared)
        assertTrue(state.highlightNotificationIssues)
        assertEquals(ProxyUiState(type = NO_PROXY), state.proxy)
    }

    @Test
    fun `read status change is reverted when the server rejects it`() {
        repository.failPrivacyRequests = true
        val viewModel = createViewModel()

        viewModel.setReadStatusShared(false)

        assertTrue(viewModel.uiState.value.privacy.readStatusShared)
        assertEquals(listOf(false), repository.readStatusRequests)
    }

    @Test
    fun `confirmed read status change survives an unrelated update of the account`() {
        val viewModel = createViewModel()

        viewModel.setReadStatusShared(false)
        userFlow.value = user.copy(displayName = "Jane Doe")

        assertFalse(viewModel.uiState.value.privacy.readStatusShared)
        assertEquals("Jane Doe", viewModel.uiState.value.account.displayName)
    }

    @Test
    fun `read status follows a change on the server`() {
        val viewModel = createViewModel()

        userFlow.value = user.copy(capabilities = capabilities(readStatusPrivate = true))

        assertFalse(viewModel.uiState.value.privacy.readStatusShared)
    }

    @Test
    fun `selecting a proxy type sets its default port`() {
        val viewModel = createViewModel()

        viewModel.setProxyType("HTTP")

        assertEquals(ProxyUiState(type = "HTTP", enabled = true, port = "3128"), viewModel.uiState.value.proxy)
        dispatcher.scheduler.advanceUntilIdle()
        verify(appPreferences).proxyPort = "3128"
    }

    @Test
    fun `turning the proxy off clears its settings`() {
        val viewModel = createViewModel()
        viewModel.setProxyType("SOCKS")
        viewModel.setProxyHost("proxy.example.com")
        viewModel.setProxyUseCredentials(true)
        viewModel.setProxyUsername("user")

        viewModel.setProxyType(NO_PROXY)

        assertEquals(ProxyUiState(type = NO_PROXY), viewModel.uiState.value.proxy)
        // also cleared once on creation, as the proxy was off
        dispatcher.scheduler.advanceUntilIdle()
        verify(appPreferences, times(2)).removeProxyHost()
        verify(appPreferences, times(2)).removeProxyUsername()
    }

    @Test
    fun `turning proxy credentials off clears them`() {
        val viewModel = createViewModel()
        viewModel.setProxyType("HTTP")
        viewModel.setProxyUseCredentials(true)
        viewModel.setProxyUsername("user")
        viewModel.setProxyPassword("secret")

        viewModel.setProxyUseCredentials(false)

        val proxy = viewModel.uiState.value.proxy
        assertFalse(proxy.useCredentials)
        assertEquals("", proxy.username)
        assertEquals("", proxy.password)
    }

    @Test
    fun `phone number is asked for when the phone book integration is enabled without one`() {
        repository.profile = UserProfileDataDto().apply { phone = "" }
        val viewModel = createViewModel()

        viewModel.enablePhoneBookIntegration()

        assertTrue(viewModel.uiState.value.privacy.phoneBookIntegration)
        assertTrue(viewModel.uiState.value.showPhoneNumberDialog)
    }

    @Test
    fun `rejected phone number keeps the dialog open with an error`() {
        repository.acceptPhoneNumber = false
        val viewModel = createViewModel()

        viewModel.submitPhoneNumber("123")

        assertTrue(viewModel.uiState.value.phoneNumberError)
    }

    @Test
    fun `accepted phone number closes the dialog and confirms it`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            repository.profile = UserProfileDataDto().apply { phone = "" }
            viewModel.enablePhoneBookIntegration()

            viewModel.submitPhoneNumber("49123456")

            assertFalse(viewModel.uiState.value.showPhoneNumberDialog)
            assertEquals(
                SettingsEvent.ShowMessage(R.string.nc_settings_phone_book_integration_phone_number_dialog_success),
                viewModel.events.first()
            )
        }

    @Test
    fun `denied contacts permission keeps the phone book integration off`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            viewModel.onContactsPermissionDenied()

            assertFalse(viewModel.uiState.value.privacy.phoneBookIntegration)
            assertEquals(
                SettingsEvent.ShowMessage(R.string.no_phone_book_integration_due_to_permissions),
                viewModel.events.first()
            )
        }

    @Test
    fun `screen stays open while its account is removed`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            viewModel.removeAccount()
            userFlow.value = user.copy(scheduledForDeletion = true)
            userFlow.value = null

            verify(accountRemover).remove(USER_ID, "Jane")
            assertNull(withTimeoutOrNull(TIMEOUT_MS) { viewModel.events.first() })
        }

    @Test
    fun `screen is closed when its account disappears`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()

            userFlow.value = null

            assertEquals(SettingsEvent.AccountUnavailable, viewModel.events.first())
        }

    @Test
    fun `account message is shown until dismissed`() {
        val viewModel = createViewModel()
        ApplicationWideMessageHolder.getInstance().messageType =
            ApplicationWideMessageHolder.MessageType.ACCOUNT_WAS_IMPORTED
        viewModel.onResume()

        assertEquals(AccountMessage.ACCOUNT_IMPORTED, viewModel.uiState.value.accountMessage)

        viewModel.dismissAccountMessage()

        assertNull(viewModel.uiState.value.accountMessage)
    }

    @Test
    fun `selecting the current proxy type again keeps a custom port`() {
        val viewModel = createViewModel()
        viewModel.setProxyType("HTTP")
        viewModel.setProxyPort("8888")

        viewModel.setProxyType("HTTP")

        assertEquals("8888", viewModel.uiState.value.proxy.port)
    }

    @Test
    fun `proxy text committed after the proxy was turned off is ignored`() {
        val viewModel = createViewModel()
        viewModel.setProxyType("HTTP")
        viewModel.setProxyType(NO_PROXY)

        viewModel.setProxyHost("proxy.example.com")

        assertEquals("", viewModel.uiState.value.proxy.host)
    }

    @Test
    fun `failed quick read status changes show the value the server confirmed`() {
        val viewModel = createViewModel()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        repository.failPrivacyRequests = true

        viewModel.setReadStatusShared(false)
        viewModel.setReadStatusShared(true)
        viewModel.setReadStatusShared(false)
        gate.complete(Unit)

        assertTrue(viewModel.uiState.value.privacy.readStatusShared)
        assertEquals(listOf(false), repository.readStatusRequests)
    }

    @Test
    fun `quick read status changes reach the server in order`() {
        val viewModel = createViewModel()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate

        viewModel.setReadStatusShared(false)
        viewModel.setReadStatusShared(true)
        gate.complete(Unit)

        assertTrue(viewModel.uiState.value.privacy.readStatusShared)
        assertEquals(listOf(false, true), repository.readStatusRequests)
    }

    @Test
    fun `screen lock timeout is reset when the device lock screen is not secure`() {
        deviceState = DEVICE_STATE.copy(isKeyguardSecure = false)
        val viewModel = createViewModel()

        viewModel.onResume()
        dispatcher.scheduler.advanceUntilIdle()

        val privacy = viewModel.uiState.value.privacy
        assertFalse(privacy.screenLockAvailable)
        assertFalse(privacy.screenLock)
        assertEquals("60", privacy.screenLockTimeout)
    }

    @Test
    fun `cancelled certificate choice keeps the stored certificate`() {
        val viewModel = createViewModel(user.copy(clientCertificate = "alias"))

        viewModel.onClientCertificateChosen(null)

        assertTrue(viewModel.uiState.value.clientCertificateSet)
        verifyBlocking(userManager, never()) { updateClientCertificate(any(), anyOrNull()) }
    }

    @Test
    fun `read status change is still sent after the screen was closed`() {
        val viewModel = createViewModel()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate

        viewModel.setReadStatusShared(false)
        viewModelStore.clear()
        gate.complete(Unit)

        assertEquals(listOf(false), repository.completedReadStatusRequests)
    }

    @Test
    fun `translated label of no proxy stored by older versions means no proxy`() {
        whenever(appPreferences.proxyType).thenReturn("Kein Proxy")

        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProxyUiState(), viewModel.uiState.value.proxy)
        verify(appPreferences).removeProxyHost()
    }

    @Test
    fun `pending account message is kept when the screen is resumed again`() {
        val viewModel = createViewModel()
        ApplicationWideMessageHolder.getInstance().messageType =
            ApplicationWideMessageHolder.MessageType.ACCOUNT_WAS_IMPORTED

        viewModel.onResume()
        viewModel.onResume()

        assertEquals(AccountMessage.ACCOUNT_IMPORTED, viewModel.uiState.value.accountMessage)
        assertNull(ApplicationWideMessageHolder.getInstance().messageType)
    }

    private fun capabilities(readStatusPrivate: Boolean) =
        CapabilitiesDto().apply {
            spreedCapability = SpreedCapabilityDto(
                features = emptyList(),
                config = hashMapOf("chat" to hashMapOf("read-privacy" to if (readStatusPrivate) 1 else 0)),
                version = ""
            )
        }

    private class FakeSettingsRepository : SettingsRepository {
        var profile: UserProfileDataDto? = null
        var acceptPhoneNumber = true
        var failPrivacyRequests = false
        var gate: CompletableDeferred<Unit>? = null
        val readStatusRequests = mutableListOf<Boolean>()
        val completedReadStatusRequests = mutableListOf<Boolean>()

        override suspend fun getUserProfile(user: User): UserProfileDataDto? = profile

        override suspend fun setPhoneNumber(user: User, phoneNumber: String): Boolean = acceptPhoneNumber

        override suspend fun setReadStatusShared(user: User, shared: Boolean) {
            readStatusRequests += shared
            gate?.await()
            if (failPrivacyRequests) throw IOException("rejected")
            completedReadStatusRequests += shared
        }

        override suspend fun setTypingStatusShared(user: User, shared: Boolean) {
            if (failPrivacyRequests) throw IOException("rejected")
        }
    }

    companion object {
        private const val USER_ID = 1L
        private const val NO_PROXY = "No proxy"
        private const val TIMEOUT_MS = 1000L
        private val DEVICE_STATE = DeviceState(
            isGooglePlayServicesAvailable = true,
            hasEmbeddedDistributor = false,
            externalDistributorCount = 0,
            acknowledgedDistributor = null,
            notificationPermissionGranted = true,
            batteryOptimizationIgnored = true,
            fullScreenIntentGranted = null,
            callRingtone = RingtoneState.Default,
            messageRingtone = RingtoneState.Disabled,
            isKeyguardSecure = true
        )
    }
}
