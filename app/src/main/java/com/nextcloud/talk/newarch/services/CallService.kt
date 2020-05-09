package com.nextcloud.talk.newarch.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.emoji.text.EmojiCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import coil.target.Target
import coil.transform.CircleCropTransformation
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R
import com.nextcloud.talk.events.CallEvent
import com.nextcloud.talk.jobs.NotificationWorker
import com.nextcloud.talk.models.SignatureVerification
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationOverall
import com.nextcloud.talk.models.json.participants.ParticipantsOverall
import com.nextcloud.talk.models.json.push.DecryptedPushMessage
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.GetConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetParticipantsForCallUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.toUser
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.newarch.utils.MagicJson
import com.nextcloud.talk.newarch.utils.NetworkComponents
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.greenrobot.eventbus.EventBus
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.parameter.parametersOf
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
    val conversationsRepository: ConversationsRepository by inject()
    val networkComponents: NetworkComponents by inject()
    val apiErrorHandler: ApiErrorHandler by inject()
    val eventBus: EventBus by inject()

    private var activeNotification = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.action == BundleKeys.KEY_SHOW_INCOMING_CALL) {
                val decryptedPushMessageString: String = it.getStringExtra(BundleKeys.KEY_DECRYPTED_PUSH_MESSAGE)!!
                val signatureVerificationString: String = it.getStringExtra(BundleKeys.KEY_SIGNATURE_VERIFICATION)!!
                val conversationString: String = it.getStringExtra(BundleKeys.KEY_CONVERSATION)!!

                val json = Json(MagicJson.customJsonConfiguration)
                val decryptedPushMessage = LoganSquare.parse(decryptedPushMessageString, DecryptedPushMessage::class.java)
                val signatureVerification = json.parse(SignatureVerification.serializer(), signatureVerificationString)
                val conversation = json.parse(Conversation.serializer(), conversationString)

                showIncomingCall(signatureVerification, decryptedPushMessage, conversation)
            } else if (it.action == BundleKeys.KEY_REJECT_INCOMING_CALL || it.action == BundleKeys.DISMISS_CALL_NOTIFICATION) {
                if (it.getStringExtra(BundleKeys.KEY_ACTIVE_NOTIFICATION) == activeNotification) {
                    endIncomingConversation(it.action != BundleKeys.DISMISS_CALL_NOTIFICATION)
                } else {
                    // do nothing? :D
                }
            } else {

            }
        }
        return START_NOT_STICKY

    }

    private fun showIncomingCall(signatureVerification: SignatureVerification, decryptedPushMessage: DecryptedPushMessage, conversation: Conversation) {
        val generatedActiveNotificationId = signatureVerification.userEntity!!.id.toString() + "@" + decryptedPushMessage.notificationId!!.toString()
        val fullScreenPendingIntent = NotificationUtils.getIncomingCallIPendingIntent(applicationContext, this@CallService, conversation, signatureVerification.userEntity!!, generatedActiveNotificationId)

        val soundUri = NotificationUtils.getCallSoundUri(applicationContext, appPreferences)
        val vibrationEffect = NotificationUtils.getVibrationEffect(appPreferences)

        val notificationChannelId = NotificationUtils.getNotificationChannelId(applicationContext, applicationContext.resources
                .getString(R.string.nc_notification_channel_calls), applicationContext.resources
                .getString(R.string.nc_notification_channel_calls_description), true,
                NotificationManagerCompat.IMPORTANCE_HIGH, soundUri!!,
                NotificationUtils.getCallAudioAttributes(true), vibrationEffect, false, null)

        val uri: Uri = Uri.parse(signatureVerification.userEntity?.baseUrl)
        var baseUrl = uri.host

        if (baseUrl == null) {
            baseUrl = signatureVerification.userEntity?.baseUrl
        }

        var largeIcon = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_baseline_person_black_24)

        val rejectCallIntent = Intent(this@CallService, CallService::class.java)
        rejectCallIntent.action = BundleKeys.KEY_REJECT_INCOMING_CALL
        rejectCallIntent.putExtra(BundleKeys.KEY_ACTIVE_NOTIFICATION, generatedActiveNotificationId)
        val rejectCallPendingIntent = PendingIntent.getService(this@CallService, 0, rejectCallIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val notificationBuilder = NotificationCompat.Builder(this@CallService, notificationChannelId)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setSmallIcon(R.drawable.ic_call_black_24dp)
                .setLargeIcon(largeIcon)
                .setSubText(baseUrl)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setContentTitle(EmojiCompat.get().process(decryptedPushMessage.subject.toString()))
                .setAutoCancel(true)
                .setOngoing(true)
                .addAction(R.drawable.ic_call_end_white_24px, resources.getString(R.string.nc_reject_call), rejectCallPendingIntent)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setSound(NotificationUtils.getCallSoundUri(applicationContext, appPreferences), AudioManager.STREAM_RING)

        if (vibrationEffect != null) {
            notificationBuilder.setVibrate(vibrationEffect)
        }

        val target = object : Target {
            override fun onSuccess(result: Drawable) {
                super.onSuccess(result)
                largeIcon = result.toBitmap()
                notificationBuilder.setLargeIcon(largeIcon)
                showNotification(notificationBuilder, signatureVerification.userEntity!!, conversation!!.token!!, decryptedPushMessage.notificationId!!, generatedActiveNotificationId)
            }

            override fun onError(error: Drawable?) {
                super.onError(error)
                showNotification(notificationBuilder, signatureVerification.userEntity!!, conversation!!.token!!, decryptedPushMessage.notificationId!!, generatedActiveNotificationId)
            }
        }

        val avatarUrl = ApiUtils.getUrlForAvatarWithName(signatureVerification.userEntity!!.baseUrl, conversation!!.name, R.dimen.avatar_size)
        val imageLoader = networkComponents.getImageLoader(signatureVerification.userEntity!!.toUser())

        val request = Images().getRequestForUrl(
                imageLoader, applicationContext, avatarUrl, signatureVerification.userEntity!!.toUser(),
                target, null, CircleCropTransformation())

        imageLoader.load(request)
    }

    private fun checkIsConversationActive(user: UserNgEntity, conversationToken: String, activeNotificationArgument: String) {
        if (activeNotificationArgument == activeNotification) {
            val getParticipantsForCallUseCase = GetParticipantsForCallUseCase(networkComponents.getRepository(false, user.toUser()), apiErrorHandler)
            getParticipantsForCallUseCase.invoke(this, parametersOf(user, conversationToken), object : UseCaseResponse<ParticipantsOverall> {
                override suspend fun onSuccess(result: ParticipantsOverall) {
                    val participants = result.ocs.data
                    if (participants.size > 0 && activeNotificationArgument == activeNotification) {
                        val activeParticipants = participants.filter { it.sessionId != null && !it.sessionId.equals("0") }
                        val activeOnAnotherDevice = activeParticipants.filter { it.userId == user.userId }
                        if (activeParticipants.isNotEmpty() && activeOnAnotherDevice.isEmpty()) {
                            delay(5000)
                            checkIsConversationActive(user, conversationToken, activeNotificationArgument)
                        } else {
                            endIncomingConversation(true)
                        }
                    } else if (activeNotificationArgument == activeNotification) {
                        endIncomingConversation(true)
                    }
                }

                override suspend fun onError(errorModel: ErrorModel?) {
                    endIncomingConversation(true)
                }
            })
        }
    }

    private fun endIncomingConversation(triggerEventBus: Boolean) {
        activeNotification = ""
        stopForeground(true)
        if (triggerEventBus) {
            eventBus.post(CallEvent())
        }
    }

    private fun showNotification(builder: NotificationCompat.Builder, user: UserNgEntity, conversationToken: String, internalNotificationId: Long, generatedNotificationId: String) {
        endIncomingConversation(true)
        activeNotification = generatedNotificationId
        val notification = builder.build()
        notification.extras.putLong(BundleKeys.KEY_INTERNAL_USER_ID, user.id)
        notification.extras.putLong(BundleKeys.KEY_NOTIFICATION_ID, internalNotificationId)
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        startForeground(generatedNotificationId.hashCode(), notification)
        checkIsConversationActive(user, conversationToken, generatedNotificationId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}