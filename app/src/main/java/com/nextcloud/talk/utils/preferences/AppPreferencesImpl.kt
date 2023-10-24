/*
 * Nextcloud Talk application
 *
 * @author Julius Linus
 * Copyright (C) 2023 Julius Linus <julius.linus@nextcloud.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.utils.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nextcloud.talk.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
@Suppress("TooManyFunctions", "DeferredResultUnused", "EmptyFunctionBlock")
class AppPreferencesImpl(val context: Context) : AppPreferences {

    override fun getProxyType(): String {
        return runBlocking { async { readString(PROXY_TYPE, "No proxy").first() } }.getCompleted()
    }

    override fun setProxyType(proxyType: String?) = runBlocking<Unit> {
        async {
            if (proxyType != null) {
                writeString(PROXY_TYPE, proxyType)
            }
        }
    }

    override fun removeProxyType() {
        proxyType = ""
    }

    override fun getProxyHost(): String {
        return runBlocking { async { readString(PROXY_HOST).first() } }.getCompleted()
    }

    override fun setProxyHost(proxyHost: String?) = runBlocking<Unit> {
        async {
            if (proxyHost != null) {
                writeString(PROXY_HOST, proxyHost)
            }
        }
    }

    override fun removeProxyHost() {
        proxyHost = ""
    }

    override fun getProxyPort(): String {
        return runBlocking { async { readString(PROXY_PORT).first() } }.getCompleted()
    }

    override fun setProxyPort(proxyPort: String?) = runBlocking<Unit> {
        async {
            if (proxyPort != null) {
                writeString(PROXY_PORT, proxyPort)
            }
        }
    }

    override fun removeProxyPort() {
        proxyPort = ""
    }

    override fun getProxyCredentials(): Boolean {
        return runBlocking { async { readBoolean(PROXY_CRED).first() } }.getCompleted()
    }

    override fun setProxyNeedsCredentials(proxyNeedsCredentials: Boolean) = runBlocking<Unit> {
        async {
            writeBoolean(PROXY_CRED, proxyNeedsCredentials)
        }
    }

    override fun removeProxyCredentials() {
        setProxyNeedsCredentials(false)
    }

    override fun getProxyUsername(): String {
        return runBlocking { async { readString(PROXY_USERNAME).first() } }.getCompleted()
    }

    override fun setProxyUsername(proxyUsername: String?) = runBlocking<Unit> {
        async {
            if (proxyUsername != null) {
                writeString(PROXY_USERNAME, proxyUsername)
            }
        }
    }

    override fun removeProxyUsername() {
        proxyUsername = ""
    }

    override fun getProxyPassword(): String {
        return runBlocking { async { readString(PROXY_PASSWORD).first() } }.getCompleted()
    }

    override fun setProxyPassword(proxyPassword: String?) = runBlocking<Unit> {
        async {
            if (proxyPassword != null) {
                writeString(PROXY_PASSWORD, proxyPassword)
            }
        }
    }

    override fun removeProxyPassword() {
        proxyPassword = ""
    }

    override fun getPushToken(): String {
        return runBlocking { async { readString(PUSH_TOKEN).first() } }.getCompleted()
    }

    override fun setPushToken(pushToken: String?) = runBlocking<Unit> {
        async {
            if (pushToken != null) {
                writeString(PUSH_TOKEN, pushToken)
            }
        }
    }

    override fun removePushToken() {
        pushToken = ""
    }

    override fun getTemporaryClientCertAlias(): String {
        return runBlocking { async { readString(TEMP_CLIENT_CERT_ALIAS).first() } }.getCompleted()
    }

    override fun setTemporaryClientCertAlias(alias: String?) = runBlocking<Unit> {
        async {
            if (alias != null) {
                writeString(TEMP_CLIENT_CERT_ALIAS, alias)
            }
        }
    }

    override fun removeTemporaryClientCertAlias() {
        temporaryClientCertAlias = ""
    }

    override fun getPushToTalkIntroShown(): Boolean {
        return runBlocking { async { readBoolean(PUSH_TO_TALK_INTRO_SHOWN).first() } }.getCompleted()
    }

    override fun setPushToTalkIntroShown(shown: Boolean) = runBlocking<Unit> {
        async {
            writeBoolean(PUSH_TO_TALK_INTRO_SHOWN, shown)
        }
    }

    override fun removePushToTalkIntroShown() {
        pushToTalkIntroShown = false
    }

    override fun getCallRingtoneUri(): String {
        return runBlocking { async { readString(CALL_RINGTONE).first() } }.getCompleted()
    }

    override fun setCallRingtoneUri(value: String?) = runBlocking<Unit> {
        async {
            if (value != null) {
                writeString(CALL_RINGTONE, value)
            }
        }
    }

    override fun removeCallRingtoneUri() {
        callRingtoneUri = ""
    }

    override fun getMessageRingtoneUri(): String {
        return runBlocking { async { readString(MESSAGE_RINGTONE).first() } }.getCompleted()
    }

    override fun setMessageRingtoneUri(value: String?) = runBlocking<Unit> {
        async {
            if (value != null) {
                writeString(MESSAGE_RINGTONE, value)
            }
        }
    }

    override fun removeMessageRingtoneUri() {
        messageRingtoneUri = ""
    }

    override fun getIsNotificationChannelUpgradedToV2(): Boolean {
        return runBlocking { async { readBoolean(NOTIFY_UPGRADE_V2).first() } }.getCompleted()
    }

    override fun setNotificationChannelIsUpgradedToV2(value: Boolean) = runBlocking<Unit> {
        async {
            writeBoolean(NOTIFY_UPGRADE_V2, value)
        }
    }

    override fun removeNotificationChannelUpgradeToV2() {
        setNotificationChannelIsUpgradedToV2(false)
    }

    override fun getIsNotificationChannelUpgradedToV3(): Boolean {
        return runBlocking { async { readBoolean(NOTIFY_UPGRADE_V3).first() } }.getCompleted()
    }

    override fun setNotificationChannelIsUpgradedToV3(value: Boolean) = runBlocking<Unit> {
        async {
            writeBoolean(NOTIFY_UPGRADE_V3, value)
        }
    }

    override fun removeNotificationChannelUpgradeToV3() {
        setNotificationChannelIsUpgradedToV3(false)
    }

    override fun getIsScreenSecured(): Boolean {
        return runBlocking { async { readBoolean(SCREEN_SECURITY).first() } }.getCompleted()
    }

    override fun setScreenSecurity(value: Boolean) = runBlocking<Unit> {
        async {
            writeBoolean(SCREEN_SECURITY, value)
        }
    }

    override fun removeScreenSecurity() {
        setScreenSecurity(false)
    }

    override fun getIsScreenLocked(): Boolean {
        return runBlocking { async { readBoolean(SCREEN_LOCK).first() } }.getCompleted()
    }

    override fun setScreenLock(value: Boolean) = runBlocking<Unit> {
        async {
            writeBoolean(SCREEN_LOCK, value)
        }
    }

    override fun removeScreenLock() {
        setScreenLock(false)
    }

    override fun getIsKeyboardIncognito(): Boolean {
        val read = runBlocking { async { readBoolean(INCOGNITO_KEYBOARD).first() } }.getCompleted()
        return read
    }

    override fun setIncognitoKeyboard(value: Boolean) = runBlocking<Unit> {
        async {
            writeBoolean(INCOGNITO_KEYBOARD, value)
        }
    }

    override fun removeIncognitoKeyboard() {
        setIncognitoKeyboard(false)
    }

    override fun isPhoneBookIntegrationEnabled(): Boolean {
        return runBlocking { async { readBoolean(PHONE_BOOK_INTEGRATION).first() } }.getCompleted()
    }

    override fun setPhoneBookIntegration(value: Boolean) = runBlocking<Unit> {
        async {
            writeBoolean(PHONE_BOOK_INTEGRATION, value)
        }
    }

    override fun removeLinkPreviews() = runBlocking<Unit> {
        async {
            writeBoolean(LINK_PREVIEWS, false)
        }
    }

    override fun getScreenLockTimeout(): String {
        val default = context.resources.getString(R.string.nc_screen_lock_timeout_sixty)
        val read = runBlocking { async { readString(SCREEN_LOCK_TIMEOUT).first() } }.getCompleted()
        return read.ifEmpty { default }
    }

    override fun setScreenLockTimeout(value: String?) = runBlocking<Unit> {
        async {
            if (value != null) {
                writeString(SCREEN_LOCK_TIMEOUT, value)
            }
        }
    }

    override fun removeScreenLockTimeout() {
        screenLockTimeout = ""
    }

    override fun getTheme(): String {
        val key = context.resources.getString(R.string.nc_settings_theme_key)
        val default = context.resources.getString(R.string.nc_default_theme)
        val read = runBlocking { async { readString(key).first() } }.getCompleted()
        return read.ifEmpty { default }
    }

    override fun setTheme(value: String?) = runBlocking<Unit> {
        async {
            if (value != null) {
                val key = context.resources.getString(R.string.nc_settings_theme_key)
                writeString(key, value)
            }
        }
    }

    override fun removeTheme() {
        theme = ""
    }

    override fun isDbCypherToUpgrade(): Boolean {
        val read = runBlocking { async { readBoolean(DB_CYPHER_V4_UPGRADE).first() } }.getCompleted()
        return read
    }

    override fun setDbCypherToUpgrade(value: Boolean) = runBlocking<Unit> {
        async {
            writeBoolean(DB_CYPHER_V4_UPGRADE, value)
        }
    }

    override fun getIsDbRoomMigrated(): Boolean {
        return runBlocking { async { readBoolean(DB_ROOM_MIGRATED).first() } }.getCompleted()
    }

    override fun setIsDbRoomMigrated(value: Boolean) = runBlocking<Unit> {
        async {
            writeBoolean(DB_ROOM_MIGRATED, value)
        }
    }

    override fun setPhoneBookIntegrationLastRun(currentTimeMillis: Long) = runBlocking<Unit> {
        async {
            writeLong(PHONE_BOOK_INTEGRATION_LAST_RUN, currentTimeMillis)
        }
    }

    override fun getPhoneBookIntegrationLastRun(defaultValue: Long?): Long {
        val result = if (defaultValue != null) {
            runBlocking { async { readLong(PHONE_BOOK_INTEGRATION_LAST_RUN, defaultValue = defaultValue).first() } }
                .getCompleted()
        } else {
            runBlocking { async { readLong(PHONE_BOOK_INTEGRATION_LAST_RUN).first() } }.getCompleted()
        }
        return result
    }

    override fun setReadPrivacy(value: Boolean) = runBlocking<Unit> {
        val key = context.resources.getString(R.string.nc_settings_read_privacy_key)
        async {
            writeBoolean(key, value)
        }
    }

    override fun getReadPrivacy(): Boolean {
        val key = context.resources.getString(R.string.nc_settings_read_privacy_key)
        return runBlocking { async { readBoolean(key).first() } }.getCompleted()
    }

    override fun setTypingStatus(value: Boolean) = runBlocking<Unit> {
        async {
            writeBoolean(TYPING_STATUS, value)
        }
    }

    override fun getTypingStatus(): Boolean {
        return runBlocking { async { readBoolean(TYPING_STATUS).first() } }.getCompleted()
    }

    override fun setSorting(value: String?) = runBlocking<Unit> {
        val key = context.resources.getString(R.string.nc_file_browser_sort_by_key)
        async {
            if (value != null) {
                writeString(key, value)
            }
        }
    }

    override fun getSorting(): String {
        val key = context.resources.getString(R.string.nc_file_browser_sort_by_key)
        val default = context.resources.getString(R.string.nc_file_browser_sort_by_default)
        val read = runBlocking { async { readString(key).first() } }.getCompleted()
        return read.ifEmpty { default }
    }

    override fun clear() {}

    private suspend fun writeString(key: String, value: String) = context.dataStore.edit { settings ->
        settings[
            stringPreferencesKey(
                key
            )
        ] = value
    }

    /**
     * Returns a Flow of type String
     * @param key the key of the persisted data to be observed
     */
    fun readString(key: String, defaultValue: String = ""): Flow<String> = context.dataStore.data.map { preferences ->
        preferences[stringPreferencesKey(key)] ?: defaultValue
    }

    private suspend fun writeBoolean(key: String, value: Boolean) = context.dataStore.edit { settings ->
        settings[
            booleanPreferencesKey(
                key
            )
        ] = value
    }

    /**
     * Returns a Flow of type Boolean
     * @param key the key of the persisted data to be observed
     */
    fun readBoolean(key: String, defaultValue: Boolean = false): Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[booleanPreferencesKey(key)] ?: defaultValue
        }

    private suspend fun writeLong(key: String, value: Long) = context.dataStore.edit { settings ->
        settings[
            longPreferencesKey(
                key
            )
        ] = value
    }

    private fun readLong(key: String, defaultValue: Long = 0): Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[longPreferencesKey(key)] ?: defaultValue
    }

    companion object {
        @Suppress("UnusedPrivateProperty")
        private val TAG = AppPreferencesImpl::class.simpleName
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
        const val PROXY_TYPE = "proxy_type"
        const val PROXY_SERVER = "proxy_server"
        const val PROXY_HOST = "proxy_host"
        const val PROXY_PORT = "proxy_port"
        const val PROXY_CRED = "proxy_credentials"
        const val PROXY_USERNAME = "proxy_username"
        const val PROXY_PASSWORD = "proxy_password"
        const val PUSH_TOKEN = "push_token"
        const val TEMP_CLIENT_CERT_ALIAS = "tempClientCertAlias"
        const val PUSH_TO_TALK_INTRO_SHOWN = "pushToTalk_intro_shown"
        const val CALL_RINGTONE = "call_ringtone"
        const val MESSAGE_RINGTONE = "message_ringtone"
        const val NOTIFY_UPGRADE_V2 = "notification_channels_upgrade_to_v2"
        const val NOTIFY_UPGRADE_V3 = "notification_channels_upgrade_to_v3"
        const val SCREEN_SECURITY = "screen_security"
        const val SCREEN_LOCK = "screen_lock"
        const val INCOGNITO_KEYBOARD = "incognito_keyboard"
        const val PHONE_BOOK_INTEGRATION = "phone_book_integration"
        const val LINK_PREVIEWS = "link_previews"
        const val SCREEN_LOCK_TIMEOUT = "screen_lock_timeout"
        const val DB_CYPHER_V4_UPGRADE = "db_cypher_v4_upgrade"
        const val DB_ROOM_MIGRATED = "db_room_migrated"
        const val PHONE_BOOK_INTEGRATION_LAST_RUN = "phone_book_integration_last_run"
        const val TYPING_STATUS = "typing_status"
    }
}
