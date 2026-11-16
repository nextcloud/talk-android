/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsUiState(
    val isOnline: Boolean = false,
    val account: AccountUiState = AccountUiState(),
    val accountMessage: AccountMessage? = null,
    val appearance: AppearanceUiState = AppearanceUiState(),
    val notifications: NotificationsUiState = NotificationsUiState(),
    val privacy: PrivacyUiState = PrivacyUiState(),
    val loggingStatus: LoggingStatus = LoggingStatus.ENABLED,
    val mediaQualityReduced: Boolean = false,
    val clientCertificateSet: Boolean = false,
    val proxy: ProxyUiState = ProxyUiState(),
    /** Whether the screen was opened to fix the notification setup, so that problems there are highlighted. */
    val highlightNotificationIssues: Boolean = false,
    val showPhoneNumberDialog: Boolean = false,
    val phoneNumberError: Boolean = false
)

@Immutable
data class AccountUiState(
    val displayName: String = "",
    val baseUrl: String = "",
    val avatarId: String = "",
    /** Authorization for loading the avatar. */
    val credentials: String = "",
    val serverAgeWarning: ServerAgeWarning = ServerAgeWarning.NONE
)

enum class ServerAgeWarning {
    NONE,
    ALMOST_END_OF_LIFE,
    END_OF_LIFE
}

enum class AccountMessage {
    ACCOUNT_UPDATED,
    SERVER_WITHOUT_TALK,
    ACCOUNT_IMPORTED,
    ACCOUNT_IMPORT_FAILED
}

@Immutable
data class AppearanceUiState(
    /** Entry value of `R.array.theme_entry_values`. */
    val theme: String = "",
    val showEcosystemSetting: Boolean = true,
    val showEcosystem: Boolean = false
)

@Immutable
data class NotificationsUiState(
    /** Null if UnifiedPush can't be used, as no distributor is installed or an account doesn't support web push. */
    val unifiedPush: UnifiedPushUiState? = null,
    val pushAvailability: PushAvailability = PushAvailability.AVAILABLE,
    val serverNotificationAppMissing: Boolean = false,
    /** Null on devices that don't need the permission. */
    val notificationPermissionGranted: Boolean? = null,
    val batteryOptimizationIgnored: Boolean = true,
    /** Null on devices that don't need the permission. */
    val fullScreenIntentGranted: Boolean? = null,
    val callRingtone: RingtoneState = RingtoneState.Default,
    val messageRingtone: RingtoneState = RingtoneState.Default,
    val showNotificationWarningSetting: Boolean = false,
    val showNotificationWarning: Boolean = false
) {
    val ringtoneSettingsEnabled: Boolean
        get() = notificationPermissionGranted != false
}

@Immutable
data class UnifiedPushUiState(val enabled: Boolean, val distributor: String, val canPickDistributor: Boolean)

enum class PushAvailability {
    AVAILABLE,
    GOOGLE_PLAY_SERVICES_UNAVAILABLE,
    UNIFIED_PUSH_DISABLED_AND_GOOGLE_PLAY_SERVICES_UNAVAILABLE
}

@Immutable
sealed interface RingtoneState {
    /** The notification channel is turned off. */
    data object Disabled : RingtoneState
    data object Silent : RingtoneState
    data object Default : RingtoneState
    data class Custom(val title: String) : RingtoneState
}

@Immutable
data class PrivacyUiState(
    /** The app lock requires a secure lock screen of the device. */
    val screenLockAvailable: Boolean = false,
    val screenLock: Boolean = false,
    /** Entry value of `R.array.screen_lock_timeout_entry_values`. */
    val screenLockTimeout: String = "",
    val screenSecurity: Boolean = false,
    val incognitoKeyboard: Boolean = false,
    val phoneBookIntegrationAvailable: Boolean = false,
    val phoneBookIntegration: Boolean = false,
    val readStatusAvailable: Boolean = false,
    val readStatusShared: Boolean = false,
    val typingStatus: TypingStatusUiState = TypingStatusUiState.Hidden
)

@Immutable
sealed interface TypingStatusUiState {
    data object Hidden : TypingStatusUiState

    /** Typing status requires the high performance backend, which the server doesn't use. */
    data object RequiresHighPerformanceBackend : TypingStatusUiState
    data class Available(val shared: Boolean) : TypingStatusUiState
}

enum class LoggingStatus {
    DISABLED,
    ENABLED,
    ADVANCED
}

@Immutable
data class ProxyUiState(
    /** [NO_PROXY] or one of [PROXY_TYPES]. */
    val type: String = NO_PROXY,
    /** Whether [type] is a proxy, not "No proxy". */
    val enabled: Boolean = false,
    val host: String = "",
    val port: String = "",
    val useCredentials: Boolean = false,
    val username: String = "",
    val password: String = ""
) {
    companion object {
        /** The stored type without a proxy, as `RestModule` expects it. */
        const val NO_PROXY = "No proxy"
        const val TYPE_HTTP = "HTTP"
        const val TYPE_DIRECT = "DIRECT"
        const val TYPE_SOCKS = "SOCKS"

        /** The names of the `java.net.Proxy.Type` values that can be chosen. */
        val PROXY_TYPES = listOf(TYPE_HTTP, TYPE_DIRECT, TYPE_SOCKS)
    }
}

/** One-off effects of the settings screen that the activity performs. */
sealed interface SettingsEvent {
    data class ShowMessage(val messageRes: Int) : SettingsEvent

    /** The account of the screen doesn't exist anymore. */
    data object AccountUnavailable : SettingsEvent
}
