/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.utils.preferences.preferencestorage

import android.os.Bundle
import android.text.TextUtils
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.interfaces.ConversationInfoInterface
import com.nextcloud.talk.models.database.ArbitraryStorageEntity
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.arbitrarystorage.ArbitraryStorageUtils
import com.yarolegovich.mp.io.StorageModule
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.koin.core.KoinComponent
import org.koin.core.inject

class DatabaseStorageModule(
        private val conversationUser: UserNgEntity,
        private val conversationToken: String,
        private val conversationInfoInterface: ConversationInfoInterface
) : StorageModule, KoinComponent {
    val arbitraryStorageUtils: ArbitraryStorageUtils by inject()
    val ncApi: NcApi by inject()
    private val accountIdentifier: Long
    private var lobbyValue = false
    private var favoriteConversationValue = false
    private var allowGuestsValue = false
    private var hasPassword: Boolean? = null
    private var conversationNameValue: String? = null
    private var messageNotificationLevel: String? = null
    override fun saveBoolean(
            key: String,
            value: Boolean
    ) {
        if (key != "conversation_lobby" && key != "allow_guests" && key != "favorite_conversation"
        ) {
            arbitraryStorageUtils.storeStorageSetting(
                    accountIdentifier, key, value.toString(),
                    conversationToken
            )
        } else {
            when (key) {
                "conversation_lobby" -> lobbyValue = value
                "allow_guests" -> allowGuestsValue = value
                "favorite_conversation" -> favoriteConversationValue = value
                else -> {
                }
            }
        }
    }

    override fun saveString(
            key: String,
            value: String
    ) {
        if (key != "message_notification_level"
                && key != "conversation_name"
                && key != "conversation_password"
        ) {
            arbitraryStorageUtils.storeStorageSetting(accountIdentifier, key, value, conversationToken)
        } else {
            if (key == "message_notification_level") {
                if (conversationUser.hasSpreedFeatureCapability("notification-levels")) {
                    if (!TextUtils.isEmpty(
                                    messageNotificationLevel
                            ) && messageNotificationLevel != value
                    ) {
                        val intValue: Int
                        intValue = when (value) {
                            "never" -> 3
                            "mention" -> 2
                            "always" -> 1
                            else -> 0
                        }
                        ncApi.setNotificationLevel(
                                        ApiUtils.getCredentials(
                                                conversationUser.username,
                                                conversationUser.token
                                        ),
                                        ApiUtils.getUrlForSettingNotificationlevel(
                                                conversationUser.baseUrl,
                                                conversationToken
                                        ),
                                        intValue
                                )
                                .subscribeOn(Schedulers.io())
                                .subscribe(object : Observer<GenericOverall> {
                                    override fun onSubscribe(d: Disposable) {}
                                    override fun onNext(genericOverall: GenericOverall) {
                                        messageNotificationLevel = value
                                    }

                                    override fun onError(e: Throwable) {}
                                    override fun onComplete() {}
                                })
                    } else {
                        messageNotificationLevel = value
                    }
                }
            } else if (key == "conversation_password") {
                if (hasPassword != null) {
                    ncApi.setPassword(
                                    ApiUtils.getCredentials(
                                            conversationUser.username,
                                            conversationUser.token
                                    ),
                                    ApiUtils.getUrlForPassword(
                                            conversationUser.baseUrl,
                                            conversationToken
                                    ), value
                            )
                            .subscribeOn(Schedulers.io())
                            .subscribe(object : Observer<GenericOverall> {
                                override fun onSubscribe(d: Disposable) {}
                                override fun onNext(genericOverall: GenericOverall) {
                                    hasPassword = !TextUtils.isEmpty(value)
                                    conversationInfoInterface.passwordSet(TextUtils.isEmpty(value))
                                }

                                override fun onError(e: Throwable) {}
                                override fun onComplete() {}
                            })
                } else {
                    hasPassword = value.toBoolean()
                }
            } else if (key == "conversation_name") {
                if (!TextUtils.isEmpty(
                                conversationNameValue
                        ) && conversationNameValue != value
                ) {
                    ncApi.renameRoom(
                                    ApiUtils.getCredentials(
                                            conversationUser.username,
                                            conversationUser.token
                                    ), ApiUtils.getRoom(
                                    conversationUser.baseUrl,
                                    conversationToken
                            ), value
                            )
                            .subscribeOn(Schedulers.io())
                            .subscribe(object : Observer<GenericOverall> {
                                override fun onSubscribe(d: Disposable) {}
                                override fun onNext(genericOverall: GenericOverall) {
                                    conversationNameValue = value
                                    conversationInfoInterface.conversationNameSet(value)
                                }

                                override fun onError(e: Throwable) {}
                                override fun onComplete() {}
                            })
                } else {
                    conversationNameValue = value
                }
            }
        }
    }

    override fun saveInt(
            key: String,
            value: Int
    ) {
        arbitraryStorageUtils.storeStorageSetting(
                accountIdentifier, key, Integer.toString(value),
                conversationToken
        )
    }

    override fun saveStringSet(
            key: String,
            value: Set<String>
    ) {
    }

    override fun getBoolean(
            key: String,
            defaultVal: Boolean
    ): Boolean {
        return if (key == "conversation_lobby") {
            lobbyValue
        } else if (key == "allow_guests") {
            allowGuestsValue
        } else if (key == "favorite_conversation") {
            favoriteConversationValue
        } else {
            val valueFromDb: ArbitraryStorageEntity? =
                    arbitraryStorageUtils.getStorageSetting(accountIdentifier, key, conversationToken)
            if (valueFromDb == null) {
                defaultVal
            } else {
                valueFromDb.value!!.toBoolean()
            }
        }
    }

    override fun getString(
            key: String,
            defaultVal: String?
    ): String? {
        if (key != "message_notification_level"
                && key != "conversation_name"
                && key != "conversation_password"
        ) {
            val valueFromDb: ArbitraryStorageEntity? =
                    arbitraryStorageUtils.getStorageSetting(accountIdentifier, key, conversationToken)
            return if (valueFromDb == null) {
                defaultVal
            } else {
                valueFromDb.value
            }
        } else if (key == "message_notification_level") {
            return messageNotificationLevel
        } else if (key == "conversation_name") {
            return conversationNameValue
        } else if (key == "conversation_password") {
            return ""
        }
        return ""
    }

    override fun getInt(
            key: String,
            defaultVal: Int
    ): Int {
        val valueFromDb: ArbitraryStorageEntity? =
                arbitraryStorageUtils.getStorageSetting(accountIdentifier, key, conversationToken)
        return if (valueFromDb == null) {
            defaultVal
        } else {
            Integer.parseInt(valueFromDb.value)
        }
    }

    override fun getStringSet(
            key: String,
            defaultVal: Set<String>
    ): Set<String>? {
        return null
    }

    override fun onSaveInstanceState(outState: Bundle) {}
    override fun onRestoreInstanceState(savedState: Bundle) {}

    init {
        accountIdentifier = conversationUser.id
    }
}