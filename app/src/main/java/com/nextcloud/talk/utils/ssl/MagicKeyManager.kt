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
package com.nextcloud.talk.utils.ssl

import android.content.Context
import android.security.KeyChain
import android.security.KeyChainException
import android.text.TextUtils
import android.util.Log
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.preferences.AppPreferences
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.X509KeyManager

class MagicKeyManager(private val keyManager: X509KeyManager, private val usersRepository: UsersRepository,
                      private val appPreferences: AppPreferences) : X509KeyManager {
    private val context: Context = sharedApplication!!.applicationContext
    override fun chooseClientAlias(strings: Array<String>, principals: Array<Principal>, socket: Socket): String? {
        var alias: String? = null
        val user = usersRepository.getActiveUser()
        user?.let {
            it.clientCertificate?.let {
                alias = it
            } ?: run {
                appPreferences.temporaryClientCertAlias?.let {
                    alias = it
                }
            }
        }

        return alias
    }

    override fun chooseServerAlias(s: String, principals: Array<Principal>, socket: Socket): String? {
        return null
    }

    private fun getCertificatesForAlias(alias: String?): Array<X509Certificate>? {
        if (alias != null) {
            val getCertificatesForAliasRunnable = GetCertificatesForAliasRunnable(alias)
            val getCertificatesThread = Thread(getCertificatesForAliasRunnable)
            getCertificatesThread.start()
            try {
                getCertificatesThread.join()
                return getCertificatesForAliasRunnable.certificates
            } catch (e: InterruptedException) {
                Log.e(TAG,
                        "Failed to join the thread while getting certificates: " + e.localizedMessage)
            }
        }
        return null
    }

    private fun getPrivateKeyForAlias(alias: String?): PrivateKey? {
        if (alias != null) {
            val getPrivateKeyForAliasRunnable = GetPrivateKeyForAliasRunnable(alias)
            val getPrivateKeyThread = Thread(getPrivateKeyForAliasRunnable)
            getPrivateKeyThread.start()
            try {
                getPrivateKeyThread.join()
                return getPrivateKeyForAliasRunnable.privateKey
            } catch (e: InterruptedException) {
                Log.e(TAG,
                        "Failed to join the thread while getting private key: " + e.localizedMessage)
            }
        }
        return null
    }

    override fun getCertificateChain(s: String): Array<X509Certificate>? {
        return if (ArrayList(listOf(*clientAliases)).contains(s)) {
            getCertificatesForAlias(s)!!
        } else null
    }

    private val clientAliases: Array<String>
        private get() {
            val aliases: MutableSet<String> = HashSet()
            var alias: String
            if (!TextUtils.isEmpty(appPreferences.temporaryClientCertAlias.also { alias = it })) {
                aliases.add(alias)
            }
            val userEntities: List<UserNgEntity> = usersRepository.getUsers()
            for (i in userEntities.indices) {
                userEntities[i].clientCertificate?.let {
                    aliases.add(it)
                }
            }
            return aliases.toTypedArray()
        }

    override fun getClientAliases(s: String, principals: Array<Principal>): Array<String> {
        return clientAliases
    }

    override fun getServerAliases(s: String, principals: Array<Principal>): Array<String>? {
        return null
    }

    override fun getPrivateKey(s: String): PrivateKey? {
        return if (ArrayList(listOf(*clientAliases)).contains(s)) {
            getPrivateKeyForAlias(s)!!
        } else null
    }

    private inner class GetCertificatesForAliasRunnable(private val alias: String) : Runnable {
        @Volatile
        lateinit var certificates: Array<X509Certificate>

        override fun run() {
            try {
                certificates = KeyChain.getCertificateChain(context, alias) as Array<X509Certificate>
            } catch (e: KeyChainException) {
                Log.e(TAG, e.localizedMessage)
            } catch (e: InterruptedException) {
                Log.e(TAG, e.localizedMessage)
            }
        }

    }

    private inner class GetPrivateKeyForAliasRunnable(private val alias: String) : Runnable {
        @Volatile
        var privateKey: PrivateKey? = null
            private set

        override fun run() {
            try {
                privateKey = KeyChain.getPrivateKey(context, alias)
            } catch (e: KeyChainException) {
                Log.e(TAG, e.localizedMessage)
            } catch (e: InterruptedException) {
                Log.e(TAG, e.localizedMessage)
            }
        }

    }

    companion object {
        private const val TAG = "MagicKeyManager"
    }

}