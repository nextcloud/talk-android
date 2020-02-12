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
package com.nextcloud.talk.services.firebase

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.emoji.text.EmojiCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bluelinelabs.logansquare.LoganSquare
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MagicCallActivity
import com.nextcloud.talk.jobs.MessageNotificationWorker
import com.nextcloud.talk.jobs.NotificationWorker
import com.nextcloud.talk.models.SignatureVerification
import com.nextcloud.talk.models.json.push.DecryptedPushMessage
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.NotificationUtils.cancelAllNotificationsForAccount
import com.nextcloud.talk.utils.NotificationUtils.cancelExistingNotificationWithId
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FROM_NOTIFICATION_START_CALL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.preferences.AppPreferences
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.greenrobot.eventbus.EventBus
import org.koin.core.KoinComponent
import org.koin.core.inject
import retrofit2.Retrofit
import java.net.CookieManager
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.util.*
import javax.crypto.Cipher
import javax.crypto.NoSuchPaddingException

class MagicFirebaseMessagingService : FirebaseMessagingService(), KoinComponent {
    val appPreferences: AppPreferences by inject()
    val retrofit: Retrofit by inject()
    val okHttpClient: OkHttpClient by inject()
    val eventBus: EventBus by inject()
    val usersRepository: UsersRepository by inject()

    private var isServiceInForeground: Boolean = false

    override fun onCreate() {
        super.onCreate()
        eventBus.register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        eventBus.unregister(this)
        isServiceInForeground = false
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        appPreferences.pushToken = token
    }

