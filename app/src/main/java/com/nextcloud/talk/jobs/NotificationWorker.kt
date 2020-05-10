/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2020 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.jobs

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.emoji.text.EmojiCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.Coil
import coil.target.Target
import coil.transform.CircleCropTransformation
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R
import com.nextcloud.talk.models.SignatureVerification
import com.nextcloud.talk.models.json.chat.ChatUtils
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationOverall
import com.nextcloud.talk.models.json.notifications.NotificationOverall
import com.nextcloud.talk.models.json.push.DecryptedPushMessage
import com.nextcloud.talk.models.json.push.NotificationUser
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.GetConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetNotificationUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.toUser
import com.nextcloud.talk.newarch.services.CallService
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.newarch.utils.MagicJson
import com.nextcloud.talk.newarch.utils.NetworkComponents
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SHOW_INCOMING_CALL
import com.nextcloud.talk.utils.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.parameter.parametersOf
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.util.function.Consumer
import javax.crypto.Cipher
import javax.crypto.NoSuchPaddingException

class NotificationWorker(
        context: Context,
        workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), KoinComponent {
    val appPreferences: AppPreferences by inject()
    private val networkComponents: NetworkComponents by inject()
    private val apiErrorHandler: ApiErrorHandler by inject()
    private val usersRepository: UsersRepository by inject()
    private val conversationsRepository: ConversationsRepository by inject()

    val tag: String = "NotificationWorker"

    override suspend fun doWork(): Result = coroutineScope {
        val data = inputData
        decryptMessage(data.getString(BundleKeys.KEY_ENCRYPTED_SUBJECT)!!, data.getString(BundleKeys.KEY_ENCRYPTED_SIGNATURE)!!)

        Result.success()
    }

    private suspend fun decryptMessage(subject: String, signature: String) = coroutineScope {
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
                    var conversation: Conversation? = null
                    decryptedPushMessage.id?.let {
                        conversation = getConversationForTokenAndUser(signatureVerification.userEntity!!, it)
                    }

                    decryptedPushMessage.apply {
                        when {
                            delete -> {
                                NotificationUtils.cancelExistingNotificationWithId(applicationContext, signatureVerification.userEntity!!, notificationId!!)
                            }
                            deleteAll -> {
                                NotificationUtils.cancelAllNotificationsForAccount(applicationContext, signatureVerification.userEntity!!)
                            }
                            type == "call" && (conversation?.type == Conversation.ConversationType.ONE_TO_ONE_CONVERSATION || conversation?.objectType.equals("share:password")) -> {
                                conversation?.let {
                                    // start service
                                    val json = Json(MagicJson.customJsonConfiguration)
                                    val incomingCallIntent = Intent(applicationContext, CallService::class.java)
                                    incomingCallIntent.action = KEY_SHOW_INCOMING_CALL
                                    incomingCallIntent.putExtra(BundleKeys.KEY_DECRYPTED_PUSH_MESSAGE, LoganSquare.serialize(decryptedPushMessage))
                                    incomingCallIntent.putExtra(BundleKeys.KEY_SIGNATURE_VERIFICATION, json.stringify(SignatureVerification.serializer(), signatureVerification))
                                    incomingCallIntent.putExtra(BundleKeys.KEY_CONVERSATION, json.stringify(Conversation.serializer(), it))
                                    ContextCompat.startForegroundService(applicationContext, incomingCallIntent)
                                }

                            }
                            type == "call" -> {
                                conversation?.let { showNotification(decryptedPushMessage, signatureVerification, it) }
                            }
                            type == "room" -> {
                                conversation?.let { showNotificationWithObjectData(this@coroutineScope, decryptedPushMessage, signatureVerification, it) }
                            }
                            type == "chat" -> {
                                conversation?.let { showNotificationWithObjectData(this@coroutineScope, decryptedPushMessage, signatureVerification, it) }
                            }
                        }
                    }
                } else {
                    // do nothing
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

    private suspend fun getConversationForTokenAndUser(user: UserNgEntity, conversationToken: String): Conversation? {
        var conversation = conversationsRepository.getConversationForUserWithToken(user.id, conversationToken)
        if (conversation == null) {
            val getConversationUseCase = GetConversationUseCase(networkComponents.getRepository(false, user.toUser()), apiErrorHandler)
            runBlocking {
                getConversationUseCase.invoke(this, parametersOf(user, conversationToken), object : UseCaseResponse<ConversationOverall> {
                    override suspend fun onSuccess(result: ConversationOverall) {
                        val internalConversation = result.ocs.data
                        conversationsRepository.saveConversationsForUser(user.id, listOf(internalConversation), false)
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

    private fun showNotificationWithObjectData(coroutineScope: CoroutineScope, decryptedPushMessage: DecryptedPushMessage, signatureVerification: SignatureVerification, conversation: Conversation) {
        val nextcloudTalkRepository = networkComponents.getRepository(false, signatureVerification.userEntity!!.toUser())
        val getNotificationUseCase = GetNotificationUseCase(nextcloudTalkRepository, apiErrorHandler)
        getNotificationUseCase.invoke(coroutineScope, parametersOf(signatureVerification.userEntity, decryptedPushMessage.notificationId.toString()), object : UseCaseResponse<NotificationOverall> {
            override suspend fun onSuccess(result: NotificationOverall) {
                val notification = result.ocs.notification

                notification.messageRichParameters?.let { messageRichParameters ->
                    if (messageRichParameters.size > 0) {
                        decryptedPushMessage.text = ChatUtils.getParsedMessage(notification.messageRich, messageRichParameters)
                    } else {
                        decryptedPushMessage.text = notification.message
                    }
                } ?: run {
                    decryptedPushMessage.text = notification.message
                }

                val subjectRichParameters = notification.subjectRichParameters
                val callHashMap = subjectRichParameters?.get("call")
                val userHashMap = subjectRichParameters?.get("user")
                val guestHashMap = subjectRichParameters?.get("guest")

                if (callHashMap?.containsKey("name") == true) {
                    if (notification.objectType == "chat") {
                        decryptedPushMessage.subject = callHashMap["name"]
                    } else {
                        decryptedPushMessage.subject = notification.subject
                    }

                }

                val notificationUser = NotificationUser()
                userHashMap?.let { userHashMap ->
                    notificationUser.id = userHashMap["id"]
                    notificationUser.type = userHashMap["type"]
                    notificationUser.name = userHashMap["name"]
                }

                guestHashMap?.let { guestHashMap ->
                    notificationUser.id = guestHashMap["id"]
                    notificationUser.type = guestHashMap["type"]
                    notificationUser.name = guestHashMap["name"]
                }

                decryptedPushMessage.timestamp = notification.datetime.millis
                decryptedPushMessage.notificationUser = notificationUser
                showNotification(decryptedPushMessage, signatureVerification, conversation)
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                // tough love, no notification for you
            }

        })
    }

    private fun showNotification(
            decryptedPushMessage: DecryptedPushMessage,
            signatureVerification: SignatureVerification,
            conversation: Conversation) {
        val largeIcon = when (conversation.type) {
            Conversation.ConversationType.PUBLIC_CONVERSATION -> {
                BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_link_black_24px)
            }
            Conversation.ConversationType.GROUP_CONVERSATION -> {
                BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_people_group_black_24px)
            }
            else -> {
                // one to one and unknown
                if (decryptedPushMessage.type == "call") {
                    BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_baseline_person_black_24)
                } else {
                    BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_chat_black_24dp)
                }
            }
        }

        var notificationId = decryptedPushMessage.timestamp.toInt()

        val pendingIntent = if (decryptedPushMessage.type == "call") {
            NotificationUtils.getIncomingCallIPendingIntent(applicationContext, applicationContext, conversation, signatureVerification.userEntity!!, null)
        } else {
            NotificationUtils.getNotificationPendingIntent(applicationContext, signatureVerification.userEntity!!.id, conversation.token!!)
        }

        val soundUri = NotificationUtils.getMessageSoundUri(applicationContext, appPreferences)

        val audioAttributesBuilder: AudioAttributes.Builder =
                AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)

        val vibrationEffect = NotificationUtils.getVibrationEffect(appPreferences, false)

        val notificationChannelId = NotificationUtils.getNotificationChannelId(applicationContext, applicationContext.resources
                .getString(R.string.nc_notification_channel_messages), applicationContext.resources
                .getString(R.string.nc_notification_channel_messages), true,
                NotificationManagerCompat.IMPORTANCE_HIGH, soundUri!!,
                audioAttributesBuilder.build(), vibrationEffect, false, null)

        val uri: Uri = Uri.parse(signatureVerification.userEntity?.baseUrl)
        var baseUrl = uri.host

        if (baseUrl == null) {
            baseUrl = signatureVerification.userEntity?.baseUrl
        }

        val notificationBuilder = NotificationCompat.Builder(applicationContext, notificationChannelId)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setSmallIcon(R.drawable.ic_logo)
                .setLargeIcon(largeIcon)
                .setSubText(baseUrl)
                .setShowWhen(true)
                .setGroup(signatureVerification.userEntity?.id.toString() + "@" + decryptedPushMessage.id)
                .setWhen(decryptedPushMessage.timestamp)
                .setContentTitle(EmojiCompat.get().process(decryptedPushMessage.subject.toString()))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(soundUri, AudioManager.STREAM_NOTIFICATION)

        if (vibrationEffect != null) {
            notificationBuilder.setVibrate(vibrationEffect)
        }

        if (Build.VERSION.SDK_INT >= 23) {
            // This method should exist since API 21, but some phones don't have it
            // So as a safeguard, we don't use it until 23
            notificationBuilder.color = applicationContext.resources.getColor(R.color.colorPrimary)
        }


        val notificationInfoBundle = Bundle()
        notificationInfoBundle.putLong(BundleKeys.KEY_INTERNAL_USER_ID, signatureVerification.userEntity!!.id)
        notificationInfoBundle.putString(BundleKeys.KEY_CONVERSATION_TOKEN, decryptedPushMessage.id)
        notificationInfoBundle.putLong(BundleKeys.KEY_NOTIFICATION_ID, decryptedPushMessage.notificationId!!)
        notificationBuilder.extras = notificationInfoBundle

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && decryptedPushMessage.notificationUser != null && decryptedPushMessage.type == "chat") {
            var style: NotificationCompat.MessagingStyle? = null

            decryptedPushMessage.id?.let { decryptedMessageId ->
                val activeStatusBarNotification =
                        NotificationUtils.findNotificationForConversation(
                                applicationContext,
                                signatureVerification.userEntity!!, decryptedMessageId)
                activeStatusBarNotification?.let { activeStatusBarNotification ->
                    notificationId = activeStatusBarNotification.id
                    style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(activeStatusBarNotification.notification)
                    notificationBuilder.setStyle(style)
                }
            }

            notificationBuilder.setOnlyAlertOnce(true)
            decryptedPushMessage.notificationUser?.let { notificationUser ->
                val person = Person.Builder().setKey(signatureVerification.userEntity!!.id.toString() + "@" + notificationUser.id)
                        .setName(EmojiCompat.get().process(notificationUser.name))
                        .setBot(notificationUser.type == "bot")

                if (notificationUser.type == "user" || notificationUser.type == "guest") {
                    val avatarUrl = when (notificationUser.type) {
                        "user" -> {
                            ApiUtils.getUrlForAvatarWithName(signatureVerification.userEntity!!.baseUrl, notificationUser.id, R.dimen.avatar_size)
                        }
                        else -> {
                            ApiUtils.getUrlForAvatarWithNameForGuests(signatureVerification.userEntity!!.baseUrl, notificationUser.name, R.dimen.avatar_size)
                        }
                    }

                    val target = object : Target {
                        override fun onSuccess(result: Drawable) {
                            super.onSuccess(result)
                            person.setIcon(IconCompat.createWithBitmap(result.toBitmap()))
                            notificationBuilder.setStyle(getStyle(decryptedPushMessage, conversation.type!!, person.build(), style))
                            NotificationManagerCompat.from(applicationContext).notify(notificationId, notificationBuilder.build())
                        }

                        override fun onError(error: Drawable?) {
                            super.onError(error)
                            notificationBuilder.setStyle(getStyle(decryptedPushMessage, conversation.type!!, person.build(), style))
                            NotificationManagerCompat.from(applicationContext).notify(notificationId, notificationBuilder.build())
                        }
                    }

                    val request = Images().getRequestForUrl(
                            Coil.loader(), applicationContext, avatarUrl!!, signatureVerification.userEntity!!.toUser(),
                            target, null, CircleCropTransformation()
                    )

                    networkComponents.getImageLoader(signatureVerification.userEntity!!.toUser()).load(request)
                } else {
                    notificationBuilder.setStyle(getStyle(decryptedPushMessage, conversation.type!!, person.build(), style))
                    NotificationManagerCompat.from(applicationContext).notify(notificationId, notificationBuilder.build())
                }
            }
        } else {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notificationBuilder.build())
        }
    }

    private fun getStyle(
            decryptedPushMessage: DecryptedPushMessage,
            conversationType: Conversation.ConversationType,
            person: Person,
            style: NotificationCompat.MessagingStyle?
    ): NotificationCompat.MessagingStyle? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val newStyle = NotificationCompat.MessagingStyle(person)
            newStyle.conversationTitle = decryptedPushMessage.subject
            newStyle.isGroupConversation = conversationType != Conversation.ConversationType.ONE_TO_ONE_CONVERSATION
            style?.messages?.forEach(
                    Consumer { message: NotificationCompat.MessagingStyle.Message ->
                        newStyle.addMessage(
                                NotificationCompat.MessagingStyle.Message(
                                        message.text,
                                        message.timestamp, message.person
                                )
                        )
                    }
            )
            newStyle.addMessage(decryptedPushMessage.text, decryptedPushMessage.timestamp, person)
            return newStyle
        }

        // we'll never come here
        return style
    }


}