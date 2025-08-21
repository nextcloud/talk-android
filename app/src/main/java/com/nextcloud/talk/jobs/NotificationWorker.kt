/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.emoji2.text.EmojiCompat
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.callnotification.CallNotificationActivity
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.models.SignatureVerification
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.chat.ChatUtils.Companion.getParsedMessage
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.notifications.NotificationOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.ParticipantsOverall
import com.nextcloud.talk.models.json.push.DecryptedPushMessage
import com.nextcloud.talk.models.json.push.NotificationUser
import com.nextcloud.talk.receivers.DirectReplyReceiver
import com.nextcloud.talk.receivers.DismissRecordingAvailableReceiver
import com.nextcloud.talk.receivers.MarkAsReadReceiver
import com.nextcloud.talk.receivers.ShareRecordingToChatReceiver
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.NotificationUtils.cancelAllNotificationsForAccount
import com.nextcloud.talk.utils.NotificationUtils.cancelNotification
import com.nextcloud.talk.utils.NotificationUtils.findNotificationForRoom
import com.nextcloud.talk.utils.NotificationUtils.getCallRingtoneUri
import com.nextcloud.talk.utils.NotificationUtils.loadAvatarSync
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_DISMISS_RECORDING_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FROM_NOTIFICATION_START_CALL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_MESSAGE_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_NOTIFICATION_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_NOTIFICATION_RESTRICT_DELETION
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_NOTIFICATION_TIMESTAMP
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_REMOTE_TALK_SHARE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ONE_TO_ONE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SHARE_RECORDING_TO_CHAT_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SYSTEM_NOTIFICATION_ID
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.net.CookieManager
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.NoSuchPaddingException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class NotificationWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    @Inject
    lateinit var appPreferences: AppPreferences

    @JvmField
    @Inject
    var arbitraryStorageManager: ArbitraryStorageManager? = null

    @JvmField
    @Inject
    var retrofit: Retrofit? = null

    var chatNetworkDataSource: ChatNetworkDataSource? = null
        @Inject set

    @Inject
    lateinit var userManager: UserManager

    @JvmField
    @Inject
    var okHttpClient: OkHttpClient? = null
    private lateinit var credentials: String
    private lateinit var ncApi: NcApi
    private lateinit var pushMessage: DecryptedPushMessage
    private lateinit var signatureVerification: SignatureVerification
    private var context: Context? = null
    private var conversationType: String? = "one2one"
    private lateinit var notificationManager: NotificationManagerCompat

    override fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)
        context = applicationContext

        initDecryptedData(inputData)
        initNcApiAndCredentials()

        notificationManager = NotificationManagerCompat.from(context!!)

        pushMessage.timestamp = System.currentTimeMillis()

        Log.d(TAG, pushMessage.toString())
        Log.d(TAG, "pushMessage.id (=KEY_ROOM_TOKEN): " + pushMessage.id)
        Log.d(TAG, "pushMessage.notificationId: " + pushMessage.notificationId)
        Log.d(TAG, "pushMessage.notificationIds: " + pushMessage.notificationIds)
        Log.d(TAG, "pushMessage.timestamp: " + pushMessage.timestamp)

        if (pushMessage.delete) {
            cancelNotification(context, signatureVerification.user!!, pushMessage.notificationId)
        } else if (pushMessage.deleteAll) {
            cancelAllNotificationsForAccount(context, signatureVerification.user!!)
        } else if (pushMessage.deleteMultiple) {
            for (notificationId in pushMessage.notificationIds!!) {
                cancelNotification(context, signatureVerification.user!!, notificationId)
            }
        } else if (isTalkNotification()) {
            Log.d(TAG, "pushMessage.type: " + pushMessage.type)
            when (pushMessage.type) {
                TYPE_CHAT, TYPE_ROOM, TYPE_RECORDING, TYPE_REMINDER -> handleNonCallPushMessage()
                TYPE_REMOTE_TALK_SHARE -> handleRemoteTalkSharePushMessage()
                TYPE_CALL -> handleCallPushMessage()
                else -> Log.e(TAG, pushMessage.type + " is not handled")
            }
        } else if (isAdminTalkNotification()) {
            Log.d(TAG, "pushMessage.type: " + pushMessage.type)
            when (pushMessage.type) {
                TYPE_ADMIN_NOTIFICATIONS -> handleTestPushMessage()
                else -> Log.e(TAG, pushMessage.type + " is not handled")
            }
        } else {
            Log.d(TAG, "a pushMessage that is not for spreed was received.")
        }

        return Result.success()
    }

    private fun handleTestPushMessage() {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = getIntentFlags()
        showNotification(intent, null)
    }

    private fun handleNonCallPushMessage() {
        val mainActivityIntent = createMainActivityIntent()
        if (pushMessage.notificationId != Long.MIN_VALUE) {
            getNcDataAndShowNotification(mainActivityIntent)
        } else {
            showNotification(mainActivityIntent, null)
        }
    }

    private fun handleRemoteTalkSharePushMessage() {
        val mainActivityIntent = Intent(context, MainActivity::class.java)
        mainActivityIntent.flags = getIntentFlags()
        val bundle = Bundle()
        bundle.putLong(KEY_INTERNAL_USER_ID, signatureVerification.user!!.id!!)
        bundle.putBoolean(KEY_REMOTE_TALK_SHARE, true)
        mainActivityIntent.putExtras(bundle)

        if (pushMessage.notificationId != Long.MIN_VALUE) {
            getNcDataAndShowNotification(mainActivityIntent)
        } else {
            showNotification(mainActivityIntent, null)
        }
    }

    private fun handleCallPushMessage() {
        val userBeingCalled = userManager.getUserWithId(signatureVerification.user!!.id!!).blockingGet()

        fun createBundle(conversation: ConversationModel): Bundle {
            val bundle = Bundle()
            bundle.putString(KEY_ROOM_TOKEN, pushMessage.id)
            bundle.putInt(KEY_NOTIFICATION_TIMESTAMP, pushMessage.timestamp.toInt())
            bundle.putLong(KEY_INTERNAL_USER_ID, signatureVerification.user!!.id!!)
            bundle.putBoolean(KEY_FROM_NOTIFICATION_START_CALL, true)

            val isOneToOneCall = conversation.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL

            bundle.putBoolean(KEY_ROOM_ONE_TO_ONE, isOneToOneCall) // ggf change in Activity? not necessary????
            bundle.putString(BundleKeys.KEY_CONVERSATION_NAME, conversation.name)
            bundle.putString(BundleKeys.KEY_CONVERSATION_DISPLAY_NAME, conversation.displayName)
            bundle.putInt(BundleKeys.KEY_CALL_FLAG, conversation.callFlag)

            val participantPermission = ParticipantPermissions(
                userBeingCalled!!.capabilities!!.spreedCapability!!,
                conversation
            )
            bundle.putBoolean(
                BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO,
                participantPermission.canPublishAudio()
            )
            bundle.putBoolean(
                BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO,
                participantPermission.canPublishVideo()
            )
            bundle.putBoolean(
                BundleKeys.KEY_IS_MODERATOR,
                ConversationUtils.isParticipantOwnerOrModerator(conversation)
            )
            return bundle
        }

        fun prepareCallNotificationScreen(conversation: ConversationModel) {
            val fullScreenIntent = Intent(context, CallNotificationActivity::class.java)
            val bundle = createBundle(conversation)

            fullScreenIntent.putExtras(bundle)
            fullScreenIntent.flags = getIntentFlags()

            val requestCode = System.currentTimeMillis().toInt()

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                fullScreenIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val soundUri = getCallRingtoneUri(applicationContext, appPreferences)
            val notificationChannelId = NotificationUtils.NotificationChannels.NOTIFICATION_CHANNEL_CALLS_V4.name
            val uri = signatureVerification.user!!.baseUrl!!.toUri()
            val baseUrl = uri.host

            val notification =
                NotificationCompat.Builder(applicationContext, notificationChannelId)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setSmallIcon(R.drawable.ic_call_black_24dp)
                    .setSubText(baseUrl)
                    .setShowWhen(true)
                    .setWhen(pushMessage.timestamp)
                    .setContentTitle(EmojiCompat.get().process(pushMessage.subject))
                    // auto cancel is set to false because notification (including sound) should continue while
                    // CallNotificationActivity is active
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setContentIntent(fullScreenPendingIntent)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setSound(soundUri)
                    .build()
            notification.flags = notification.flags or Notification.FLAG_INSISTENT

            sendNotification(pushMessage.timestamp.toInt(), notification)

            checkIfCallIsActive(signatureVerification, conversation)
        }

        chatNetworkDataSource?.getRoom(userBeingCalled, roomToken = pushMessage.id!!)
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ConversationModel> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(conversation: ConversationModel) {
                    if (userManager.setUserAsActive(userBeingCalled!!).blockingGet()) {
                        prepareCallNotificationScreen(conversation)
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to get room", e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun initNcApiAndCredentials() {
        credentials = ApiUtils.getCredentials(
            signatureVerification.user!!.username,
            signatureVerification.user!!.token
        )!!
        ncApi = retrofit!!.newBuilder().client(
            okHttpClient!!.newBuilder().cookieJar(
                JavaNetCookieJar(
                    CookieManager()
                )
            ).build()
        ).build().create(
            NcApi::class.java
        )
    }

    @Suppress("TooGenericExceptionCaught", "NestedBlockDepth", "ComplexMethod", "LongMethod")
    private fun initDecryptedData(inputData: Data) {
        val subject = inputData.getString(BundleKeys.KEY_NOTIFICATION_SUBJECT)
        val signature = inputData.getString(BundleKeys.KEY_NOTIFICATION_SIGNATURE)
        try {
            val base64DecodedSubject = Base64.decode(subject, Base64.DEFAULT)
            val base64DecodedSignature = Base64.decode(signature, Base64.DEFAULT)
            val pushUtils = PushUtils()
            val privateKey = pushUtils.readKeyFromFile(false) as PrivateKey
            try {
                signatureVerification = pushUtils.verifySignature(
                    base64DecodedSignature,
                    base64DecodedSubject
                )
                if (signatureVerification.signatureValid) {
                    val cipher = Cipher.getInstance("RSA/None/PKCS1Padding")
                    cipher.init(Cipher.DECRYPT_MODE, privateKey)
                    val decryptedSubject = cipher.doFinal(base64DecodedSubject)

                    pushMessage = LoganSquare.parse(
                        String(decryptedSubject),
                        DecryptedPushMessage::class.java
                    )
                }
            } catch (e: NoSuchAlgorithmException) {
                Log.e(TAG, "No proper algorithm to decrypt the message ", e)
            } catch (e: NoSuchPaddingException) {
                Log.e(TAG, "No proper padding to decrypt the message ", e)
            } catch (e: InvalidKeyException) {
                Log.e(TAG, "Invalid private key ", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error occurred while initializing decoded data ", e)
        }
    }

    private fun isTalkNotification() = SPREED_APP == pushMessage.app

    private fun isAdminTalkNotification() = ADMIN_NOTIFICATION_TALK == pushMessage.app

    private fun getNcDataAndShowNotification(intent: Intent) {
        val user = signatureVerification.user

        // see https://github.com/nextcloud/notifications/blob/master/docs/ocs-endpoint-v2.md
        ncApi.getNcNotification(
            credentials,
            ApiUtils.getUrlForNcNotificationWithId(
                user!!.baseUrl!!,
                (pushMessage.notificationId!!).toString()
            )
        )
            .blockingSubscribe(object : Observer<NotificationOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(notificationOverall: NotificationOverall) {
                    val ncNotification = notificationOverall.ocs!!.notification
                    if (ncNotification != null) {
                        enrichPushMessageByNcNotificationData(ncNotification)
                        showNotification(intent, ncNotification)
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to get NC notification", e)
                    if (BuildConfig.DEBUG) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Failed to get NC notification", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun enrichPushMessageByNcNotificationData(
        ncNotification: com.nextcloud.talk.models.json.notifications.Notification
    ) {
        pushMessage.objectId = ncNotification.objectId
        pushMessage.timestamp = ncNotification.datetime!!.millis

        if (ncNotification.messageRichParameters != null &&
            ncNotification.messageRichParameters!!.isNotEmpty()
        ) {
            pushMessage.text = getParsedMessage(
                ncNotification.messageRich,
                ncNotification.messageRichParameters
            )
        } else {
            pushMessage.text = ncNotification.message
        }

        val subjectRichParameters = ncNotification.subjectRichParameters
        if (subjectRichParameters != null && subjectRichParameters.isNotEmpty()) {
            val callHashMap = subjectRichParameters["call"]
            val userHashMap = subjectRichParameters["user"]
            val guestHashMap = subjectRichParameters["guest"]
            if (callHashMap != null && callHashMap.isNotEmpty() && callHashMap.containsKey("name")) {
                if (subjectRichParameters.containsKey("reaction")) {
                    pushMessage.subject = ""
                } else if (ncNotification.objectType == "chat") {
                    pushMessage.subject = callHashMap["name"]!!
                } else {
                    pushMessage.subject = ncNotification.subject!!
                }

                if (subjectRichParameters.containsKey("reaction")) {
                    pushMessage.text = ncNotification.subject
                }

                if (callHashMap.containsKey("call-type")) {
                    conversationType = callHashMap["call-type"]
                }
            }
            val notificationUser = NotificationUser()
            if (userHashMap != null && userHashMap.isNotEmpty()) {
                notificationUser.id = userHashMap["id"]
                notificationUser.type = userHashMap["type"]
                notificationUser.name = userHashMap["name"]
                pushMessage.notificationUser = notificationUser
            } else if (guestHashMap != null && guestHashMap.isNotEmpty()) {
                notificationUser.id = guestHashMap["id"]
                notificationUser.type = guestHashMap["type"]
                notificationUser.name = guestHashMap["name"]
                pushMessage.notificationUser = notificationUser
            }
        } else {
            pushMessage.subject = ncNotification.subject.orEmpty()
        }
    }

    @Suppress("MagicNumber")
    private fun showNotification(
        intent: Intent,
        ncNotification: com.nextcloud.talk.models.json.notifications.Notification?
    ) {
        var category = ""
        when (pushMessage.type) {
            TYPE_CHAT, TYPE_ROOM, TYPE_RECORDING, TYPE_REMINDER, TYPE_ADMIN_NOTIFICATIONS, TYPE_REMOTE_TALK_SHARE ->
                category = Notification.CATEGORY_MESSAGE

            TYPE_CALL ->
                category = Notification.CATEGORY_CALL

            else -> Log.e(TAG, "unknown pushMessage.type")
        }

        val pendingIntent = createUniquePendingIntent(intent)
        val uri = signatureVerification.user!!.baseUrl!!.toUri()
        val baseUrl = uri.host

        var contentTitle: CharSequence? = ""
        if (!TextUtils.isEmpty(pushMessage.subject)) {
            contentTitle = EmojiCompat.get().process(pushMessage.subject)
        }

        var contentText: CharSequence? = ""
        if (!TextUtils.isEmpty(pushMessage.text)) {
            contentText = EmojiCompat.get().process(pushMessage.text!!)
        }

        val autoCancelOnClick = TYPE_RECORDING != pushMessage.type

        val notificationBuilder =
            createNotificationBuilder(category, contentTitle, contentText, baseUrl, pendingIntent, autoCancelOnClick)
        val activeStatusBarNotification = findNotificationForRoom(
            context,
            signatureVerification.user!!,
            pushMessage.id!!
        )

        // NOTE - systemNotificationId is an internal ID used on the device only.
        // It is NOT the same as the notification ID used in communication with the server.
        val systemNotificationId: Int =
            activeStatusBarNotification?.id ?: calculateCRC32(System.currentTimeMillis().toString()).toInt()

        if ((TYPE_CHAT == pushMessage.type || TYPE_REMINDER == pushMessage.type) &&
            pushMessage.notificationUser != null
        ) {
            prepareChatNotification(notificationBuilder, activeStatusBarNotification)
            addReplyAction(notificationBuilder, systemNotificationId)
            addMarkAsReadAction(notificationBuilder, systemNotificationId)
        }

        if (TYPE_RECORDING == pushMessage.type && ncNotification != null) {
            addDismissRecordingAvailableAction(notificationBuilder, systemNotificationId, ncNotification)
            addShareRecordingToChatAction(notificationBuilder, systemNotificationId, ncNotification)
        }
        sendNotification(systemNotificationId, notificationBuilder.build())
    }

    private fun createNotificationBuilder(
        category: String,
        contentTitle: CharSequence?,
        contentText: CharSequence?,
        baseUrl: String?,
        pendingIntent: PendingIntent?,
        autoCancelOnClick: Boolean
    ): NotificationCompat.Builder {
        val notificationBuilder = NotificationCompat.Builder(context!!, "1")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(category)
            .setLargeIcon(getLargeIcon())
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSubText(baseUrl)
            .setWhen(pushMessage.timestamp)
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(autoCancelOnClick)
            .setColor(context!!.resources.getColor(R.color.colorPrimary, null))

        val notificationInfoBundle = Bundle()
        notificationInfoBundle.putLong(KEY_INTERNAL_USER_ID, signatureVerification.user!!.id!!)
        // could be an ID or a TOKEN
        notificationInfoBundle.putString(KEY_ROOM_TOKEN, pushMessage.id)
        notificationInfoBundle.putLong(KEY_NOTIFICATION_ID, pushMessage.notificationId!!)

        if (pushMessage.type == TYPE_RECORDING) {
            notificationInfoBundle.putBoolean(KEY_NOTIFICATION_RESTRICT_DELETION, true)
        }

        notificationBuilder.setExtras(notificationInfoBundle)

        when (pushMessage.type) {
            TYPE_CHAT,
            TYPE_ROOM,
            TYPE_RECORDING,
            TYPE_REMINDER,
            TYPE_ADMIN_NOTIFICATIONS,
            TYPE_REMOTE_TALK_SHARE -> {
                notificationBuilder.setChannelId(
                    NotificationUtils.NotificationChannels.NOTIFICATION_CHANNEL_MESSAGES_V4.name
                )
            }
        }

        notificationBuilder.setContentIntent(pendingIntent)
        val groupName = signatureVerification.user!!.id.toString() + "@" + pushMessage.id
        notificationBuilder.setGroup(calculateCRC32(groupName).toString())
        return notificationBuilder
    }

    private fun getLargeIcon(): Bitmap {
        val largeIcon: Bitmap
        if (pushMessage.type == TYPE_RECORDING) {
            largeIcon = ContextCompat.getDrawable(context!!, R.drawable.ic_baseline_videocam_24)?.toBitmap()!!
        } else {
            when (conversationType) {
                "one2one" -> {
                    pushMessage.subject = ""
                    largeIcon =
                        ContextCompat.getDrawable(context!!, R.drawable.ic_people_group_black_24px)?.toBitmap()!!
                }

                "group" ->
                    largeIcon =
                        ContextCompat.getDrawable(context!!, R.drawable.ic_people_group_black_24px)?.toBitmap()!!

                "public" ->
                    largeIcon =
                        ContextCompat.getDrawable(context!!, R.drawable.ic_link_black_24px)?.toBitmap()!!

                else -> // assuming one2one
                    largeIcon = if (TYPE_CHAT == pushMessage.type || TYPE_ROOM == pushMessage.type) {
                        ContextCompat.getDrawable(context!!, R.drawable.ic_comment)?.toBitmap()!!
                    } else if (TYPE_REMINDER == pushMessage.type) {
                        ContextCompat.getDrawable(context!!, R.drawable.ic_timer_black_24dp)?.toBitmap()!!
                    } else {
                        ContextCompat.getDrawable(context!!, R.drawable.ic_call_black_24dp)?.toBitmap()!!
                    }
            }
        }
        return largeIcon
    }

    private fun calculateCRC32(s: String): Long {
        val crc32 = CRC32()
        crc32.update(s.toByteArray())
        return crc32.value
    }

    private fun prepareChatNotification(
        notificationBuilder: NotificationCompat.Builder,
        activeStatusBarNotification: StatusBarNotification?
    ) {
        val notificationUser = pushMessage.notificationUser
        val userType = notificationUser!!.type
        var style: NotificationCompat.MessagingStyle? = null
        if (activeStatusBarNotification != null) {
            style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(
                activeStatusBarNotification.notification
            )
        }
        val person = Person.Builder()
            .setKey(signatureVerification.user!!.id.toString() + "@" + notificationUser.id)
            .setName(EmojiCompat.get().process(notificationUser.name!!))
            .setBot("bot" == userType)
        notificationBuilder.setOnlyAlertOnce(false)

        if ("user" == userType || "guest" == userType) {
            val baseUrl = signatureVerification.user!!.baseUrl
            val avatarUrl = if ("user" == userType) {
                ApiUtils.getUrlForAvatar(
                    baseUrl!!,
                    notificationUser.id,
                    false
                )
            } else {
                ApiUtils.getUrlForGuestAvatar(baseUrl!!, notificationUser.name, false)
            }
            person.setIcon(loadAvatarSync(avatarUrl, context!!))
        }
        notificationBuilder.setStyle(getStyle(person.build(), style))
    }

    private fun buildIntentForAction(cls: Class<*>, systemNotificationId: Int, messageId: Int): PendingIntent {
        val actualIntent = Intent(context, cls)

        // NOTE - systemNotificationId is an internal ID used on the device only.
        // It is NOT the same as the notification ID used in communication with the server.
        actualIntent.putExtra(KEY_SYSTEM_NOTIFICATION_ID, systemNotificationId)
        actualIntent.putExtra(KEY_INTERNAL_USER_ID, signatureVerification.user?.id)
        actualIntent.putExtra(KEY_ROOM_TOKEN, pushMessage.id)
        actualIntent.putExtra(KEY_MESSAGE_ID, messageId)

        val intentFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, systemNotificationId, actualIntent, intentFlag)
    }

    private fun addMarkAsReadAction(notificationBuilder: NotificationCompat.Builder, systemNotificationId: Int) {
        if (pushMessage.objectId != null) {
            val messageId: Int = try {
                parseMessageId(pushMessage.objectId!!)
            } catch (nfe: NumberFormatException) {
                Log.e(TAG, "Failed to parse messageId from objectId, skip adding mark-as-read action.", nfe)
                return
            }

            val pendingIntent = buildIntentForAction(
                MarkAsReadReceiver::class.java,
                systemNotificationId,
                messageId
            )
            val markAsReadAction = NotificationCompat.Action.Builder(
                R.drawable.ic_eye,
                context!!.resources.getString(R.string.nc_mark_as_read),
                pendingIntent
            )
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build()
            notificationBuilder.addAction(markAsReadAction)
        }
    }

    private fun addReplyAction(notificationBuilder: NotificationCompat.Builder, systemNotificationId: Int) {
        val replyLabel = context!!.resources.getString(R.string.nc_reply)
        val remoteInput = RemoteInput.Builder(NotificationUtils.KEY_DIRECT_REPLY)
            .setLabel(replyLabel)
            .build()

        val replyPendingIntent = buildIntentForAction(
            DirectReplyReceiver::class.java,
            systemNotificationId,
            0
        )
        val replyAction = NotificationCompat.Action.Builder(R.drawable.ic_reply, replyLabel, replyPendingIntent)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .addRemoteInput(remoteInput)
            .build()
        notificationBuilder.addAction(replyAction)
    }

    private fun addDismissRecordingAvailableAction(
        notificationBuilder: NotificationCompat.Builder,
        systemNotificationId: Int,
        ncNotification: com.nextcloud.talk.models.json.notifications.Notification
    ) {
        var dismissLabel = ""
        var dismissRecordingUrl = ""

        for (action in ncNotification.actions!!) {
            if (!action.primary) {
                dismissLabel = action.label.orEmpty()
                dismissRecordingUrl = action.link.orEmpty()
            }
        }

        val dismissIntent = Intent(context, DismissRecordingAvailableReceiver::class.java)
        dismissIntent.putExtra(KEY_SYSTEM_NOTIFICATION_ID, systemNotificationId)
        dismissIntent.putExtra(KEY_DISMISS_RECORDING_URL, dismissRecordingUrl)

        val intentFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(context, systemNotificationId, dismissIntent, intentFlag)

        val dismissAction = NotificationCompat.Action.Builder(R.drawable.ic_delete, dismissLabel, dismissPendingIntent)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
        notificationBuilder.addAction(dismissAction)
    }

    private fun addShareRecordingToChatAction(
        notificationBuilder: NotificationCompat.Builder,
        systemNotificationId: Int,
        ncNotification: com.nextcloud.talk.models.json.notifications.Notification
    ) {
        var shareToChatLabel = ""
        var shareToChatUrl = ""

        for (action in ncNotification.actions!!) {
            if (action.primary) {
                shareToChatLabel = action.label.orEmpty()
                shareToChatUrl = action.link.orEmpty()
            }
        }

        val shareRecordingIntent = Intent(context, ShareRecordingToChatReceiver::class.java)
        shareRecordingIntent.putExtra(KEY_SYSTEM_NOTIFICATION_ID, systemNotificationId)
        shareRecordingIntent.putExtra(KEY_SHARE_RECORDING_TO_CHAT_URL, shareToChatUrl)
        shareRecordingIntent.putExtra(KEY_ROOM_TOKEN, pushMessage.id)

        val intentFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val shareRecordingPendingIntent = PendingIntent.getBroadcast(
            context,
            systemNotificationId,
            shareRecordingIntent,
            intentFlag
        )

        val shareRecordingAction = NotificationCompat.Action.Builder(
            R.drawable.ic_delete,
            shareToChatLabel,
            shareRecordingPendingIntent
        )
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
        notificationBuilder.addAction(shareRecordingAction)
    }

    private fun getStyle(person: Person, style: NotificationCompat.MessagingStyle?): NotificationCompat.MessagingStyle {
        val newStyle = NotificationCompat.MessagingStyle(person)
        newStyle.conversationTitle = pushMessage.subject
        newStyle.isGroupConversation = "one2one" != conversationType
        style?.messages?.forEach(
            Consumer { message: NotificationCompat.MessagingStyle.Message ->
                newStyle.addMessage(
                    NotificationCompat.MessagingStyle.Message(
                        message.text,
                        message.timestamp,
                        message.person
                    )
                )
            }
        )
        newStyle.addMessage(pushMessage.text, pushMessage.timestamp, person)
        return newStyle
    }

    @Throws(NumberFormatException::class)
    private fun parseMessageId(objectId: String): Int {
        val objectIdParts = objectId.split("/".toRegex()).toTypedArray()
        return if (objectIdParts.size < 2) {
            throw NumberFormatException("Invalid objectId, doesn't contain at least one '/'")
        } else {
            objectIdParts[1].toInt()
        }
    }

    private fun sendNotification(notificationId: Int, notification: Notification) {
        Log.d(TAG, "show notification with id $notificationId")
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        notificationManager.notify(notificationId, notification)

        return
    }

    private fun removeNotification(notificationId: Int) {
        Log.d(TAG, "removed notification with id $notificationId")
        notificationManager.cancel(notificationId)
    }

    private fun checkIfCallIsActive(signatureVerification: SignatureVerification, conversation: ConversationModel) {
        Log.d(TAG, "checkIfCallIsActive")
        var hasParticipantsInCall = true
        var inCallOnDifferentDevice = false

        val apiVersion = ApiUtils.getConversationApiVersion(
            signatureVerification.user!!,
            intArrayOf(ApiUtils.API_V4, 1)
        )

        var isCallNotificationVisible = true

        ncApi.getPeersForCall(
            credentials,
            ApiUtils.getUrlForCall(
                apiVersion,
                signatureVerification.user!!.baseUrl!!,
                pushMessage.id!!
            )
        )
            .repeatWhen { completed ->
                completed.zipWith(Observable.range(TIMER_START, TIMER_COUNT)) { _, i -> i }
                    .flatMap { Observable.timer(TIMER_DELAY, TimeUnit.SECONDS) }
                    .takeWhile { isCallNotificationVisible && hasParticipantsInCall && !inCallOnDifferentDevice }
            }
            .subscribeOn(Schedulers.io())
            .subscribe(object : Observer<ParticipantsOverall> {
                override fun onSubscribe(d: Disposable) = Unit

                override fun onNext(participantsOverall: ParticipantsOverall) {
                    val participantList: List<Participant> = participantsOverall.ocs!!.data!!
                    hasParticipantsInCall = participantList.isNotEmpty()
                    if (hasParticipantsInCall) {
                        for (participant in participantList) {
                            if (participant.actorId == signatureVerification.user!!.userId &&
                                participant.actorType == Participant.ActorType.USERS
                            ) {
                                inCallOnDifferentDevice = true
                                break
                            }
                        }
                    }
                    if (inCallOnDifferentDevice) {
                        Log.d(TAG, "inCallOnDifferentDevice is true")
                        removeNotification(pushMessage.timestamp.toInt())
                    }

                    if (!hasParticipantsInCall) {
                        showMissedCallNotification(conversation)
                        Log.d(TAG, "no participants in call")
                        removeNotification(pushMessage.timestamp.toInt())
                    }

                    isCallNotificationVisible = NotificationUtils.isNotificationVisible(
                        context,
                        pushMessage.timestamp.toInt()
                    )
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error in getPeersForCall", e)
                    if (isCallNotificationVisible) {
                        showMissedCallNotification(conversation)
                    }
                    removeNotification(pushMessage.timestamp.toInt())
                }

                override fun onComplete() {
                    if (isCallNotificationVisible) {
                        // this state can be reached when call timeout is reached.
                        showMissedCallNotification(conversation)
                    }

                    removeNotification(pushMessage.timestamp.toInt())
                }
            })
    }

    fun showMissedCallNotification(conversation: ConversationModel) {
        val isOngoingCallNotificationVisible = NotificationUtils.isNotificationVisible(
            context,
            pushMessage.timestamp.toInt()
        )

        if (isOngoingCallNotificationVisible) {
            val notificationBuilder = NotificationCompat.Builder(
                context!!,
                NotificationUtils.NotificationChannels
                    .NOTIFICATION_CHANNEL_MESSAGES_V4.name
            )

            val intent = createMainActivityIntent()

            val notification: Notification = notificationBuilder
                .setContentTitle(
                    String.format(
                        context!!.resources.getString(R.string.nc_missed_call),
                        conversation.displayName
                    )
                )
                .setSmallIcon(R.drawable.ic_baseline_phone_missed_24)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(createUniquePendingIntent(intent))
                .build()

            val notificationId: Int = SystemClock.uptimeMillis().toInt()
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "'you missed a call' notification was created")
        }
    }

    private fun createMainActivityIntent(): Intent {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = getIntentFlags()
        val bundle = Bundle()
        bundle.putString(KEY_ROOM_TOKEN, pushMessage.id)
        bundle.putLong(KEY_INTERNAL_USER_ID, signatureVerification.user!!.id!!)
        intent.putExtras(bundle)
        return intent
    }

    private fun createUniquePendingIntent(intent: Intent): PendingIntent? {
        // Use unique request code to make sure that a new PendingIntent gets created for each notification
        // See https://github.com/nextcloud/talk-android/issues/2111
        val requestCode = System.currentTimeMillis().toInt()
        val intentFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, requestCode, intent, intentFlag)
    }

    private fun getIntentFlags(): Int = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

    companion object {
        val TAG = NotificationWorker::class.simpleName
        private const val TYPE_CHAT = "chat"
        private const val TYPE_ROOM = "room"
        private const val TYPE_CALL = "call"
        private const val TYPE_RECORDING = "recording"
        private const val TYPE_REMOTE_TALK_SHARE = "remote_talk_share"
        private const val TYPE_REMINDER = "reminder"
        private const val TYPE_ADMIN_NOTIFICATIONS = "admin_notifications"
        private const val SPREED_APP = "spreed"
        private const val ADMIN_NOTIFICATION_TALK = "admin_notification_talk"
        private const val TIMER_START = 1
        private const val TIMER_COUNT = 12
        private const val TIMER_DELAY: Long = 5
    }
}
