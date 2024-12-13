/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.models.SignatureVerification
import com.nextcloud.talk.models.json.push.PushConfigurationState
import com.nextcloud.talk.models.json.push.PushRegistrationOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.UserIdUtils.getIdForUser
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observer
import io.reactivex.SingleObserver
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PushUtils {
    @JvmField
    @Inject
    var userManager: UserManager? = null

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var arbitraryStorageManager: ArbitraryStorageManager

    @JvmField
    @Inject
    var eventBus: EventBus? = null
    private val publicKeyFile: File
    private val privateKeyFile: File
    private val proxyServer: String

    init {
        sharedApplication!!.componentApplication.inject(this)
        val keyPath = sharedApplication!!
            .getDir("PushKeystore", Context.MODE_PRIVATE)
            .absolutePath
        publicKeyFile = File(keyPath, "push_key.pub")
        privateKeyFile = File(keyPath, "push_key.priv")
        proxyServer = sharedApplication!!
            .resources.getString(R.string.nc_push_server_url)
    }

    fun verifySignature(signatureBytes: ByteArray?, subjectBytes: ByteArray?): SignatureVerification {
        val signatureVerification = SignatureVerification()
        signatureVerification.signatureValid = false
        val users = userManager!!.users.blockingGet()
        try {
            val signature = Signature.getInstance("SHA512withRSA")
            if (users != null && users.size > 0) {
                var publicKey: PublicKey?
                for (user in users) {
                    if (user.pushConfigurationState != null) {
                        publicKey = readKeyFromString(
                            true,
                            user.pushConfigurationState!!.userPublicKey
                        ) as PublicKey?
                        signature.initVerify(publicKey)
                        signature.update(subjectBytes)
                        if (signature.verify(signatureBytes)) {
                            signatureVerification.signatureValid = true
                            signatureVerification.user = user
                            return signatureVerification
                        }
                    }
                }
            }
        } catch (e: NoSuchAlgorithmException) {
            Log.d(TAG, "No such algorithm")
        } catch (e: InvalidKeyException) {
            Log.d(TAG, "Invalid key while trying to verify")
        } catch (e: SignatureException) {
            Log.d(TAG, "Signature exception while trying to verify")
        }
        return signatureVerification
    }

    private fun saveKeyToFile(key: Key, path: String): Int {
        val encoded = key.encoded
        try {
            return if (!File(path).exists() && !File(path).createNewFile()) {
                -1
            } else {
                FileOutputStream(path).use { keyFileOutputStream ->
                    keyFileOutputStream.write(encoded)
                    0
                }
            }
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "Failed to save key to file")
        } catch (e: IOException) {
            Log.d(TAG, "Failed to save key to file via IOException")
        }
        return -1
    }

    private fun generateSHA512Hash(pushToken: String): String {
        var messageDigest: MessageDigest? = null
        try {
            messageDigest = MessageDigest.getInstance("SHA-512")
            messageDigest.update(pushToken.toByteArray())
            return bytesToHex(messageDigest.digest())
        } catch (e: NoSuchAlgorithmException) {
            Log.d(TAG, "SHA-512 algorithm not supported")
        }
        return ""
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val result = StringBuilder()
        for (individualByte in bytes) {
            result.append(
                ((individualByte.toInt() and BYTES_TO_HEX_SUFFIX) + BYTES_TO_HEX_SUFFIX_SUFFIX)
                    .toString(BYTES_TO_HEX_RADIX)
                    .substring(1)
            )
        }
        return result.toString()
    }

    fun generateRsa2048KeyPair(): Int {
        if (!publicKeyFile.exists() && !privateKeyFile.exists()) {
            var keyGen: KeyPairGenerator? = null
            try {
                keyGen = KeyPairGenerator.getInstance("RSA")
                keyGen.initialize(RSA_KEY_SIZE)
                val pair = keyGen.generateKeyPair()
                val statusPrivate = saveKeyToFile(pair.private, privateKeyFile.absolutePath)
                val statusPublic = saveKeyToFile(pair.public, publicKeyFile.absolutePath)
                return if (statusPrivate == 0 && statusPublic == 0) {
                    // all went well
                    RETURN_CODE_KEY_GENERATION_SUCCESSFUL
                } else {
                    RETURN_CODE_KEY_GENERATION_FAILED
                }
            } catch (e: NoSuchAlgorithmException) {
                Log.d(TAG, "RSA algorithm not supported")
            }
        } else {
            // We already have the key
            return RETURN_CODE_KEY_ALREADY_EXISTS
        }

        // we failed to generate the key
        return RETURN_CODE_KEY_GENERATION_FAILED
    }

    fun pushRegistrationToServer(ncApi: NcApi) {
        val pushToken = appPreferences.pushToken

        if (pushToken.isNotEmpty()) {
            Log.d(TAG, "pushRegistrationToServer will be done with pushToken: $pushToken")
            val pushTokenHash = generateSHA512Hash(pushToken).lowercase(Locale.getDefault())
            val devicePublicKey = readKeyFromFile(true) as PublicKey?
            if (devicePublicKey != null) {
                val devicePublicKeyBytes = Base64.encode(devicePublicKey.encoded, Base64.NO_WRAP)
                var devicePublicKeyBase64 = String(devicePublicKeyBytes)
                devicePublicKeyBase64 = devicePublicKeyBase64.replace("(.{64})".toRegex(), "$1\n")
                devicePublicKeyBase64 = "-----BEGIN PUBLIC KEY-----\n$devicePublicKeyBase64\n-----END PUBLIC KEY-----"

                val users = userManager!!.users.blockingGet()
                for (user in users) {
                    if (!user.scheduledForDeletion) {
                        val nextcloudRegisterPushMap: MutableMap<String, String> = HashMap()
                        nextcloudRegisterPushMap["format"] = "json"
                        nextcloudRegisterPushMap["pushTokenHash"] = pushTokenHash
                        nextcloudRegisterPushMap["devicePublicKey"] = devicePublicKeyBase64
                        nextcloudRegisterPushMap["proxyServer"] = proxyServer
                        registerDeviceWithNextcloud(ncApi, nextcloudRegisterPushMap, pushToken, user)
                    }
                }
            }
        } else {
            Log.e(TAG, "push token was empty when trying to register at server")
        }
    }

    private fun registerDeviceWithNextcloud(
        ncApi: NcApi,
        nextcloudRegisterPushMap: Map<String, String>,
        token: String,
        user: User
    ) {
        val credentials = ApiUtils.getCredentials(user.username, user.token)
        ncApi.registerDeviceForNotificationsWithNextcloud(
            credentials,
            ApiUtils.getUrlNextcloudPush(user.baseUrl!!),
            nextcloudRegisterPushMap
        )
            .subscribe(object : Observer<PushRegistrationOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(pushRegistrationOverall: PushRegistrationOverall) {
                    arbitraryStorageManager.storeStorageSetting(
                        getIdForUser(user),
                        LATEST_PUSH_REGISTRATION_AT_SERVER,
                        System.currentTimeMillis().toString(),
                        ""
                    )

                    Log.d(TAG, "pushTokenHash successfully registered at nextcloud server.")
                    val proxyMap: MutableMap<String, String?> = HashMap()
                    proxyMap["pushToken"] = token
                    proxyMap["deviceIdentifier"] = pushRegistrationOverall.ocs!!.data!!.deviceIdentifier
                    proxyMap["deviceIdentifierSignature"] = pushRegistrationOverall.ocs!!.data!!.signature
                    proxyMap["userPublicKey"] = pushRegistrationOverall.ocs!!.data!!.publicKey
                    registerDeviceWithPushProxy(ncApi, proxyMap, user)
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to register device with nextcloud", e)
                    eventBus!!.post(EventStatus(user.id!!, EventStatus.EventType.PUSH_REGISTRATION, false))
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun registerDeviceWithPushProxy(ncApi: NcApi, proxyMap: Map<String, String?>, user: User) {
        ncApi.registerDeviceForNotificationsWithPushProxy(ApiUtils.getUrlPushProxy(), proxyMap)
            .subscribeOn(Schedulers.io())
            .subscribe(object : Observer<Unit> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(t: Unit) {
                    try {
                        arbitraryStorageManager.storeStorageSetting(
                            getIdForUser(user),
                            LATEST_PUSH_REGISTRATION_AT_PUSH_PROXY,
                            System.currentTimeMillis().toString(),
                            ""
                        )

                        Log.d(TAG, "pushToken successfully registered at pushproxy.")
                        updatePushStateForUser(proxyMap, user)
                    } catch (e: IOException) {
                        Log.e(TAG, "IOException while updating user", e)
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to register device with pushproxy", e)
                    eventBus!!.post(EventStatus(user.id!!, EventStatus.EventType.PUSH_REGISTRATION, false))
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    @Throws(IOException::class)
    private fun updatePushStateForUser(proxyMap: Map<String, String?>, user: User) {
        val pushConfigurationState = PushConfigurationState()
        pushConfigurationState.pushToken = proxyMap["pushToken"]
        pushConfigurationState.deviceIdentifier = proxyMap["deviceIdentifier"]
        pushConfigurationState.deviceIdentifierSignature = proxyMap["deviceIdentifierSignature"]
        pushConfigurationState.userPublicKey = proxyMap["userPublicKey"]
        pushConfigurationState.usesRegularPass = java.lang.Boolean.FALSE
        if (user.id != null) {
            userManager!!.updatePushState(user.id!!, pushConfigurationState).subscribe(object : SingleObserver<Int?> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onSuccess(integer: Int) {
                    eventBus!!.post(
                        EventStatus(
                            getIdForUser(user),
                            EventStatus.EventType.PUSH_REGISTRATION,
                            true
                        )
                    )
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "update push state for user failed", e)
                    eventBus!!.post(
                        EventStatus(
                            getIdForUser(user),
                            EventStatus.EventType.PUSH_REGISTRATION,
                            false
                        )
                    )
                }
            })
        } else {
            Log.e(TAG, "failed to update updatePushStateForUser. user.getId() was null")
        }
    }

    private fun readKeyFromString(readPublicKey: Boolean, keyString: String?): Key? {
        var keyString = keyString
        keyString = if (readPublicKey) {
            keyString!!.replace("\\n".toRegex(), "").replace(
                "-----BEGIN PUBLIC KEY-----",
                ""
            ).replace("-----END PUBLIC KEY-----", "")
        } else {
            keyString!!.replace("\\n".toRegex(), "").replace(
                "-----BEGIN PRIVATE KEY-----",
                ""
            ).replace("-----END PRIVATE KEY-----", "")
        }
        var keyFactory: KeyFactory? = null
        try {
            keyFactory = KeyFactory.getInstance("RSA")
            return if (readPublicKey) {
                val keySpec = X509EncodedKeySpec(Base64.decode(keyString, Base64.DEFAULT))
                keyFactory.generatePublic(keySpec)
            } else {
                val keySpec = PKCS8EncodedKeySpec(Base64.decode(keyString, Base64.DEFAULT))
                keyFactory.generatePrivate(keySpec)
            }
        } catch (e: NoSuchAlgorithmException) {
            Log.d(TAG, "No such algorithm while reading key from string")
        } catch (e: InvalidKeySpecException) {
            Log.d(TAG, "Invalid key spec while reading key from string")
        }
        return null
    }

    fun readKeyFromFile(readPublicKey: Boolean): Key? {
        val path: String
        path = if (readPublicKey) {
            publicKeyFile.absolutePath
        } else {
            privateKeyFile.absolutePath
        }
        try {
            FileInputStream(path).use { fileInputStream ->
                val bytes = ByteArray(fileInputStream.available())
                fileInputStream.read(bytes)
                val keyFactory = KeyFactory.getInstance("RSA")
                return if (readPublicKey) {
                    val keySpec = X509EncodedKeySpec(bytes)
                    keyFactory.generatePublic(keySpec)
                } else {
                    val keySpec = PKCS8EncodedKeySpec(bytes)
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
        private const val RSA_KEY_SIZE: Int = 2048
        private const val RETURN_CODE_KEY_GENERATION_SUCCESSFUL: Int = 0
        private const val RETURN_CODE_KEY_ALREADY_EXISTS: Int = -1
        private const val RETURN_CODE_KEY_GENERATION_FAILED: Int = -2
        private const val BYTES_TO_HEX_RADIX: Int = 16
        private const val BYTES_TO_HEX_SUFFIX = 0xff
        private const val BYTES_TO_HEX_SUFFIX_SUFFIX = 0x100
        const val LATEST_PUSH_REGISTRATION_AT_SERVER: String = "LATEST_PUSH_REGISTRATION_AT_SERVER"
        const val LATEST_PUSH_REGISTRATION_AT_PUSH_PROXY: String = "LATEST_PUSH_REGISTRATION_AT_PUSH_PROXY"
    }
}
