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
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R.string
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.events.EventStatus.EventType.PUSH_REGISTRATION
import com.nextcloud.talk.models.SignatureVerification
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.push.PushConfigurationState
import com.nextcloud.talk.models.json.push.PushRegistrationOverall
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observer
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
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.HashMap
import javax.inject.Inject
import kotlin.experimental.and

@AutoInjector(NextcloudTalkApplication::class)
class PushUtils(val usersRepository: UsersRepository) {
  @JvmField
  @Inject
  var userUtils: UserUtils? = null
  @JvmField
  @Inject
  var appPreferences: AppPreferences? = null
  @JvmField
  @Inject
  var eventBus: EventBus? = null
  @JvmField
  @Inject
  var ncApi: NcApi? = null
  private val keysFile: File
  private val publicKeyFile: File
  private val privateKeyFile: File
  private val proxyServer: String
  fun verifySignature(
    signatureBytes: ByteArray?,
    subjectBytes: ByteArray?
  ): SignatureVerification {
    val signature: Signature?
    var pushConfigurationState: PushConfigurationState?
    var publicKey: PublicKey?
    val signatureVerification =
      SignatureVerification()
    signatureVerification.signatureValid = false
    val userEntities: List<UserNgEntity> = usersRepository.getUsers()
    try {
      signature = Signature.getInstance("SHA512withRSA")
      if (userEntities.size > 0) {
        for (userEntity in userEntities) {
          pushConfigurationState = userEntity.pushConfiguration
          if (pushConfigurationState?.userPublicKey != null) {
            publicKey = readKeyFromString(
                true, pushConfigurationState.userPublicKey!!
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
          ((individualByte and 0xff.toByte()) + 0x100).toString(16)
              .substring(1)
      )
    }
    return result.toString()
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

  fun pushRegistrationToServer() {
    val token: String = appPreferences!!.pushToken
    if (!TextUtils.isEmpty(token)) {
      var credentials: String
      val pushTokenHash = generateSHA512Hash(token).toLowerCase()
      val devicePublicKey =
        readKeyFromFile(true) as PublicKey?
      if (devicePublicKey != null) {
        val publicKeyBytes: ByteArray? =
          Base64.encode(devicePublicKey.encoded, Base64.NO_WRAP)
        var publicKey = String(publicKeyBytes!!)
        publicKey = publicKey.replace("(.{64})".toRegex(), "$1\n")
        publicKey = "-----BEGIN PUBLIC KEY-----\n$publicKey\n-----END PUBLIC KEY-----\n"
        if (userUtils!!.anyUserExists()) {
          var accountPushData: PushConfigurationState? = null
          for (userEntityObject in usersRepository.getUsers()) {
            val userEntity = userEntityObject
            accountPushData = userEntity.pushConfiguration
            if (accountPushData == null || accountPushData.pushToken != token) {
              val queryMap: MutableMap<String, String> =
                HashMap()
              queryMap["format"] = "json"
              queryMap["pushTokenHash"] = pushTokenHash
              queryMap["devicePublicKey"] = publicKey
              queryMap["proxyServer"] = proxyServer
              credentials = ApiUtils.getCredentials(
                  userEntity.username, userEntity.token
              )
              val finalCredentials = credentials
              ncApi!!.registerDeviceForNotificationsWithNextcloud(
                  credentials,
                  ApiUtils.getUrlNextcloudPush(userEntity.baseUrl),
                  queryMap
              )
                  .subscribe(object : Observer<PushRegistrationOverall> {
                    override fun onSubscribe(d: Disposable) {}
                    override fun onNext(pushRegistrationOverall: PushRegistrationOverall) {
                      val proxyMap: MutableMap<String, String> =
                        HashMap()
                      proxyMap["pushToken"] = token
                      proxyMap["deviceIdentifier"] =
                        pushRegistrationOverall.ocs.data.deviceIdentifier
                      proxyMap["deviceIdentifierSignature"] = pushRegistrationOverall.ocs
                          .data.signature
                      proxyMap["userPublicKey"] = pushRegistrationOverall.ocs
                          .data.publicKey
                      ncApi!!.registerDeviceForNotificationsWithProxy(
                          ApiUtils.getUrlPushProxy(), proxyMap
                      )
                          .subscribeOn(Schedulers.io())
                          .subscribe(object : Observer<Void> {
                            override fun onSubscribe(d: Disposable) {}
                            override fun onNext(aVoid: Void) {
                              val pushConfigurationState =
                                PushConfigurationState()
                              pushConfigurationState.pushToken = token
                              pushConfigurationState.deviceIdentifier = pushRegistrationOverall
                                  .ocs.data.deviceIdentifier
                              pushConfigurationState.deviceIdentifierSignature =
                                pushRegistrationOverall.ocs.data.signature
                              pushConfigurationState.userPublicKey = pushRegistrationOverall.ocs
                                  .data.publicKey
                              pushConfigurationState.usesRegularPass = false
                              try {
                                userUtils!!.createOrUpdateUser(
                                    null,
                                    null, null,
                                    userEntity.displayName,
                                    LoganSquare.serialize(
                                        pushConfigurationState
                                    ), null,
                                    null, userEntity.id, null, null, null
                                )
                                    .subscribe(object : Observer<UserEntity> {
                                      override fun onSubscribe(d: Disposable) {}
                                      override fun onNext(userEntity: UserEntity) {
                                        eventBus!!.post(
                                            EventStatus(
                                                userEntity.id,
                                                PUSH_REGISTRATION,
                                                true
                                            )
                                        )
                                      }

                                      override fun onError(e: Throwable) {
                                        eventBus!!.post(
                                            EventStatus(
                                                userEntity.id,
                                                PUSH_REGISTRATION, false
                                            )
                                        )
                                      }

                                      override fun onComplete() {}
                                    })
                              } catch (e: IOException) {
                                Log.e(TAG, "IOException while updating user")
                              }
                            }

                            override fun onError(e: Throwable) {
                              eventBus!!.post(
                                  EventStatus(
                                      userEntity.id,
                                      PUSH_REGISTRATION,
                                      false
                                  )
                              )
                            }

                            override fun onComplete() {}
                          })
                    }

                    override fun onError(e: Throwable) {
                      eventBus!!.post(
                          EventStatus(
                              userEntity.id,
                              PUSH_REGISTRATION,
                              false
                          )
                      )
                    }

                    override fun onComplete() {}
                  })
            }
          }
        }
      }
    }
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
    sharedApplication!!
        .componentApplication
        .inject(this)
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
    proxyServer =
      sharedApplication!!.resources
          .getString(string.nc_push_server_url)
  }
}