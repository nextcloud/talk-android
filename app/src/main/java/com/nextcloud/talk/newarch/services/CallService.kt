package com.nextcloud.talk.newarch.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
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
import com.nextcloud.talk.activities.MagicCallActivity
import com.nextcloud.talk.events.CallEvent
import com.nextcloud.talk.jobs.MessageNotificationWorker
import com.nextcloud.talk.models.SignatureVerification
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationOverall
import com.nextcloud.talk.models.json.participants.Participant
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
import com.nextcloud.talk.newarch.utils.NetworkComponents
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.newarch.utils.MagicJson
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
import org.parceler.Parcels
import retrofit2.Retrofit
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
            if (it.action == BundleKeys.KEY_INCOMING_PUSH_MESSSAGE) {
                decryptMessage(it.getStringExtra(BundleKeys.KEY_ENCRYPTED_SUBJECT), it.getStringExtra(BundleKeys.KEY_ENCRYPTED_SIGNATURE))
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
                        val conversation = getConversationForTokenAndUser(signatureVerification.userEntity!!, decryptedPushMessage.id!!)

                        decryptedPushMessage.apply {
                            when {
                                delete -> {
                                    NotificationUtils.cancelExistingNotificationWithId(applicationContext, signatureVerification.userEntity!!, notificationId!!)
                                }
                                deleteAll -> {
                                    NotificationUtils.cancelAllNotificationsForAccount(applicationContext, signatureVerification.userEntity!!)
                                }
                                type == "call" -> {
                                    if (conversation != null) {
                                        val generatedActiveNotificationId = signatureVerification.userEntity!!.id.toString() + "@" + decryptedPushMessage.notificationId!!.toString()
                                        val fullScreenIntent = Intent(applicationContext, MagicCallActivity::class.java)
                                        fullScreenIntent.action = BundleKeys.KEY_OPEN_INCOMING_CALL
                                        val bundle = Bundle()
                                        bundle.putParcelable(BundleKeys.KEY_CONVERSATION, Parcels.wrap(conversation))
                                        bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, signatureVerification.userEntity)
                                        bundle.putString(BundleKeys.KEY_ACTIVE_NOTIFICATION, generatedActiveNotificationId)
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

                                        var largeIcon = when (conversation.type) {
                                            Conversation.ConversationType.PUBLIC_CONVERSATION -> {
                                                BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_link_black_24px)
                                            }
                                            Conversation.ConversationType.GROUP_CONVERSATION -> {
                                                BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_people_group_black_24px)
                                            }
                                            else -> {
                                                // one to one and unknown
                                                BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_user)
                                            }
                                        }

                                        val rejectCallIntent = Intent(this@CallService, CallService::class.java)
                                        rejectCallIntent.action = BundleKeys.KEY_REJECT_INCOMING_CALL
                                        rejectCallIntent.putExtra(BundleKeys.KEY_ACTIVE_NOTIFICATION, generatedActiveNotificationId)
                                        val rejectCallPendingIntent = PendingIntent.getService(this@CallService, 0, rejectCallIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                                        val notificationBuilder = NotificationCompat.Builder(this@CallService, notificationChannelId)
                                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                                .setCategory(NotificationCompat.CATEGORY_CALL)
                                                .setSmallIcon(R.drawable.ic_call_black_24dp)
                                                .setLargeIcon(largeIcon)
                                                .setSubText(userBaseUrl)
                                                .setShowWhen(true)
                                                .setWhen(System.currentTimeMillis())
                                                .setContentTitle(EmojiCompat.get().process(decryptedPushMessage.subject.toString()))
                                                .setAutoCancel(true)
                                                .setOngoing(true)
                                                .addAction(R.drawable.ic_call_end_white_24px, resources.getString(R.string.reject_call), rejectCallPendingIntent)
                                                .setContentIntent(fullScreenPendingIntent)
                                                .setFullScreenIntent(fullScreenPendingIntent, true)
                                                .setSound(NotificationUtils.getCallSoundUri(applicationContext, appPreferences), AudioManager.STREAM_RING)

                                        if (vibrationEffect != null) {
                                            notificationBuilder.setVibrate(vibrationEffect)
                                        }

                                        if (conversation.type == Conversation.ConversationType.ONE_TO_ONE_CONVERSATION) {
                                            val target = object : Target {
                                                override fun onSuccess(result: Drawable) {
                                                    super.onSuccess(result)
                                                    largeIcon = result.toBitmap()
                                                    notificationBuilder.setLargeIcon(largeIcon)
                                                    showNotification(notificationBuilder, signatureVerification.userEntity!!, conversation.token!!, decryptedPushMessage.notificationId!!, generatedActiveNotificationId)
                                                }

                                                override fun onError(error: Drawable?) {
                                                    super.onError(error)
                                                    showNotification(notificationBuilder, signatureVerification.userEntity!!, conversation.token!!, decryptedPushMessage.notificationId!!, generatedActiveNotificationId)
                                                }
                                            }

                                            val avatarUrl = ApiUtils.getUrlForAvatarWithName(signatureVerification.userEntity!!.baseUrl, conversation.name, R.dimen.avatar_size)
                                            val imageLoader = networkComponents.getImageLoader(signatureVerification.userEntity!!.toUser())

                                            val request = Images().getRequestForUrl(
                                                    imageLoader, applicationContext, avatarUrl, signatureVerification.userEntity,
                                                    target, null, CircleCropTransformation())

                                            imageLoader.load(request)
                                        } else {
                                            showNotification(notificationBuilder, signatureVerification.userEntity!!, conversation.token!!, decryptedPushMessage.notificationId!!, generatedActiveNotificationId)
                                        }
                                    }
                                }
                                else -> {
                                    if (conversation != null) {
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

    private fun checkIsConversationActive(user: UserNgEntity, conversationToken: String, activeNotificationArgument: String) {
        if (activeNotificationArgument == activeNotification) {
            val getParticipantsForCallUseCase = GetParticipantsForCallUseCase(networkComponents.getRepository(false, user.toUser()), apiErrorHandler)
            getParticipantsForCallUseCase.invoke(this, parametersOf(user, conversationToken), object : UseCaseResponse<ParticipantsOverall> {
                override suspend fun onSuccess(result: ParticipantsOverall) {
                    val participants = result.ocs.data
                    if (participants.size > 0 && activeNotificationArgument == activeNotification) {
                        val activeParticipants = participants.filter { it.participantFlags != Participant.ParticipantFlags.NOT_IN_CALL }
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

    private fun endIncomingConversation(triggerEventBus : Boolean) {
        activeNotification = ""
        stopForeground(true)
        if (triggerEventBus) {
            eventBus.post(CallEvent())
        }
    }

    private suspend fun getConversationForTokenAndUser(user: UserNgEntity, conversationToken: String): Conversation? {
        var conversation = conversationsRepository.getConversationForUserWithToken(user.id, conversationToken)
        if (conversation == null) {
            val getConversationUseCase = GetConversationUseCase(networkComponents.getRepository(false, user.toUser()), apiErrorHandler)
            runBlocking {
                getConversationUseCase.invoke(this, parametersOf(user, conversationToken), object : UseCaseResponse<ConversationOverall> {
                    override suspend fun onSuccess(result: ConversationOverall) {
                        val internalConversation = result.ocs.data
                        conversationsRepository.saveConversationsForUser(user.id!!, listOf(internalConversation), false)
                        conversation = result.ocs.data
                    }

                    override suspend fun onError(errorModel: ErrorModel?) {
                        conversation = null
                    }
                })
            }
        }

        return conversation
    }

    private fun showNotification(builder: NotificationCompat.Builder, user: UserNgEntity, conversationToken: String, internalNotificationId: Long, generatedNotificationId: String) {
        endIncomingConversation(true)
        activeNotification = generatedNotificationId
        val notification = builder.build()
        notification.extras.putLong(BundleKeys.KEY_INTERNAL_USER_ID, user.id!!)
        notification.extras.putLong(BundleKeys.KEY_NOTIFICATION_ID, internalNotificationId)
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        startForeground(generatedNotificationId.hashCode(), notification)
        checkIsConversationActive(user, conversationToken, generatedNotificationId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}