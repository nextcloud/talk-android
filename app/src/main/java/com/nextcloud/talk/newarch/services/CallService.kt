package com.nextcloud.talk.newarch.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.emoji.text.EmojiCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MagicCallActivity
import com.nextcloud.talk.jobs.MessageNotificationWorker
import com.nextcloud.talk.models.SignatureVerification
import com.nextcloud.talk.models.json.push.DecryptedPushMessage
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.utils.MagicJson
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import javax.crypto.Cipher
import javax.crypto.NoSuchPaddingException
import kotlin.coroutines.CoroutineContext

class CallService : Service(), KoinComponent, CoroutineScope {
    val tag: String = "CallService"
    private var coroutineJob: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    val appPreferences: AppPreferences by inject()
    val usersRepository: UsersRepository by inject()

    var currentlyActiveNotificationId = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (intent.action == BundleKeys.KEY_INCOMING_PUSH_MESSSAGE) {
                decryptMessage(intent.getStringExtra(BundleKeys.KEY_ENCRYPTED_SUBJECT), intent.getStringExtra(BundleKeys.KEY_ENCRYPTED_SIGNATURE))
            } else if (intent.action == BundleKeys.KEY_REJECT_INCOMING_CALL || intent.action == BundleKeys.KEY_SHOW_INCOMING_CALL) {
                if (intent.getLongExtra(BundleKeys.KEY_NOTIFICATION_ID, -1L) == currentlyActiveNotificationId) {
                    stopForeground(true)
                } else {
                    // do nothing? :D
                }
            } else {

            }
        }
        return START_NOT_STICKY

    }

    private fun decryptMessage(subject: String, signature: String) = coroutineContext.run {
        launch {
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
                            when {
                                delete -> {
                                    NotificationUtils.cancelExistingNotificationWithId(applicationContext, signatureVerification.userEntity!!, notificationId!!)
                                }
                                deleteAll -> {
                                    NotificationUtils.cancelAllNotificationsForAccount(applicationContext, signatureVerification.userEntity!!)
                                }
                                type == "call" -> {
                                    val timestamp = System.currentTimeMillis()

                                    val fullScreenIntent = Intent(applicationContext, MagicCallActivity::class.java)
                                    fullScreenIntent.action = BundleKeys.KEY_OPEN_INCOMING_CALL
                                    val bundle = Bundle()
                                    bundle.putString(BundleKeys.KEY_CONVERSATION_TOKEN, decryptedPushMessage.id)
                                    bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, signatureVerification.userEntity)
                                    bundle.putLong(BundleKeys.KEY_NOTIFICATION_ID, timestamp)
                                    fullScreenIntent.putExtras(bundle)

                                    fullScreenIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                    val fullScreenPendingIntent = PendingIntent.getActivity(this@CallService, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT)

                                    val audioAttributesBuilder = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    audioAttributesBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST)

                                    val soundUri = NotificationUtils.getCallSoundUri(applicationContext, appPreferences)
                                    val vibrationEffect = NotificationUtils.getVibrationEffect(appPreferences)

                                    val notificationChannelId = NotificationUtils.getNotificationChannelId(applicationContext, applicationContext.resources
                                            .getString(R.string.nc_notification_channel_calls), applicationContext.resources
                                            .getString(R.string.nc_notification_channel_calls_description), true,
                                            NotificationManagerCompat.IMPORTANCE_HIGH, soundUri!!,
                                            audioAttributesBuilder.build(), vibrationEffect, false, null)

                                    val userBaseUrl = Uri.parse(signatureVerification.userEntity!!.baseUrl).toString()

                                    val rejectCallIntent = Intent(this@CallService, CallService::class.java)
                                    rejectCallIntent.action = BundleKeys.KEY_REJECT_INCOMING_CALL
                                    rejectCallIntent.putExtra(BundleKeys.KEY_NOTIFICATION_ID, timestamp)
                                    val rejectCallPendingIntent = PendingIntent.getService(this@CallService, 0, rejectCallIntent, 0)
                                    val notificationBuilder = NotificationCompat.Builder(this@CallService, notificationChannelId)
                                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                                            .setCategory(NotificationCompat.CATEGORY_CALL)
                                            .setSmallIcon(R.drawable.ic_call_black_24dp)
                                            .setSubText(userBaseUrl)
                                            .setShowWhen(true)
                                            .setWhen(timestamp)
                                            .setContentTitle(EmojiCompat.get().process(decryptedPushMessage.subject.toString()))
                                            .setAutoCancel(true)
                                            .setOngoing(true)
                                            .addAction(R.drawable.ic_call_end_white_24px, resources.getString(R.string.reject_call), rejectCallPendingIntent)
                                            //.setTimeoutAfter(45000L)
                                            .setFullScreenIntent(fullScreenPendingIntent, true)
                                            .setSound(NotificationUtils.getCallSoundUri(applicationContext, appPreferences), AudioManager.STREAM_RING)

                                    if (vibrationEffect != null) {
                                        notificationBuilder.setVibrate(vibrationEffect)
                                    }

                                    val notification = notificationBuilder.build()
                                    notification.flags = notification.flags or Notification.FLAG_INSISTENT
                                    //checkIfCallIsActive(signatureVerification, decryptedPushMessage)
                                    currentlyActiveNotificationId = timestamp
                                    startForeground(timestamp.toInt(), notification)
                                }
                                else -> {
                                    val json = Json(MagicJson.customJsonConfiguration)

                                    val messageData = Data.Builder()
                                            .putString(BundleKeys.KEY_DECRYPTED_PUSH_MESSAGE, LoganSquare.serialize(decryptedPushMessage))
                                            .putString(BundleKeys.KEY_SIGNATURE_VERIFICATION, json.stringify(SignatureVerification.serializer(), signatureVerification))
                                            .build()
                                    val pushNotificationWork = OneTimeWorkRequest.Builder(MessageNotificationWorker::class.java).setInputData(messageData).build()
                                    WorkManager.getInstance().enqueue(pushNotificationWork)
                                }
                            }
                        }
                    } else {
                        // do absolutely nothing
                    }
                } catch (e1: NoSuchAlgorithmException) {
                    Log.d(tag, "No proper algorithm to decrypt the message " + e1.localizedMessage)
                } catch (e1: NoSuchPaddingException) {
                    Log.d(tag, "No proper padding to decrypt the message " + e1.localizedMessage)
                } catch (e1: InvalidKeyException) {
                    Log.d(tag, "Invalid private key " + e1.localizedMessage)
                }
            } catch (exception: Exception) {
                Log.d(tag, "Something went very wrong " + exception.localizedMessage)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}