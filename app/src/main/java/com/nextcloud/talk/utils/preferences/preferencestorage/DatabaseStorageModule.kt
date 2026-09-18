/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.preferences.preferencestorage

import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.data.storage.model.ArbitraryStorage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ApiUtils.getConversationApiVersion
import com.nextcloud.talk.utils.ApiUtils.getCredentials
import com.nextcloud.talk.utils.ApiUtils.getUrlForMessageExpiration
import com.nextcloud.talk.utils.ApiUtils.getUrlForRoomNotificationCalls
import com.nextcloud.talk.utils.ApiUtils.getUrlForRoomNotificationLevel
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.UserIdUtils.getIdForUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class DatabaseStorageModule(conversationUser: User, conversationToken: String) {

    @JvmField
    @Inject
    var arbitraryStorageManager: ArbitraryStorageManager? = null

    @JvmField
    @Inject
    var ncApi: NcApi? = null

    @JvmField
    @Inject
    var ncApiCoroutines: NcApiCoroutines? = null

    private var messageExpiration = 0
    private val conversationUser: User
    private val conversationToken: String
    private val accountIdentifier: Long

    private var lobbyValue = false

    private var messageNotificationLevel: String? = null

    init {
        sharedApplication!!.componentApplication.inject(this)

        this.conversationUser = conversationUser
        this.accountIdentifier = getIdForUser(conversationUser)
        this.conversationToken = conversationToken
    }

    suspend fun saveBoolean(key: String, value: Boolean) {
        if ("call_notifications_switch" == key) {
            val apiVersion = getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.API_V4))
            val url = getUrlForRoomNotificationCalls(apiVersion, conversationUser.baseUrl, conversationToken)
            val credentials = getCredentials(conversationUser.username, conversationUser.token)
            val notificationLevel = if (value) 1 else 0
            withContext(Dispatchers.IO) {
                ncApiCoroutines!!.notificationCalls(credentials!!, url, notificationLevel)
            }
        }
        if ("lobby_switch" != key) {
            arbitraryStorageManager!!.storeStorageSetting(
                accountIdentifier,
                key,
                value.toString(),
                conversationToken
            )
        } else {
            lobbyValue = value
        }
    }

    suspend fun saveString(key: String, value: String) {
        when (key) {
            "conversation_settings_dropdown" -> saveMessageExpiration(value)
            "conversation_info_message_notifications_dropdown" -> saveNotificationLevel(value)
            else -> arbitraryStorageManager!!.storeStorageSetting(accountIdentifier, key, value, conversationToken)
        }
    }

    private suspend fun saveMessageExpiration(value: String) {
        val apiVersion = getConversationApiVersion(conversationUser, intArrayOf(API_VERSION_4))
        val valueInt = value.replace("expire_", "").toInt()
        withContext(Dispatchers.IO) {
            ncApiCoroutines!!.setMessageExpiration(
                getCredentials(conversationUser.username, conversationUser.token)!!,
                getUrlForMessageExpiration(apiVersion, conversationUser.baseUrl, conversationToken),
                valueInt
            )
            messageExpiration = valueInt
        }
    }

    private suspend fun saveNotificationLevel(value: String) {
        val spreedCapability = conversationUser.capabilities?.spreedCapability ?: return
        if (!hasSpreedFeatureCapability(spreedCapability, SpreedFeatures.NOTIFICATION_LEVELS)) {
            messageNotificationLevel = value
            return
        }
        if (messageNotificationLevel == value) {
            return
        }

        val intValue = when (value) {
            "never" -> NOTIFICATION_NEVER
            "mention" -> NOTIFICATION_MENTION
            "always" -> NOTIFICATION_ALWAYS
            else -> 0
        }
        val apiVersion = getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.API_V4, 1))
        withContext(Dispatchers.IO) {
            ncApiCoroutines!!.setNotificationLevel(
                getCredentials(conversationUser.username, conversationUser.token)!!,
                getUrlForRoomNotificationLevel(apiVersion, conversationUser.baseUrl, conversationToken),
                intValue
            )
            messageNotificationLevel = value
        }
    }

    fun getBoolean(key: String, defaultVal: Boolean): Boolean =
        if ("lobby_switch" == key) {
            lobbyValue
        } else {
            arbitraryStorageManager!!
                .getStorageSetting(accountIdentifier, key, conversationToken)
                .map { arbitraryStorage: ArbitraryStorage -> arbitraryStorage.value.toBoolean() }
                .blockingGet(defaultVal)
        }

    fun getString(key: String, defaultVal: String): String? =
        if ("conversation_settings_dropdown" == key) {
            when (messageExpiration) {
                EXPIRE_4_WEEKS -> "expire_2419200"
                EXPIRE_7_DAYS -> "expire_604800"
                EXPIRE_1_DAY -> "expire_86400"
                EXPIRE_8_HOURS -> "expire_28800"
                EXPIRE_1_HOUR -> "expire_3600"
                else -> "expire_0"
            }
        } else if ("conversation_info_message_notifications_dropdown" == key) {
            messageNotificationLevel
        } else {
            arbitraryStorageManager!!
                .getStorageSetting(accountIdentifier, key, conversationToken)
                .map(ArbitraryStorage::value)
                .blockingGet(defaultVal)
        }

    fun setMessageExpiration(messageExpiration: Int) {
        this.messageExpiration = messageExpiration
    }

    companion object {
        private const val EXPIRE_1_HOUR = 3600
        private const val EXPIRE_8_HOURS = 28800
        private const val EXPIRE_1_DAY = 86400
        private const val EXPIRE_7_DAYS = 604800
        private const val EXPIRE_4_WEEKS = 2419200
        private const val NOTIFICATION_NEVER = 3
        private const val NOTIFICATION_MENTION = 2
        private const val NOTIFICATION_ALWAYS = 1
        private const val API_VERSION_4 = 4
    }
}