    @SuppressLint("LongLogTag")
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.data.let {
            decryptMessage(it["subject"]!!, it["signature"]!!)
        }
    }

    private fun decryptMessage(subject: String, signature: String) {
        val signatureVerification: SignatureVerification
        val decryptedPushMessage: DecryptedPushMessage

        try {
            val base64DecodedSubject = Base64.decode(subject, Base64.DEFAULT)
            val base64DecodedSignature = Base64.decode(signature, Base64.DEFAULT)
            val pushUtils = PushUtils(usersRepository)
            val privateKey = pushUtils.readKeyFromFile(false) as PrivateKey
            try {
                signatureVerification = pushUtils.verifySignature(base64DecodedSignature, base64DecodedSubject)
                if (signatureVerification.signatureValid) {
                    val cipher = Cipher.getInstance("RSA/None/PKCS1Padding")
                    cipher.init(Cipher.DECRYPT_MODE, privateKey)
                    val decryptedSubject = cipher.doFinal(base64DecodedSubject)
                    decryptedPushMessage = LoganSquare.parse(String(decryptedSubject), DecryptedPushMessage::class.java)
                    decryptedPushMessage.apply {
                        val timestamp = decryptedPushMessage.timestamp
                        when {
                            delete -> {
                                cancelExistingNotificationWithId(applicationContext, signatureVerification.userEntity, notificationId!!)
                            }
                            deleteAll -> {
                                cancelAllNotificationsForAccount(applicationContext, signatureVerification.userEntity)
                            }
                            type == "call" -> {
                                val fullScreenIntent = Intent(applicationContext, MagicCallActivity::class.java)
                                val bundle = Bundle()
                                bundle.putString(BundleKeys.KEY_ROOM_ID, decryptedPushMessage.id)
                                bundle.putParcelable(KEY_USER_ENTITY, signatureVerification.userEntity)
                                bundle.putBoolean(KEY_FROM_NOTIFICATION_START_CALL, true)
                                fullScreenIntent.putExtras(bundle)

                                fullScreenIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                val fullScreenPendingIntent = PendingIntent.getActivity(this@MagicFirebaseMessagingService, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT)

                                val audioAttributesBuilder = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                audioAttributesBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST)

                                val soundUri = NotificationUtils.getCallSoundUri(applicationContext, appPreferences)
                                val vibrationEffect = NotificationUtils.getVibrationEffect(appPreferences)

                                val notificationChannelId = NotificationUtils.getNotificationChannelId(applicationContext, applicationContext.resources
                                        .getString(R.string.nc_notification_channel_calls), applicationContext.resources
                                        .getString(R.string.nc_notification_channel_calls_description), true,
                                        NotificationManagerCompat.IMPORTANCE_HIGH, soundUri!!,
                                        audioAttributesBuilder.build(), vibrationEffect, false, null)

                                val userBaseUrl = Uri.parse(signatureVerification.userEntity.baseUrl).toString()

                                val notificationBuilder = NotificationCompat.Builder(this@MagicFirebaseMessagingService, notificationChannelId)
                                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                                        .setCategory(NotificationCompat.CATEGORY_CALL)
                                        .setSmallIcon(R.drawable.ic_call_black_24dp)
                                        .setSubText(userBaseUrl)
                                        .setShowWhen(true)
                                        .setWhen(decryptedPushMessage.timestamp)
                                        .setContentTitle(EmojiCompat.get().process(decryptedPushMessage.subject.toString()))
                                        .setAutoCancel(true)
                                        .setOngoing(true)
                                        //.setTimeoutAfter(45000L)
                                        .setFullScreenIntent(fullScreenPendingIntent, true)
                                        .setSound(NotificationUtils.getCallSoundUri(applicationContext, appPreferences))

                                if (vibrationEffect != null) {
                                    notificationBuilder.setVibrate(vibrationEffect)
                                }

                                val notification = notificationBuilder.build()
                                notification.flags = notification.flags or Notification.FLAG_INSISTENT
                                isServiceInForeground = true
                                checkIfCallIsActive(signatureVerification, decryptedPushMessage)
                                startForeground(decryptedPushMessage.timestamp.toInt(), notification)
                            }
                            else -> {
                                val messageData = Data.Builder()
                                        .putString(BundleKeys.KEY_DECRYPTED_PUSH_MESSAGE, LoganSquare.serialize(decryptedPushMessage))
                                        .putString(BundleKeys.KEY_SIGNATURE_VERIFICATION, LoganSquare.serialize(signatureVerification))
                                        .build()
                                val pushNotificationWork = OneTimeWorkRequest.Builder(MessageNotificationWorker::class.java).setInputData(messageData).build()
                                WorkManager.getInstance().enqueue(pushNotificationWork)
                            }
                        }
                    }
                }
            } catch (e1: NoSuchAlgorithmException) {
                Log.d(NotificationWorker.TAG, "No proper algorithm to decrypt the message " + e1.localizedMessage)
            } catch (e1: NoSuchPaddingException) {
                Log.d(NotificationWorker.TAG, "No proper padding to decrypt the message " + e1.localizedMessage)
            } catch (e1: InvalidKeyException) {
                Log.d(NotificationWorker.TAG, "Invalid private key " + e1.localizedMessage)
            }
        } catch (exception: Exception) {
            Log.d(NotificationWorker.TAG, "Something went very wrong " + exception.localizedMessage)
        }
    }

    private fun checkIfCallIsActive(signatureVerification: SignatureVerification, decryptedPushMessage: DecryptedPushMessage) {
        /*val ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(JavaNetCookieJar(CookieManager())).build()).build().create(NcApi::class.java)
        var hasParticipantsInCall = false
        var inCallOnDifferentDevice = false

        ncApi.getPeersForCall(ApiUtils.getCredentials(signatureVerification.userEntity.username, signatureVerification.userEntity.token),
                ApiUtils.getUrlForParticipants(signatureVerification.userEntity.baseUrl,
                        decryptedPushMessage.id))
                .subscribeOn(Schedulers.io())
                .subscribe(object : Observer<ParticipantsOverall> {
                    override fun onSubscribe(d: Disposable) {
                    }

                    override fun onNext(participantsOverall: ParticipantsOverall) {
                        val participantList: List<Participant> = participantsOverall.ocs.data
                        for (participant in participantList) {
                            if (participant.participantFlags != Participant.ParticipantFlags.NOT_IN_CALL) {
                                hasParticipantsInCall = true
                                if (participant.userId == signatureVerification.userEntity.userId) {
                                    inCallOnDifferentDevice = true
                                    break
                                }
                            }
                        }

                        if (!hasParticipantsInCall || inCallOnDifferentDevice) {
                            stopForeground(true)
                        } else if (isServiceInForeground) {
                            checkIfCallIsActive(signatureVerification, decryptedPushMessage)
                        }
                    }

                    override fun onError(e: Throwable) {}
                    override fun onComplete() {
                    }
                })*/

    }
}