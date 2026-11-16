/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.nextcloud.talk.R
import com.nextcloud.talk.conversationlist.ConversationsListActivity.Companion.NOTIFICATION_WARNING_DATE_NOT_SET
import com.nextcloud.talk.dagger.modules.ApplicationScope
import com.nextcloud.talk.dagger.modules.IoDispatcher
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.jobs.CapabilitiesSyncWorker
import com.nextcloud.talk.jobs.ContactAddressBookWorker
import com.nextcloud.talk.logger.Level
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.logger.LogsRepository
import com.nextcloud.talk.settings.data.SettingsRepository
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.setExpeditedIfSupported
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

@Suppress("LongParameterList", "TooManyFunctions")
class SettingsViewModel @AssistedInject constructor(
    private val appPreferences: AppPreferences,
    private val userManager: UserManager,
    private val settingsRepository: SettingsRepository,
    private val logsRepository: LogsRepository,
    private val deviceStateReader: DeviceStateReader,
    private val accountRemover: AccountRemover,
    private val workManager: WorkManager,
    private val logger: Logger,
    networkMonitor: NetworkMonitor,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    // Dagger provides the application context, which cannot leak an Activity
    @field:SuppressLint("StaticFieldLeak") private val context: Context,
    @Assisted initialUser: User,
    @Assisted highlightNotificationIssues: Boolean
) : ViewModel() {

    // preferences are written in the order they were changed, and also after the screen was closed
    private val preferenceWriter = ioDispatcher.limitedParallelism(1)

    private val userId = initialUser.id!!
    private val user = MutableStateFlow(initialUser)

    // The last values in the stored account, which are only updated with the capabilities. A change is shown until
    // they change, so that an update of e.g. the display name doesn't revert it.
    private var storedReadStatusShared: Boolean? = null
    private var storedTypingStatusShared: Boolean? = null
    private val readStatusSetting = ServerSetting()
    private val typingStatusSetting = ServerSetting()

    private var allAccountsSupportWebPush = false
    private var unifiedPushPreference = false
    private var deviceState: DeviceState? = null
    private var isRemovingAccount = false

    private val _uiState = MutableStateFlow(initialState(initialUser, highlightNotificationIssues))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    val clientCertificateAlias: String?
        get() = user.value.clientCertificate?.takeIf { it.isNotEmpty() }

    init {
        clearDisabledProxySettings()
        viewModelScope.launch {
            userManager.userFlow(userId).collect { updatedUser ->
                when {
                    // the account disappears while it is removed, which restarts the app when done
                    isRemovingAccount -> Unit
                    updatedUser == null || updatedUser.scheduledForDeletion ->
                        _events.send(SettingsEvent.AccountUnavailable)
                    else -> user.value = updatedUser
                }
            }
        }
        viewModelScope.launch {
            combine(user, networkMonitor.isOnline, ::Pair).collect { (currentUser, isOnline) ->
                val readStatusChange = storedValueChange(
                    value = !CapabilitiesUtil.isReadStatusPrivate(currentUser),
                    previous = storedReadStatusShared,
                    setting = readStatusSetting
                ).also { storedReadStatusShared = it ?: storedReadStatusShared }
                val typingStatusChange = storedValueChange(
                    value = !CapabilitiesUtil.isTypingStatusPrivate(currentUser),
                    previous = storedTypingStatusShared,
                    setting = typingStatusSetting
                ).also { storedTypingStatusShared = it ?: storedTypingStatusShared }
                _uiState.update { it.withUser(currentUser, isOnline, readStatusChange, typingStatusChange) }
            }
        }
    }

    /** Refreshes everything that may have changed while the screen was in the background. */
    fun onResume() {
        val accountMessage = consumeAccountMessage()
        val loggingStatus = loggingStatus()
        _uiState.update {
            it.copy(loggingStatus = loggingStatus, accountMessage = accountMessage ?: it.accountMessage)
        }
        refreshDeviceState()
        refreshUserProfile()
        syncCapabilities()
    }

    /** Loads the capabilities of the account anew, which updates [user] through the stored account. */
    private fun syncCapabilities() {
        workManager.enqueue(
            OneTimeWorkRequest.Builder(CapabilitiesSyncWorker::class.java).setExpeditedIfSupported().build()
        )
    }

    fun refreshDeviceState() {
        viewModelScope.launch {
            allAccountsSupportWebPush = userManager.getUsers().all { it.hasWebPushCapability }
            val (useUnifiedPush, state) = withContext(ioDispatcher) {
                appPreferences.useUnifiedPush to deviceStateReader.read()
            }
            unifiedPushPreference = useUnifiedPush
            deviceState = state
            if (!state.isKeyguardSecure) {
                persist { prefs ->
                    prefs.removeScreenLock()
                    prefs.removeScreenLockTimeout()
                }
            }
            _uiState.update {
                it.copy(
                    notifications = notificationsState(state),
                    privacy = if (state.isKeyguardSecure) {
                        it.privacy.copy(screenLockAvailable = true)
                    } else {
                        it.privacy.copy(
                            screenLockAvailable = false,
                            screenLock = false,
                            screenLockTimeout = context.getString(R.string.nc_screen_lock_timeout_sixty)
                        )
                    }
                )
            }
        }
    }

    fun dismissAccountMessage() {
        _uiState.update { it.copy(accountMessage = null) }
    }

    fun setTheme(theme: String) {
        _uiState.update { it.copy(appearance = it.appearance.copy(theme = theme)) }
        persist { prefs -> prefs.theme = theme }
    }

    fun setShowEcosystem(show: Boolean) {
        _uiState.update { it.copy(appearance = it.appearance.copy(showEcosystem = show)) }
        persist { prefs -> prefs.setShowEcosystem(show) }
    }

    fun setUseUnifiedPush(enabled: Boolean) {
        val state = deviceState ?: return
        unifiedPushPreference = enabled
        persist { prefs -> prefs.useUnifiedPush = enabled }
        _uiState.update { it.copy(notifications = notificationsState(state)) }
    }

    fun onDistributorChanged(distributor: String?) {
        _uiState.update {
            val unifiedPush = it.notifications.unifiedPush ?: return@update it
            val updated = unifiedPush.copy(distributor = distributor.orEmpty())
            it.copy(notifications = it.notifications.copy(unifiedPush = updated))
        }
    }

    fun setShowNotificationWarning(show: Boolean) {
        _uiState.update { it.copy(notifications = it.notifications.copy(showNotificationWarning = show)) }
        persist { prefs ->
            prefs.setShowRegularNotificationWarning(show)
            if (show) {
                prefs.setNotificationWarningLastPostponedDate(NOTIFICATION_WARNING_DATE_NOT_SET)
            }
        }
    }

    fun setScreenLock(enabled: Boolean) {
        _uiState.update { it.copy(privacy = it.privacy.copy(screenLock = enabled)) }
        val timeout = _uiState.value.privacy.screenLockTimeout
        persist { prefs ->
            prefs.setScreenLock(enabled)
            if (enabled) {
                SecurityUtils.createKey(timeout)
                prefs.lockTimestamp = System.currentTimeMillis()
            }
        }
    }

    fun setScreenLockTimeout(timeout: String) {
        _uiState.update { it.copy(privacy = it.privacy.copy(screenLockTimeout = timeout)) }
        persist { prefs ->
            prefs.screenLockTimeout = timeout
            SecurityUtils.createKey(timeout)
        }
    }

    fun setScreenSecurity(enabled: Boolean) {
        _uiState.update { it.copy(privacy = it.privacy.copy(screenSecurity = enabled)) }
        persist { prefs -> prefs.setScreenSecurity(enabled) }
    }

    fun setIncognitoKeyboard(enabled: Boolean) {
        _uiState.update { it.copy(privacy = it.privacy.copy(incognitoKeyboard = enabled)) }
        persist { prefs -> prefs.setIncognitoKeyboard(enabled) }
    }

    /** Enables the phone book integration, for which the contacts permissions must be granted. */
    fun enablePhoneBookIntegration() {
        setPhoneBookIntegrationState(true)
        persist { prefs -> prefs.setPhoneBookIntegration(true) }
        ContactAddressBookWorker.syncNow(workManager)
        viewModelScope.launch {
            try {
                if (settingsRepository.getUserProfile(user.value)?.phone.isNullOrEmpty()) {
                    _uiState.update { it.copy(showPhoneNumberDialog = true, phoneNumberError = false) }
                }
            } catch (e: IOException) {
                logger.w(TAG, "Failed to check for the phone number", e)
            } catch (e: HttpException) {
                logger.w(TAG, "Failed to check for the phone number", e)
            }
        }
    }

    fun disablePhoneBookIntegration() {
        setPhoneBookIntegrationState(false)
        persist { prefs -> prefs.setPhoneBookIntegration(false) }
        ContactAddressBookWorker.deleteAll(workManager)
    }

    fun onContactsPermissionDenied() {
        setPhoneBookIntegrationState(false)
        persist { prefs -> prefs.setPhoneBookIntegration(false) }
        _events.trySend(SettingsEvent.ShowMessage(R.string.no_phone_book_integration_due_to_permissions))
    }

    fun submitPhoneNumber(phoneNumber: String) {
        viewModelScope.launch {
            val accepted = try {
                settingsRepository.setPhoneNumber(user.value, phoneNumber)
            } catch (e: IOException) {
                logger.e(TAG, "Failed to set the phone number", e)
                false
            } catch (e: HttpException) {
                logger.e(TAG, "Failed to set the phone number", e)
                false
            }
            if (accepted) {
                _uiState.update { it.copy(showPhoneNumberDialog = false, phoneNumberError = false) }
                _events.send(
                    SettingsEvent.ShowMessage(R.string.nc_settings_phone_book_integration_phone_number_dialog_success)
                )
            } else {
                _uiState.update { it.copy(phoneNumberError = true) }
            }
        }
    }

    fun clearPhoneNumberError() {
        _uiState.update { it.copy(phoneNumberError = false) }
    }

    fun dismissPhoneNumberDialog() {
        _uiState.update { it.copy(showPhoneNumberDialog = false, phoneNumberError = false) }
    }

    fun setReadStatusShared(shared: Boolean) {
        updatePrivacySetting(
            setting = readStatusSetting,
            apply = { state, value -> state.copy(readStatusShared = value) },
            shared = shared,
            request = { settingsRepository.setReadStatusShared(user.value, it) }
        )
    }

    fun setTypingStatusShared(shared: Boolean) {
        updatePrivacySetting(
            setting = typingStatusSetting,
            apply = { state, value ->
                if (state.typingStatus is TypingStatusUiState.Available) {
                    state.copy(typingStatus = TypingStatusUiState.Available(value))
                } else {
                    state
                }
            },
            shared = shared,
            request = { settingsRepository.setTypingStatusShared(user.value, it) }
        )
    }

    fun setMediaQualityReduced(reduced: Boolean) {
        _uiState.update { it.copy(mediaQualityReduced = reduced) }
        persist { prefs -> prefs.setCompressUploadImages(reduced) }
    }

    /** Stores the alias of the chosen client certificate. A null [alias] means the choice was cancelled. */
    fun onClientCertificateChosen(alias: String?) {
        if (alias == null) return
        _uiState.update { it.copy(clientCertificateSet = true) }
        viewModelScope.launch {
            userManager.updateClientCertificate(userId, alias)
        }
    }

    fun setProxyType(type: String) {
        if (type == _uiState.value.proxy.type) return
        if (!isProxyEnabled(type)) {
            _uiState.update { it.copy(proxy = ProxyUiState(type = ProxyUiState.NO_PROXY)) }
            persist { prefs -> prefs.proxyType = ProxyUiState.NO_PROXY }
            clearProxySettings()
            return
        }
        val port = defaultProxyPort(type) ?: _uiState.value.proxy.port
        _uiState.update { it.copy(proxy = it.proxy.copy(type = type, enabled = true, port = port)) }
        persist { prefs ->
            prefs.proxyType = type
            prefs.proxyPort = port
        }
    }

    // the text fields commit when they leave the screen, also after the proxy or its credentials were turned off
    fun setProxyHost(host: String) {
        if (!_uiState.value.proxy.enabled) return
        _uiState.update { it.copy(proxy = it.proxy.copy(host = host)) }
        persist { prefs -> prefs.proxyHost = host }
    }

    fun setProxyPort(port: String) {
        if (!_uiState.value.proxy.enabled) return
        _uiState.update { it.copy(proxy = it.proxy.copy(port = port)) }
        persist { prefs -> prefs.proxyPort = port }
    }

    fun setProxyUseCredentials(useCredentials: Boolean) {
        _uiState.update {
            it.copy(
                proxy = if (useCredentials) {
                    it.proxy.copy(useCredentials = true)
                } else {
                    it.proxy.copy(useCredentials = false, username = "", password = "")
                }
            )
        }
        persist { prefs ->
            prefs.setProxyNeedsCredentials(useCredentials)
            if (!useCredentials) {
                prefs.removeProxyUsername()
                prefs.removeProxyPassword()
            }
        }
    }

    fun setProxyUsername(username: String) {
        if (!_uiState.value.proxy.useCredentials) return
        _uiState.update { it.copy(proxy = it.proxy.copy(username = username)) }
        persist { prefs -> prefs.proxyUsername = username }
    }

    fun setProxyPassword(password: String) {
        if (!_uiState.value.proxy.useCredentials) return
        _uiState.update { it.copy(proxy = it.proxy.copy(password = password)) }
        persist { prefs -> prefs.proxyPassword = password }
    }

    fun removeAccount() {
        if (isRemovingAccount) return
        isRemovingAccount = true
        accountRemover.remove(userId, _uiState.value.account.displayName)
    }

    private fun initialState(initialUser: User, highlightNotificationIssues: Boolean): SettingsUiState {
        // older versions stored the translated label of "No proxy"
        val proxyType = appPreferences.proxyType.takeIf(::isProxyEnabled) ?: ProxyUiState.NO_PROXY
        val useCredentials = isProxyEnabled(proxyType) && appPreferences.proxyCredentials
        return SettingsUiState(
            account = accountState(initialUser),
            appearance = AppearanceUiState(
                theme = appPreferences.theme,
                showEcosystemSetting = !context.resources.getBoolean(R.bool.is_branded_client),
                showEcosystem = appPreferences.isShowEcosystem
            ),
            notifications = NotificationsUiState(
                showNotificationWarning = appPreferences.showRegularNotificationWarning == true
            ),
            privacy = PrivacyUiState(
                screenLock = appPreferences.isScreenLocked,
                screenLockTimeout = appPreferences.screenLockTimeout,
                screenSecurity = appPreferences.isScreenSecured,
                incognitoKeyboard = appPreferences.isKeyboardIncognito,
                phoneBookIntegration = appPreferences.isPhoneBookIntegrationEnabled
            ),
            loggingStatus = loggingStatus(),
            mediaQualityReduced = appPreferences.compressUploadImages,
            clientCertificateSet = !initialUser.clientCertificate.isNullOrEmpty(),
            proxy = if (isProxyEnabled(proxyType)) {
                ProxyUiState(
                    type = proxyType,
                    enabled = true,
                    host = appPreferences.proxyHost,
                    port = appPreferences.proxyPort,
                    useCredentials = useCredentials,
                    username = if (useCredentials) appPreferences.proxyUsername else "",
                    password = if (useCredentials) appPreferences.proxyPassword else ""
                )
            } else {
                ProxyUiState(type = proxyType)
            },
            highlightNotificationIssues = highlightNotificationIssues
        )
    }

    /**
     * Applies [currentUser]. [readStatusChange] and [typingStatusChange] are the values of the stored account if they
     * changed, else null to keep what the screen shows.
     */
    private fun SettingsUiState.withUser(
        currentUser: User,
        isOnline: Boolean,
        readStatusChange: Boolean?,
        typingStatusChange: Boolean?
    ): SettingsUiState {
        val spreedCapability = currentUser.capabilities?.spreedCapability
        val typingStatus = when {
            currentUser.externalSignalingServer?.externalSignalingServer.isNullOrEmpty() ->
                TypingStatusUiState.RequiresHighPerformanceBackend
            !isOnline || !CapabilitiesUtil.isTypingStatusAvailable(currentUser) -> TypingStatusUiState.Hidden
            typingStatusChange != null -> TypingStatusUiState.Available(typingStatusChange)
            privacy.typingStatus !is TypingStatusUiState.Available ->
                TypingStatusUiState.Available(typingStatusSetting.requested ?: false)
            else -> privacy.typingStatus
        }
        return copy(
            isOnline = isOnline,
            account = accountState(currentUser),
            notifications = notifications.copy(
                serverNotificationAppMissing =
                currentUser.capabilities?.notificationsCapability?.features.isNullOrEmpty()
            ),
            privacy = privacy.copy(
                phoneBookIntegrationAvailable = isOnline &&
                    CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapability, SpreedFeatures.PHONEBOOK_SEARCH),
                readStatusAvailable = isOnline && CapabilitiesUtil.isReadStatusAvailable(spreedCapability),
                readStatusShared = readStatusChange ?: privacy.readStatusShared,
                typingStatus = typingStatus
            )
        )
    }

    private fun accountState(currentUser: User): AccountUiState {
        val serverVersion = currentUser.serverVersion?.major
        return AccountUiState(
            displayName = currentUser.displayName.orEmpty(),
            baseUrl = currentUser.baseUrl.orEmpty(),
            avatarId = currentUser.userId?.takeIf { it.isNotEmpty() } ?: currentUser.username.orEmpty(),
            credentials = currentUser.getCredentials(),
            serverAgeWarning = when {
                CapabilitiesUtil.isServerEOL(serverVersion) -> ServerAgeWarning.END_OF_LIFE
                CapabilitiesUtil.isServerAlmostEOL(serverVersion) -> ServerAgeWarning.ALMOST_END_OF_LIFE
                else -> ServerAgeWarning.NONE
            }
        )
    }

    private fun notificationsState(state: DeviceState): NotificationsUiState {
        val current = _uiState.value.notifications
        // UnifiedPush is only offered if every account supports web push
        val unifiedPushOffered = state.externalDistributorCount > 0 && allAccountsSupportWebPush
        val unifiedPushEnabled = unifiedPushOffered && unifiedPushPreference
        val unifiedPush = if (unifiedPushOffered) {
            UnifiedPushUiState(
                enabled = unifiedPushEnabled,
                distributor = current.unifiedPush?.distributor?.takeIf { it.isNotEmpty() }
                    ?: state.acknowledgedDistributor.orEmpty(),
                canPickDistributor = unifiedPushEnabled && state.externalDistributorCount > 1
            )
        } else {
            null
        }
        val pushAvailability = when {
            state.isGooglePlayServicesAvailable || unifiedPushPreference || state.hasEmbeddedDistributor ->
                PushAvailability.AVAILABLE
            unifiedPushOffered -> PushAvailability.UNIFIED_PUSH_DISABLED_AND_GOOGLE_PLAY_SERVICES_UNAVAILABLE
            else -> PushAvailability.GOOGLE_PLAY_SERVICES_UNAVAILABLE
        }
        return current.copy(
            unifiedPush = unifiedPush,
            pushAvailability = pushAvailability,
            notificationPermissionGranted = state.notificationPermissionGranted,
            batteryOptimizationIgnored = state.batteryOptimizationIgnored,
            fullScreenIntentGranted = state.fullScreenIntentGranted,
            callRingtone = state.callRingtone,
            messageRingtone = state.messageRingtone,
            showNotificationWarningSetting = state.isGooglePlayServicesAvailable
        )
    }

    /**
     * Returns [value] if it differs from the [previous] value of the stored account, after taking it over as the
     * confirmed value of [setting]. Returns null if it didn't change.
     */
    private fun storedValueChange(value: Boolean, previous: Boolean?, setting: ServerSetting): Boolean? {
        if (value == previous) return null
        setting.confirmed = value
        setting.requested = value
        return value
    }

    /**
     * Shows [shared] right away and sends it to the server. Requests are sent one at a time, a change replaced by a
     * newer one is skipped, and a failed change shows the value the server confirmed last. They run in the application
     * scope, so that closing the screen doesn't cancel a change it already shows, and on the main thread, which
     * [setting] is confined to.
     */
    private fun updatePrivacySetting(
        setting: ServerSetting,
        apply: (PrivacyUiState, Boolean) -> PrivacyUiState,
        shared: Boolean,
        request: suspend (Boolean) -> Unit
    ) {
        setting.requested = shared
        _uiState.update { it.copy(privacy = apply(it.privacy, shared)) }
        appScope.launch(Dispatchers.Main.immediate) {
            setting.mutex.withLock {
                if (setting.requested != shared || setting.confirmed == shared) return@withLock
                try {
                    request(shared)
                    setting.confirmed = shared
                    syncCapabilities()
                } catch (e: IOException) {
                    logger.e(TAG, "Failed to change a privacy setting", e)
                    revertPrivacySetting(setting, apply, shared)
                } catch (e: HttpException) {
                    logger.e(TAG, "Failed to change a privacy setting", e)
                    revertPrivacySetting(setting, apply, shared)
                }
            }
        }
    }

    private fun revertPrivacySetting(
        setting: ServerSetting,
        apply: (PrivacyUiState, Boolean) -> PrivacyUiState,
        failed: Boolean
    ) {
        val confirmed = setting.confirmed ?: !failed
        if (setting.requested == failed) {
            setting.requested = confirmed
            _uiState.update { it.copy(privacy = apply(it.privacy, confirmed)) }
        }
    }

    private fun setPhoneBookIntegrationState(enabled: Boolean) {
        _uiState.update { it.copy(privacy = it.privacy.copy(phoneBookIntegration = enabled)) }
    }

    private fun loggingStatus(): LoggingStatus =
        when {
            logsRepository.minimumLevel == Level.NONE -> LoggingStatus.DISABLED
            logsRepository.minimumLevel <= Level.DEBUG -> LoggingStatus.ADVANCED
            else -> LoggingStatus.ENABLED
        }

    private fun consumeAccountMessage(): AccountMessage? {
        val holder = ApplicationWideMessageHolder.getInstance()
        val message = when (holder.messageType) {
            ApplicationWideMessageHolder.MessageType.ACCOUNT_UPDATED_NOT_ADDED -> AccountMessage.ACCOUNT_UPDATED
            ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK -> AccountMessage.SERVER_WITHOUT_TALK
            ApplicationWideMessageHolder.MessageType.ACCOUNT_WAS_IMPORTED -> AccountMessage.ACCOUNT_IMPORTED
            ApplicationWideMessageHolder.MessageType.FAILED_TO_IMPORT_ACCOUNT -> AccountMessage.ACCOUNT_IMPORT_FAILED
            else -> null
        }
        holder.messageType = null
        return message
    }

    private fun refreshUserProfile() {
        viewModelScope.launch {
            val profile = try {
                settingsRepository.getUserProfile(user.value)
            } catch (e: IOException) {
                logger.w(TAG, "Failed to load the user profile", e)
                null
            } catch (e: HttpException) {
                logger.w(TAG, "Failed to load the user profile", e)
                null
            }
            val displayName = profile?.displayName?.takeIf { it.isNotEmpty() }
                ?: profile?.displayNameAlt?.takeIf { it.isNotEmpty() }
            if (displayName != null && displayName != user.value.displayName) {
                userManager.updateDisplayName(userId, displayName)
            }
        }
    }

    private fun isProxyEnabled(type: String): Boolean = type in ProxyUiState.PROXY_TYPES

    private fun defaultProxyPort(type: String): String? =
        when (type) {
            ProxyUiState.TYPE_HTTP -> context.getString(R.string.nc_settings_http_value)
            ProxyUiState.TYPE_DIRECT -> context.getString(R.string.nc_settings_direct_value)
            ProxyUiState.TYPE_SOCKS -> context.getString(R.string.nc_settings_socks_value)
            else -> null
        }

    private fun clearDisabledProxySettings() {
        val proxy = _uiState.value.proxy
        when {
            !isProxyEnabled(proxy.type) -> clearProxySettings()
            !proxy.useCredentials -> persist { prefs ->
                prefs.removeProxyUsername()
                prefs.removeProxyPassword()
            }
        }
    }

    private fun clearProxySettings() {
        persist { prefs ->
            prefs.removeProxyHost()
            prefs.removeProxyPort()
            prefs.removeProxyCredentials()
            prefs.removeProxyUsername()
            prefs.removeProxyPassword()
        }
    }

    private fun persist(write: (AppPreferences) -> Unit) {
        appScope.launch(preferenceWriter) { write(appPreferences) }
    }

    @AssistedFactory
    interface Factory {
        fun build(user: User, highlightNotificationIssues: Boolean): SettingsViewModel
    }

    companion object {
        private val TAG = SettingsViewModel::class.java.simpleName
    }
}

/** A setting stored on the server, which the screen changes one request at a time. */
private class ServerSetting {
    val mutex = Mutex()

    /** The value the server confirmed last, or the one of the stored account. */
    var confirmed: Boolean? = null

    /** The value chosen last on the screen. */
    var requested: Boolean? = null
}
