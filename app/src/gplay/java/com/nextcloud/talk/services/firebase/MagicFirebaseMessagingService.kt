/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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
import android.os.Handler
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.emoji.text.EmojiCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MagicCallActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.controllers.CallController
import com.nextcloud.talk.events.CallNotificationClick
import com.nextcloud.talk.jobs.NotificationWorker
import com.nextcloud.talk.jobs.PushRegistrationWorker
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.models.SignatureVerification
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.ParticipantsOverall
import com.nextcloud.talk.models.json.push.DecryptedPushMessage
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FROM_NOTIFICATION_START_CALL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.webrtc.Logging
import retrofit2.Retrofit
import java.io.IOException
import java.net.CookieManager
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import javax.crypto.Cipher
import javax.crypto.NoSuchPaddingException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MagicFirebaseMessagingService : FirebaseMessagingService() {
    @JvmField
    @Inject
    var appPreferences: AppPreferences? = null

    var isServiceInForeground: Boolean = false
    private var decryptedPushMessage: DecryptedPushMessage? = null
    private var signatureVerification: SignatureVerification? = null
    private var handler: Handler = Handler()

    @JvmField
    @Inject
    var retrofit: Retrofit? = null

    @JvmField
    @Inject
    var okHttpClient: OkHttpClient? = null

    @JvmField
    @Inject
    var eventBus: EventBus? = null


    override fun onCreate() {
        super.onCreate()
        Logging.d(javaClass.simpleName, "onCreate")
        sharedApplication!!.componentApplication.inject(this)
        eventBus?.register(this)
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(event: CallNotificationClick) {
        Logging.d(javaClass.simpleName, "onMessageEvent")
        isServiceInForeground = false
        stopForeground(true)
    }

    override fun onDestroy() {
        Logging.d(javaClass.simpleName, "onDestroy")
        isServiceInForeground = false
        eventBus?.unregister(this)
        stopForeground(true)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onNewToken(token: String) {
        Logging.d(javaClass.simpleName, "onNewToken")
        super.onNewToken(token)
        sharedApplication!!.componentApplication.inject(this)
        appPreferences!!.pushToken = token
        val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java).build()
        WorkManager.getInstance().enqueue(pushRegistrationWork)
    }

    @SuppressLint("LongLogTag")
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Logging.d(javaClass.simpleName, "onMessageReceived(RemoteMessage)")
        sharedApplication!!.componentApplication.inject(this)
        if (!remoteMessage.data["subject"].isNullOrEmpty() && !remoteMessage.data["signature"].isNullOrEmpty()) {
            decryptMessage(remoteMessage.data["subject"]!!, remoteMessage.data["signature"]!!)
        }
    }

    private fun decryptMessage(subject: String, signature: String) {
        Logging.d(javaClass.simpleName, "decryptMessage")

        try {
            val base64DecodedSubject = Base64.decode(subject, Base64.DEFAULT)
            val base64DecodedSignature = Base64.decode(signature, Base64.DEFAULT)
            val pushUtils = PushUtils()
            val privateKey = pushUtils.readKeyFromFile(false) as PrivateKey
            try {
                signatureVerification = pushUtils.verifySignature(base64DecodedSignature,
                        base64DecodedSubject)
                if (signatureVerification!!.signatureValid) {
                    val cipher = Cipher.getInstance("RSA/None/PKCS1Padding")
                    cipher.init(Cipher.DECRYPT_MODE, privateKey)
                    val decryptedSubject = cipher.doFinal(base64DecodedSubject)
                    decryptedPushMessage = LoganSquare.parse(String(decryptedSubject),
                            DecryptedPushMessage::class.java)
                    Logging.d(javaClass.simpleName, "    decryptedPushMessage: " + decryptedPushMessage.toString())

                    decryptedPushMessage?.apply {
                        timestamp = System.currentTimeMillis()
                        if (delete) {
                            Logging.d(javaClass.simpleName, "    delete=true")
                            NotificationUtils.cancelExistingNotificationWithId(applicationContext, signatureVerification!!.userEntity, notificationId)
                        } else if (deleteAll) {
                            Logging.d(javaClass.simpleName, "    deleteAll=true")
                            NotificationUtils.cancelAllNotificationsForAccount(applicationContext, signatureVerification!!.userEntity)
                        } else if (type == "call") {
                            Logging.d(javaClass.simpleName, "    type=call")

                            val fullScreenIntent = Intent(applicationContext, MagicCallActivity::class.java)
                            val bundle = Bundle()
                            bundle.putString(BundleKeys.KEY_ROOM_ID, decryptedPushMessage!!.id)
                            bundle.putParcelable(KEY_USER_ENTITY, signatureVerification!!.userEntity)
                            bundle.putBoolean(KEY_FROM_NOTIFICATION_START_CALL, true)
                            fullScreenIntent.putExtras(bundle)

                            fullScreenIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            val fullScreenPendingIntent = PendingIntent.getActivity(this@MagicFirebaseMessagingService, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT)

                            val audioAttributesBuilder = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            audioAttributesBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST)

                            val ringtonePreferencesString: String? = appPreferences!!.callRingtoneUri
                            val soundUri = if (TextUtils.isEmpty(ringtonePreferencesString)) {
                                Uri.parse("android.resource://" + applicationContext.packageName +
                                        "/raw/librem_by_feandesign_call")
                            } else {
                                try {
                                    val ringtoneSettings = LoganSquare.parse(ringtonePreferencesString, RingtoneSettings::class.java)
                                    ringtoneSettings.ringtoneUri
                                } catch (exception: IOException) {
                                    Uri.parse("android.resource://" + applicationContext.packageName + "/raw/librem_by_feandesign_call")
                                }
                            }

                            val notificationChannelId = NotificationUtils.getNotificationChannelId(
                                    applicationContext.resources.getString(R.string.nc_notification_channel_calls),
                                    applicationContext.resources.getString(R.string.nc_notification_channel_calls_description),
                                    true,
                                    NotificationManagerCompat.IMPORTANCE_HIGH,
                                    soundUri!!,
                                    audioAttributesBuilder.build(),
                                    null,
                                    false)

                            NotificationUtils.createNotificationChannel(applicationContext!!,
                                    notificationChannelId, applicationContext.resources
                                    .getString(R.string.nc_notification_channel_calls), applicationContext.resources
                                    .getString(R.string.nc_notification_channel_calls_description), true,
                                    NotificationManagerCompat.IMPORTANCE_HIGH, soundUri, audioAttributesBuilder.build(), null, false)

                            val uri = Uri.parse(signatureVerification!!.userEntity.baseUrl)
                            val baseUrl = uri.host

                            val notification = NotificationCompat.Builder(this@MagicFirebaseMessagingService, notificationChannelId)
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                    .setCategory(NotificationCompat.CATEGORY_CALL)
                                    .setSmallIcon(R.drawable.ic_call_black_24dp)
                                    .setSubText(baseUrl)
                                    .setShowWhen(true)
                                    .setWhen(decryptedPushMessage!!.timestamp)
                                    .setContentTitle(EmojiCompat.get().process(decryptedPushMessage!!.subject))
                                    .setAutoCancel(true)
                                    .setOngoing(true)
                                    //.setTimeoutAfter(45000L)
                                    .setContentIntent(fullScreenPendingIntent)
                                    .setFullScreenIntent(fullScreenPendingIntent, true)
                                    .setSound(soundUri)
                                    .build()
                            notification.flags = notification.flags or Notification.FLAG_INSISTENT
                            isServiceInForeground = true
                            checkIfCallIsActive(signatureVerification!!, decryptedPushMessage!!)
                            Logging.d(javaClass.simpleName, "    executing startForeground. " +
                                    "timestamp=" + decryptedPushMessage!!.timestamp.toInt() + " notification=" + notification)
                            startForeground(decryptedPushMessage!!.timestamp.toInt(), notification)
                        } else {
                            Logging.d(javaClass.simpleName, "    else....(none of delete/deleteAll/call)")

                            val messageData = Data.Builder()
                                    .putString(BundleKeys.KEY_NOTIFICATION_SUBJECT, subject)
                                    .putString(BundleKeys.KEY_NOTIFICATION_SIGNATURE, signature)
                                    .build()
                            val pushNotificationWork = OneTimeWorkRequest.Builder(NotificationWorker::class.java).setInputData(messageData).build()
                            WorkManager.getInstance().enqueue(pushNotificationWork)
                        }
                    }
                }
            } catch (e1: NoSuchAlgorithmException) {
                Log.e(javaClass.simpleName, "No proper algorithm to decrypt the message " + e1)
            } catch (e1: NoSuchPaddingException) {
                Log.e(javaClass.simpleName, "No proper padding to decrypt the message " + e1)
            } catch (e1: InvalidKeyException) {
                Log.e(javaClass.simpleName, "Invalid private key " + e1)
            }
        } catch (exception: Exception) {
            Log.e(javaClass.simpleName, "Something went very wrong " + exception)
        }
    }

    private fun checkIfCallIsActive(signatureVerification: SignatureVerification, decryptedPushMessage: DecryptedPushMessage) {
        Logging.d(javaClass.simpleName, "checkIfCallIsActive")
        Logging.d(javaClass.simpleName, "    isServiceInForeground:$isServiceInForeground")

        val ncApi = retrofit!!.newBuilder().client(okHttpClient!!.newBuilder().cookieJar(JavaNetCookieJar(CookieManager())).build()).build().create(NcApi::class.java)
        var hasParticipantsInCall = false
        var inCallOnDifferentDevice = false

        ncApi.getPeersForCall(ApiUtils.getCredentials(signatureVerification.userEntity.username, signatureVerification.userEntity.token),
                ApiUtils.getUrlForCall(signatureVerification.userEntity.baseUrl,
                        decryptedPushMessage.id))
                .takeWhile {
                    isServiceInForeground
                }
                .subscribeOn(Schedulers.io())
                .subscribe(object : Observer<ParticipantsOverall> {
                    override fun onSubscribe(d: Disposable) {
                    }

                    override fun onNext(participantsOverall: ParticipantsOverall) {
                        val participantList: List<Participant> = participantsOverall.ocs.data

                        Logging.d(javaClass.simpleName, "    participantList=$participantList")

                        hasParticipantsInCall = participantList.isNotEmpty()
                        if (!hasParticipantsInCall) {
                            for (participant in participantList) {
                                if (participant.userId == signatureVerification.userEntity.userId) {
                                    inCallOnDifferentDevice = true
                                    break
                                }
                            }
                        }

                        if (!hasParticipantsInCall || inCallOnDifferentDevice) {
                            stopForeground(true)
                            handler.removeCallbacksAndMessages(null)
                        } else if (isServiceInForeground) {
                            handler.postDelayed({
                                checkIfCallIsActive(signatureVerification, decryptedPushMessage)
                            }, 5000)
                        }
                    }

                    override fun onError(e: Throwable) {
                        Logging.d(javaClass.simpleName, "   checkIfCallIsActive-onError")
                    }
                    override fun onComplete() {
                        Logging.d(javaClass.simpleName, "   checkIfCallIsActive-onComplete")
                    }
                })
    }
}