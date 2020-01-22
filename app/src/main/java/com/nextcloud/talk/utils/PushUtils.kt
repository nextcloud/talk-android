/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.SignatureVerification
import com.nextcloud.talk.models.json.push.PushConfiguration
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.utils.hashWithAlgorithm
import com.nextcloud.talk.utils.preferences.AppPreferences
import org.greenrobot.eventbus.EventBus
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.io.*
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class PushUtils(val usersRepository: UsersRepository) : KoinComponent {
    val appPreferences: AppPreferences by inject()
    val eventBus: EventBus by inject()
    val ncApi: NcApi by inject()
    private val keysFile: File
    private val publicKeyFile: File
    private val privateKeyFile: File

    fun getMapForPushRegistrationWithServer(user: UserNgEntity): Map<String, String?>? {
        val options = mutableMapOf<String, String?>()
        val pushConfiguration = user.pushConfiguration
        options["pushToken"] = pushConfiguration?.pushToken
        options["deviceIdentifier"] = pushConfiguration?.deviceIdentifier
        options["deviceIdentifierSignature"] = pushConfiguration?.deviceIdentifierSignature
        options["userPublicKey"] = pushConfiguration?.userPublicKey

        if (options.containsValue(null)) {
            return null
        }

        return options
    }

    fun getMapForPushRegistrationWithServer(context: Context, token: String): Map<String, String> {
        val options = mutableMapOf<String, String>()

        // Let's generate a keypair if we don't have it
        generateRsa2048KeyPair()

        val pushTokenHash = token.hashWithAlgorithm("SHA-512")
        var publicKey = ""
        val devicePublicKey = readKeyFromFile(true) as PublicKey?
        devicePublicKey?.let {
            val publicKeyBytes: ByteArray = Base64.encode(it.encoded, Base64.NO_WRAP)
            publicKey = String(publicKeyBytes)
            publicKey = publicKey.replace("(.{64})".toRegex(), "$1\n")
            publicKey = "-----BEGIN PUBLIC KEY-----\n$publicKey\n-----END PUBLIC KEY-----\n"
        }

        options["format"] = "json"
        options["pushTokenHash"] = pushTokenHash
        options["devicePublicKey"] = publicKey
        options["proxyServer"] = context.resources.getString(R.string.nc_push_server_url)

        return options
    }

    fun verifySignature(
            signatureBytes: ByteArray?,
            subjectBytes: ByteArray?
    ): SignatureVerification {
        val signature: Signature?
        var pushConfiguration: PushConfiguration?
        var publicKey: PublicKey?
        val signatureVerification =
                SignatureVerification()
        signatureVerification.signatureValid = false
        val userEntities: List<UserNgEntity> = usersRepository.getUsers()
        try {
            signature = Signature.getInstance("SHA512withRSA")
            if (userEntities.isNotEmpty()) {
                for (userEntity in userEntities) {
                    pushConfiguration = userEntity.pushConfiguration
                    if (pushConfiguration?.userPublicKey != null) {
                        publicKey = readKeyFromString(
                                true, pushConfiguration.userPublicKey!!
                        ) as PublicKey?
                        signature.initVerify(publicKey)
                        signature.update(subjectBytes)
                        if (signature.verify(signatureBytes)) {
                            signatureVerification.signatureValid = true
                            signatureVerification.userEntity = userEntity
                            return signatureVerification
                        }
                    }
                }
            }
        } catch (e: NoSuchAlgorithmException) {
            Log.d(TAG, "No such algorithm")
        } catch (e: IOException) {
            Log.d(TAG, "Error while trying to parse push configuration viewState")
        } catch (e: InvalidKeyException) {
            Log.d(TAG, "Invalid key while trying to verify")
        } catch (e: SignatureException) {
            Log.d(TAG, "Signature exception while trying to verify")
        }
        return signatureVerification
    }

    private fun saveKeyToFile(
            key: Key,
            path: String?
    ): Int {
        val encoded: ByteArray? = key.encoded
        try {
            if (!File(path).exists()) {
                if (!File(path).createNewFile()) {
                    return -1
                }
            }
            FileOutputStream(path)
                    .use { keyFileOutputStream ->
                        keyFileOutputStream.write(encoded)
                        return 0
                    }
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "Failed to save key to file")
        } catch (e: IOException) {
            Log.d(TAG, "Failed to save key to file via IOException")
        }
        return -1
    }

    fun generateRsa2048KeyPair(): Int {
        if (!publicKeyFile.exists() && !privateKeyFile.exists()) {
            if (!keysFile.exists()) {
                keysFile.mkdirs()
            }
            var keyGen: KeyPairGenerator? = null
            try {
                keyGen = KeyPairGenerator.getInstance("RSA")
                keyGen.initialize(2048)
                val pair: KeyPair = keyGen.generateKeyPair()
                val statusPrivate =
                        saveKeyToFile(pair.private, privateKeyFile.absolutePath)
                val statusPublic =
                        saveKeyToFile(pair.public, publicKeyFile.absolutePath)
                return if (statusPrivate == 0 && statusPublic == 0) {
                    // all went well

                    0
                } else {
                    -2
                }
            } catch (e: NoSuchAlgorithmException) {
                Log.d(TAG, "RSA algorithm not supported")
            }
        }

        // we failed to generate the key
        else {
            // We already have the key
            return -1
        }

        return -2
    }


    private fun readKeyFromString(
            readPublicKey: Boolean,
            keyString: String
    ): Key? {
        var keyString = keyString
        keyString = if (readPublicKey) {
            keyString.replace("\\n".toRegex(), "")
                    .replace(
                            "-----BEGIN PUBLIC KEY-----",
                            ""
                    )
                    .replace("-----END PUBLIC KEY-----", "")
        } else {
            keyString.replace("\\n".toRegex(), "")
                    .replace(
                            "-----BEGIN PRIVATE KEY-----",
                            ""
                    )
                    .replace("-----END PRIVATE KEY-----", "")
        }
        var keyFactory: KeyFactory? = null
        try {
            keyFactory = KeyFactory.getInstance("RSA")
            return if (readPublicKey) {
                val keySpec = X509EncodedKeySpec(
                        Base64.decode(keyString, Base64.DEFAULT)
                )
                keyFactory.generatePublic(keySpec)
            } else {
                val keySpec =
                        PKCS8EncodedKeySpec(
                                Base64.decode(keyString, Base64.DEFAULT)
                        )
                keyFactory.generatePrivate(keySpec)
            }
        } catch (e: NoSuchAlgorithmException) {
            Log.d("TAG", "No such algorithm while reading key from string")
        } catch (e: InvalidKeySpecException) {
            Log.d("TAG", "Invalid key spec while reading key from string")
        }
        return null
    }

    fun readKeyFromFile(readPublicKey: Boolean): Key? {
        val path: String?
        path = if (readPublicKey) {
            publicKeyFile.absolutePath
        } else {
            privateKeyFile.absolutePath
        }
        try {
            FileInputStream(path)
                    .use { fileInputStream ->
                        val bytes = ByteArray(fileInputStream.available())
                        fileInputStream.read(bytes)
                        val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
                        return if (readPublicKey) {
                            val keySpec =
                                    X509EncodedKeySpec(bytes)
                            keyFactory.generatePublic(keySpec)
                        } else {
                            val keySpec =
                                    PKCS8EncodedKeySpec(bytes)
                            keyFactory.generatePrivate(keySpec)
                        }
                    }
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "Failed to find path while reading the Key")
        } catch (e: IOException) {
            Log.d(TAG, "IOException while reading the key")
        } catch (e: InvalidKeySpecException) {
            Log.d(TAG, "InvalidKeySpecException while reading the key")
        } catch (e: NoSuchAlgorithmException) {
            Log.d(TAG, "RSA algorithm not supported")
        }
        return null
    }

    companion object {
        private const val TAG = "PushUtils"
    }

    init {
        keysFile = sharedApplication!!
                .getDir("PushKeyStore", Context.MODE_PRIVATE)
        publicKeyFile = File(
                sharedApplication!!.getDir(
                        "PushKeystore",
                        Context.MODE_PRIVATE
                ), "push_key.pub"
        )
        privateKeyFile = File(
                sharedApplication!!.getDir(
                        "PushKeystore",
                        Context.MODE_PRIVATE
                ), "push_key.priv"
        )
    }
}