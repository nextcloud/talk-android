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
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Bundle
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
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.jobs.NotificationWorker
import com.nextcloud.talk.jobs.PushRegistrationWorker
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.models.SignatureVerification
import com.nextcloud.talk.models.json.push.DecryptedPushMessage
import com.nextcloud.talk.utils.NotificationUtils.NOTIFICATION_CHANNEL_CALLS_V3
import com.nextcloud.talk.utils.NotificationUtils.cancelAllNotificationsForAccount
import com.nextcloud.talk.utils.NotificationUtils.cancelExistingNotificationWithId
import com.nextcloud.talk.utils.NotificationUtils.createNotificationChannel
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FROM_NOTIFICATION_START_CALL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.preferences.AppPreferences
import java.io.IOException
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

    private var decryptedPushMessage: DecryptedPushMessage? = null
    private var signatureVerification: SignatureVerification? = null

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        sharedApplication!!.componentApplication.inject(this)
        appPreferences!!.pushToken = token
        val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java).build()
        WorkManager.getInstance().enqueue(pushRegistrationWork)
    }

    @SuppressLint("LongLogTag")
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        sharedApplication!!.componentApplication.inject(this)
        if (!remoteMessage.data["subject"].isNullOrEmpty() && !remoteMessage.data["signature"].isNullOrEmpty()) {
            decryptMessage(remoteMessage.data["subject"]!!, remoteMessage.data["signature"]!!)
        }
    }

    private fun decryptMessage(subject: String, signature: String) {
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
                    decryptedPushMessage?.apply {
                        timestamp = System.currentTimeMillis()
                        if (delete) {
                            cancelExistingNotificationWithId(applicationContext, signatureVerification!!.userEntity, notificationId)
                        } else if (deleteAll) {
                            cancelAllNotificationsForAccount(applicationContext, signatureVerification!!.userEntity)
                        } else if (type == "call") {
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

                            createNotificationChannel(applicationContext!!,
                                    NOTIFICATION_CHANNEL_CALLS_V3, applicationContext.resources
                                    .getString(R.string.nc_notification_channel_calls), applicationContext.resources
                                    .getString(R.string.nc_notification_channel_calls_description), true,
                                    NotificationManagerCompat.IMPORTANCE_HIGH, soundUri!!, audioAttributesBuilder.build())

                            val uri = Uri.parse(signatureVerification!!.userEntity.baseUrl)
                            val baseUrl = uri.host

                            val notificationBuilder = NotificationCompat.Builder(this@MagicFirebaseMessagingService, NOTIFICATION_CHANNEL_CALLS_V3)
                                    .setPriority(NotificationCompat.PRIORITY_MAX)
                                    .setCategory(NotificationCompat.CATEGORY_CALL)
                                    .setSmallIcon(R.drawable.ic_call_black_24dp)
                                    .setSubText(baseUrl)
                                    .setShowWhen(true)
                                    .setWhen(decryptedPushMessage!!.timestamp)
                                    .setContentTitle(EmojiCompat.get().process(decryptedPushMessage!!.subject))
                                    .setAutoCancel(true)
                                    .setOngoing(true)
                                    .setTimeoutAfter(45000L)
                                    .setFullScreenIntent(fullScreenPendingIntent, true)
                                    .setSound(soundUri)
                            startForeground(System.currentTimeMillis().toInt(), notificationBuilder.build())
                        } else {
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
}