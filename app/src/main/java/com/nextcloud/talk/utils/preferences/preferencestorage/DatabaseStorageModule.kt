/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.preferences.preferencestorage

import android.text.TextUtils
import android.util.Log
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
            val apiVersion = getConversationApiVersion(conversationUser, intArrayOf(4))
            val url = getUrlForRoomNotificationCalls(apiVersion, conversationUser.baseUrl, conversationToken)
            val credentials = getCredentials(conversationUser.username, conversationUser.token)
            val notificationLevel = if (value) 1 else 0
            withContext(Dispatchers.IO) {
                try {
                    ncApiCoroutines!!.notificationCalls(credentials!!, url, notificationLevel)
                    Log.d(TAG, "Toggled notification calls")
                } catch (e: Throwable) {
                    Log.e(TAG, "Error when trying to toggle notification calls", e)
                }
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
            "conversation_settings_dropdown" -> {
                try {
                    val apiVersion = getConversationApiVersion(conversationUser, intArrayOf(4))
                    val trimmedValue = value.replace("expire_", "")
                    val valueInt = trimmedValue.toInt()
                    withContext(Dispatchers.IO) {
                        ncApiCoroutines!!.setMessageExpiration(
                            getCredentials(conversationUser.username, conversationUser.token)!!,
                            getUrlForMessageExpiration(
                                apiVersion,
                                conversationUser.baseUrl,
                                conversationToken
                            ),
                            valueInt
                        )
                        messageExpiration = valueInt
                    }
                } catch (exception: Exception) {
                    Log.e(TAG, "Error when trying to set message expiration", exception)
                }
            }
            "conversation_info_message_notifications_dropdown" -> {
                try {
                    if (hasSpreedFeatureCapability(
                            conversationUser.capabilities!!.spreedCapability!!,
                            SpreedFeatures.NOTIFICATION_LEVELS
                        )
                    ) {
                        if (TextUtils.isEmpty(messageNotificationLevel) || messageNotificationLevel != value) {
                            val intValue = when (value) {
                                "never" -> 3
                                "mention" -> 2
                                "always" -> 1
                                else -> 0
                            }
                            val apiVersion = getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.API_V4, 1))
                            withContext(Dispatchers.IO) {
                                ncApiCoroutines!!.setNotificationLevel(
                                    getCredentials(
                                        conversationUser.username,
                                        conversationUser.token
                                    )!!,
                                    getUrlForRoomNotificationLevel(
                                        apiVersion,
                                        conversationUser.baseUrl,
                                        conversationToken
                                    ),
                                    intValue
                                )
                                messageNotificationLevel = value
                            }
                        } else {
                            messageNotificationLevel = value
                        }
                    }
                } catch (exception: Exception) {
                    Log.e(TAG, "Error trying to set notification level", exception)
                }
            }
            else -> {
                arbitraryStorageManager!!.storeStorageSetting(accountIdentifier, key, value, conversationToken)
            }
        }
    }

    fun getBoolean(key: String, defaultVal: Boolean): Boolean {
        return if ("lobby_switch" == key) {
            lobbyValue
        } else {
            arbitraryStorageManager!!
                .getStorageSetting(accountIdentifier, key, conversationToken)
                .map { arbitraryStorage: ArbitraryStorage -> arbitraryStorage.value.toBoolean() }
                .blockingGet(defaultVal)
        }
    }

    fun getString(key: String, defaultVal: String): String? {
        return if ("conversation_settings_dropdown" == key) {
            when (messageExpiration) {
                2419200 -> "expire_2419200"
                604800 -> "expire_604800"
                86400 -> "expire_86400"
                28800 -> "expire_28800"
                3600 -> "expire_3600"
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
    }

    fun setMessageExpiration(messageExpiration: Int) {
        this.messageExpiration = messageExpiration
    }

    companion object {
        private const val TAG = "DatabaseStorageModule"
    }
}
