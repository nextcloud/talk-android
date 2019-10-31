/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat.Builder
import androidx.core.app.NotificationCompat.MessagingStyle
import androidx.core.app.NotificationCompat.MessagingStyle.Message
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.emoji.text.EmojiCompat
import androidx.work.ListenableWorker.Result
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import coil.Coil
import coil.target.Target
import coil.transform.CircleCropTransformation
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R.color
import com.nextcloud.talk.R.dimen
import com.nextcloud.talk.R.drawable
import com.nextcloud.talk.R.string
import com.nextcloud.talk.activities.MagicCallActivity
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.models.SignatureVerification
import com.nextcloud.talk.models.database.ArbitraryStorageEntity
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.chat.ChatUtils
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.Conversation.ConversationType.GROUP_CONVERSATION
import com.nextcloud.talk.models.json.conversations.Conversation.ConversationType.ONE_TO_ONE_CONVERSATION
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.notifications.NotificationOverall
import com.nextcloud.talk.models.json.push.DecryptedPushMessage
import com.nextcloud.talk.models.json.push.NotificationUser
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.local.models.hasSpreedFeatureCapability
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.newarch.utils.getCredentials
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DoNotDisturbUtils.isDnDActive
import com.nextcloud.talk.utils.DoNotDisturbUtils.isInDoNotDisturbWithPriority
import com.nextcloud.talk.utils.DoNotDisturbUtils.shouldPlaySound
import com.nextcloud.talk.utils.DoNotDisturbUtils.shouldVibrate
import com.nextcloud.talk.utils.NotificationUtils.NOTIFICATION_CHANNEL_CALLS_V3
import com.nextcloud.talk.utils.NotificationUtils.NOTIFICATION_CHANNEL_MESSAGES_V3
import com.nextcloud.talk.utils.NotificationUtils.cancelAllNotificationsForAccount
import com.nextcloud.talk.utils.NotificationUtils.cancelExistingNotificationWithId
import com.nextcloud.talk.utils.NotificationUtils.createNotificationChannel
import com.nextcloud.talk.utils.NotificationUtils.findNotificationForRoom
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FROM_NOTIFICATION_START_CALL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_NOTIFICATION_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_NOTIFICATION_SIGNATURE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_NOTIFICATION_SUBJECT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.database.arbitrarystorage.ArbitraryStorageUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.parceler.Parcels
import retrofit2.Retrofit
import java.io.IOException
import java.net.CookieManager
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.util.HashMap
import java.util.function.Consumer
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.NoSuchPaddingException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class NotificationWorker(
  context: Context,
  workerParams: WorkerParameters
) : Worker(context, workerParams), KoinComponent {

  val appPreferences: AppPreferences by inject()
  val retrofit: Retrofit by inject()
  val okHttpClient: OkHttpClient by inject()
  val usersRepository: UsersRepository by inject()

  @JvmField
  @Inject
  var arbitraryStorageUtils: ArbitraryStorageUtils? = null
  private var ncApi: NcApi? = null
  private var decryptedPushMessage: DecryptedPushMessage? = null
  private var context: Context? = null
  private var signatureVerification: SignatureVerification? = null
  private var conversationType: String? = "one2one"
  private var credentials: String? = null
  private var muteCall = false
  private var importantConversation = false


  private fun showNotificationForCallWithNoPing(intent: Intent) {
    val userEntity: UserNgEntity =
      signatureVerification!!.userEntity
    var arbitraryStorageEntity: ArbitraryStorageEntity?

    arbitraryStorageEntity = arbitraryStorageUtils!!.getStorageSetting(
        userEntity.id,
        "mute_calls",
        intent.extras!!.getString(KEY_ROOM_TOKEN)
    )

    if (arbitraryStorageEntity != null) {
      muteCall = arbitraryStorageEntity.value!!.toBoolean()
    }

    arbitraryStorageEntity = arbitraryStorageUtils!!.getStorageSetting(
        userEntity.id,
        "important_conversation",
        intent.extras!!.getString(KEY_ROOM_TOKEN)
    )

    if (arbitraryStorageEntity != null) {
      importantConversation = arbitraryStorageEntity.value!!.toBoolean()
    }

    if (isDnDActive()) {
      if (!isInDoNotDisturbWithPriority()
          || !importantConversation
          || muteCall
      ) {
        return
      }
    }
    ncApi!!.getRoom(
        credentials, ApiUtils.getRoom(
        userEntity.baseUrl,
        intent.extras!!.getString(KEY_ROOM_TOKEN)
    )
    )
        .blockingSubscribe(object : Observer<RoomOverall?> {
          override fun onSubscribe(d: Disposable) {}
          override fun onNext(roomOverall: RoomOverall) {
            val conversation: Conversation =
              roomOverall.ocs.data
            intent.putExtra(
                KEY_ROOM,
                Parcels.wrap(
                    conversation
                )
            )
            if ((conversation.type
                    == ONE_TO_ONE_CONVERSATION) ||
                !TextUtils.isEmpty(
                    conversation.objectType
                ) && "share:password" == conversation.objectType
            ) {
              context!!.startActivity(intent)
            } else {
              conversationType = if (conversation.type == GROUP_CONVERSATION) {
                "group"
              } else {
                "public"
              }
              if (decryptedPushMessage!!.notificationId != Long.MIN_VALUE) {
                showNotificationWithObjectData(intent)
              } else {
                showNotification(intent)
              }
            }
          }

          override fun onError(e: Throwable) {}
          override fun onComplete() {}
        })
  }

  private fun showNotificationWithObjectData(intent: Intent) {
    val userEntity: UserNgEntity =
      signatureVerification!!.userEntity
    ncApi!!.getNotification(
        credentials, ApiUtils.getUrlForNotificationWithId(
        userEntity.baseUrl,
        decryptedPushMessage!!.notificationId.toString()
    )
    )
        .blockingSubscribe(object : Observer<NotificationOverall?> {
          override fun onSubscribe(d: Disposable) {}
          override fun onNext(notificationOverall: NotificationOverall) {
            val notification: com.nextcloud.talk.models.json.notifications.Notification =
              notificationOverall.ocs
                  .notification
            if (notification.messageRichParameters != null &&
                notification.messageRichParameters.size > 0
            ) {
              decryptedPushMessage!!.text = ChatUtils.getParsedMessage(
                  notification.messageRich,
                  notification.messageRichParameters
              )
            } else {
              decryptedPushMessage!!.text = notification.message
            }
            val subjectRichParameters: HashMap<String, HashMap<String, String>>? =
              notification
                  .subjectRichParameters
            decryptedPushMessage!!.timestamp = notification.datetime.millis
            if (subjectRichParameters != null && subjectRichParameters.size > 0) {
              val callHashMap =
                subjectRichParameters["call"]
              val userHashMap =
                subjectRichParameters["user"]
              val guestHashMap =
                subjectRichParameters["guest"]
              if (callHashMap != null && callHashMap.size > 0 && callHashMap.containsKey(
                      "name"
                  )
              ) {
                if (notification.objectType == "chat") {
                  decryptedPushMessage!!.subject = callHashMap["name"]
                } else {
                  decryptedPushMessage!!.subject = notification.subject
                }
                if (callHashMap.containsKey("call-type")) {
                  conversationType = callHashMap["call-type"]
                }
              }
              val notificationUser =
                NotificationUser()
              if (userHashMap != null && !userHashMap.isEmpty()) {
                notificationUser.id = userHashMap["id"]
                notificationUser.type = userHashMap["type"]
                notificationUser.name = userHashMap["name"]
                decryptedPushMessage!!.notificationUser = notificationUser
              } else if (guestHashMap != null && !guestHashMap.isEmpty()) {
                notificationUser.id = guestHashMap["id"]
                notificationUser.type = guestHashMap["type"]
                notificationUser.name = guestHashMap["name"]
                decryptedPushMessage!!.notificationUser = notificationUser
              }
            }
            showNotification(intent)
          }

          override fun onError(e: Throwable) {}
          override fun onComplete() {}
        })
  }

  private fun showNotification(intent: Intent) {
    val smallIcon: Int
    val largeIcon: Bitmap?
    val category: String
    val priority = Notification.PRIORITY_HIGH
    smallIcon = drawable.ic_logo
    category = if (decryptedPushMessage!!.type == "chat" || (decryptedPushMessage!!.type
            == "room")
    ) {
      Notification.CATEGORY_MESSAGE
    } else {
      Notification.CATEGORY_CALL
    }
    largeIcon = when (conversationType) {
      "group" -> BitmapFactory.decodeResource(
          context!!.resources,
          drawable.ic_people_group_black_24px
      )
      "public" -> BitmapFactory.decodeResource(
          context!!.resources, drawable.ic_link_black_24px
      )
      else -> // assuming one2one
        if (decryptedPushMessage!!.type == "chat" || (decryptedPushMessage!!.type
                == "room")
        ) {
          BitmapFactory.decodeResource(
              context!!.resources, drawable.ic_chat_black_24dp
          )
        } else {
          BitmapFactory.decodeResource(
              context!!.resources, drawable.ic_call_black_24dp
          )
        }
    }
    intent.action = System.currentTimeMillis()
        .toString()
    val pendingIntent: PendingIntent? = PendingIntent.getActivity(
        context,
        0, intent, 0
    )
    val uri: Uri =
      Uri.parse(signatureVerification!!.userEntity.baseUrl)
    val baseUrl = uri.host
    val notificationBuilder: Builder =
      Builder(context!!, "1")
          .setLargeIcon(largeIcon)
          .setSmallIcon(smallIcon)
          .setCategory(category)
          .setPriority(priority)
          .setSubText(baseUrl)
          .setWhen(decryptedPushMessage!!.timestamp)
          .setShowWhen(true)
          .setContentTitle(
              EmojiCompat.get().process(decryptedPushMessage!!.subject)
          )
          .setContentIntent(pendingIntent)
          .setAutoCancel(true)
    if (!TextUtils.isEmpty(decryptedPushMessage!!.text)) {
      notificationBuilder.setContentText(
          EmojiCompat.get().process(decryptedPushMessage!!.text)
      )
    }
    if (VERSION.SDK_INT >= 23) {
      // This method should exist since API 21, but some phones don't have it
      // So as a safeguard, we don't use it until 23

      notificationBuilder.color = context!!.resources.getColor(color.colorPrimary)
    }
    val notificationInfo = Bundle()
    notificationInfo.putLong(
        KEY_INTERNAL_USER_ID,
        signatureVerification!!.userEntity.id
    )
    // could be an ID or a TOKEN

    notificationInfo.putString(
        KEY_ROOM_TOKEN,
        decryptedPushMessage!!.id
    )
    notificationInfo.putLong(
        KEY_NOTIFICATION_ID,
        decryptedPushMessage!!.notificationId
    )
    notificationBuilder.extras = notificationInfo
    if (VERSION.SDK_INT >= VERSION_CODES.O) {

      /*NotificationUtils.createNotificationChannelGroup(context,
                    Long.toString(crc32.getValue()),
                    groupName);*/

      if (decryptedPushMessage!!.type == "chat" || (decryptedPushMessage!!.type
              == "room")
      ) {
        createNotificationChannel(
            context!!,
            NOTIFICATION_CHANNEL_MESSAGES_V3,
            context!!.resources
                .getString(string.nc_notification_channel_messages),
            context!!.resources
                .getString(string.nc_notification_channel_messages), true,
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationBuilder.setChannelId(
            NOTIFICATION_CHANNEL_MESSAGES_V3
        )
      } else {
        createNotificationChannel(
            context!!,
            NOTIFICATION_CHANNEL_CALLS_V3,
            context!!.resources
                .getString(string.nc_notification_channel_calls),
            context!!.resources
                .getString(string.nc_notification_channel_calls_description),
            true,
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationBuilder.setChannelId(
            NOTIFICATION_CHANNEL_CALLS_V3
        )
      }
    } else {
      // red color for the lights

      notificationBuilder.setLights(-0x10000, 200, 200)
    }
    notificationBuilder.setContentIntent(pendingIntent)
    var crc32 = CRC32()
    val groupName =
      signatureVerification!!.userEntity.id.toString() + "@" + decryptedPushMessage!!.id
    crc32.update(groupName.toByteArray())
    notificationBuilder.setGroup(crc32.value.toString())

    // notificationId

    crc32 = CRC32()
    val stringForCrc = System.currentTimeMillis()
        .toString()
    crc32.update(stringForCrc.toByteArray())
    val activeStatusBarNotification =
      findNotificationForRoom(
          context,
          signatureVerification!!.userEntity, decryptedPushMessage!!.id
      )
    val notificationId: Int
    notificationId = activeStatusBarNotification?.id ?: crc32.value
        .toInt()
    if (VERSION.SDK_INT >= VERSION_CODES.N && decryptedPushMessage!!.notificationUser != null && decryptedPushMessage!!.type == "chat") {
      var style: MessagingStyle? = null
      if (activeStatusBarNotification != null) {
        val activeNotification: Notification? =
          activeStatusBarNotification.notification
        style =
          MessagingStyle.extractMessagingStyleFromNotification(
              activeNotification
          )
      }
      val person = Person.Builder()
          .setKey(
              signatureVerification!!.userEntity.id.toString() +
                  "@" + decryptedPushMessage!!.notificationUser.id
          )
          .setName(
              EmojiCompat.get().process(
                  decryptedPushMessage!!.notificationUser.name
              )
          )
          .setBot(decryptedPushMessage!!.notificationUser.type == "bot")
      notificationBuilder.setOnlyAlertOnce(true)
      if (decryptedPushMessage!!.notificationUser.type == "user" || decryptedPushMessage!!.notificationUser.type == "guest") {
        var avatarUrl: String? = ApiUtils.getUrlForAvatarWithName(
            signatureVerification!!.userEntity.baseUrl,
            decryptedPushMessage!!.notificationUser.id,
            dimen.avatar_size
        )
        if (decryptedPushMessage!!.notificationUser.type == "guest") {
          avatarUrl = ApiUtils.getUrlForAvatarWithNameForGuests(
              signatureVerification!!.userEntity.baseUrl,
              decryptedPushMessage!!.notificationUser.name,
              dimen.avatar_size
          )
        }

        val target = object : Target {
          override fun onSuccess(result: Drawable) {
            super.onSuccess(result)
            person.setIcon(IconCompat.createWithBitmap(result.toBitmap()))
            notificationBuilder.setStyle(
                getStyle(
                    person.build(),
                    style
                )
            )
            sendNotificationWithId(notificationId, notificationBuilder.build())
          }

          override fun onError(error: Drawable?) {
            super.onError(error)
            notificationBuilder.setStyle(getStyle(person.build(), style))
            sendNotificationWithId(notificationId, notificationBuilder.build())
          }

        }

        val request = Images().getRequestForUrl(
            Coil.loader(), context!!, avatarUrl!!, null,
            target, null, CircleCropTransformation()
        )

        Coil.loader()
            .load(request)
      } else {
        notificationBuilder.setStyle(getStyle(person.build(), style))
        sendNotificationWithId(notificationId, notificationBuilder.build())
      }
    } else {
      sendNotificationWithId(notificationId, notificationBuilder.build())
    }
  }

  private fun getStyle(
    person: Person,
    style: MessagingStyle?
  ): MessagingStyle? {
    if (VERSION.SDK_INT >= VERSION_CODES.N) {
      val newStyle =
        MessagingStyle(person)
      newStyle.conversationTitle = decryptedPushMessage!!.subject
      newStyle.isGroupConversation = conversationType != "one2one"
      style?.messages?.forEach(
          Consumer { message: Message ->
            newStyle.addMessage(
                Message(
                    message.text,
                    message.timestamp, message.person
                )
            )
          }
      )
      newStyle.addMessage(
          decryptedPushMessage!!.text, decryptedPushMessage!!.timestamp,
          person
      )
      return newStyle
    }

    // we'll never come here

    return style
  }

  private fun sendNotificationWithId(
    notificationId: Int,
    notification: Notification
  ) {
    val notificationManager =
      NotificationManagerCompat.from(context!!)
    notificationManager.notify(notificationId, notification)
    if (notification.category != Notification.CATEGORY_CALL) {
      val ringtonePreferencesString: String?
      val soundUri: Uri?
      ringtonePreferencesString = appPreferences!!.messageRingtoneUri
      soundUri = if (TextUtils.isEmpty(ringtonePreferencesString)) {
        Uri.parse(
            "android.resource://" + context!!.packageName +
                "/raw/librem_by_feandesign_message"
        )
      } else {
        try {
          val ringtoneSettings: RingtoneSettings =
            LoganSquare.parse(
                ringtonePreferencesString, RingtoneSettings::class.java
            )
          ringtoneSettings.ringtoneUri
        } catch (exception: IOException) {
          Uri.parse(
              "android.resource://" + context!!.packageName +
                  "/raw/librem_by_feandesign_message"
          )
        }
      }

      if (soundUri != null && (shouldPlaySound(importantConversation))
      ) {
        val audioAttributesBuilder: AudioAttributes.Builder =
          AudioAttributes.Builder()
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        if (decryptedPushMessage!!.type == "chat" || (decryptedPushMessage!!.type
                == "room")
        ) {
          audioAttributesBuilder.setUsage(
              AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT
          )
        } else {
          audioAttributesBuilder.setUsage(
              AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST
          )
        }
        val mediaPlayer = MediaPlayer()
        try {
          mediaPlayer.setDataSource(context!!, soundUri)
          mediaPlayer.setAudioAttributes(audioAttributesBuilder.build())
          mediaPlayer.setOnPreparedListener { mp: MediaPlayer? -> mediaPlayer.start() }
          mediaPlayer.setOnCompletionListener { obj: MediaPlayer -> obj.release() }
          mediaPlayer.prepareAsync()
        } catch (e: IOException) {
          Log.e(TAG, "Failed to set data source")
        }
      }
      if (shouldVibrate(appPreferences!!.shouldVibrateSetting)
      ) {
        val vibrator =
          context!!.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (VERSION.SDK_INT >= VERSION_CODES.O) {
          vibrator.vibrate(
              VibrationEffect.createOneShot(
                  500, VibrationEffect.DEFAULT_AMPLITUDE
              )
          )
        } else {
          vibrator.vibrate(500)
        }
      }
    }
  }

  override fun doWork(): Result {
    sharedApplication!!.componentApplication.inject(this)
    context = applicationContext
    val data = inputData
    val subject =
      data.getString(KEY_NOTIFICATION_SUBJECT)
    val signature =
      data.getString(KEY_NOTIFICATION_SIGNATURE)
    val base64DecodedSubject: ByteArray = Base64.decode(subject, Base64.DEFAULT)
    val base64DecodedSignature: ByteArray = Base64.decode(signature, Base64.DEFAULT)
    val pushUtils = PushUtils(usersRepository)
    val privateKey = pushUtils.readKeyFromFile(false) as PrivateKey
    try {
      signatureVerification = pushUtils.verifySignature(
          base64DecodedSignature,
          base64DecodedSubject
      )
      if (signatureVerification!!.signatureValid) {
        val cipher: Cipher = Cipher.getInstance("RSA/None/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val decryptedSubject: ByteArray? = cipher.doFinal(base64DecodedSubject)
        decryptedPushMessage =
          LoganSquare.parse(
              decryptedSubject!!.toString(Charsets.UTF_8),
              DecryptedPushMessage::class.java
          )
        decryptedPushMessage!!.timestamp = System.currentTimeMillis()
        if (decryptedPushMessage!!.delete) {
          cancelExistingNotificationWithId(
              context,
              signatureVerification!!.userEntity, decryptedPushMessage!!.notificationId
          )
        } else if (decryptedPushMessage!!.deleteAll) {
          cancelAllNotificationsForAccount(
              context,
              signatureVerification!!.userEntity
          )
        } else {
          credentials = signatureVerification!!.userEntity.getCredentials()

          ncApi = retrofit!!.newBuilder()
              .client(
                  okHttpClient!!.newBuilder().cookieJar(
                      JavaNetCookieJar(CookieManager())
                  ).build()
              )
              .build()
              .create(
                  NcApi::class.java
              )
          val hasChatSupport =
            signatureVerification!!.userEntity.hasSpreedFeatureCapability("chat-v2")
          val shouldShowNotification = decryptedPushMessage!!.app == "spreed"
          if (shouldShowNotification) {
            val intent: Intent
            val bundle = Bundle()
            val startACall =
              decryptedPushMessage!!.type == "call" || !hasChatSupport
            intent = if (startACall) {
              Intent(
                  context, MagicCallActivity::class.java
              )
            } else {
              Intent(
                  context, MainActivity::class.java
              )
            }
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            if (!signatureVerification!!.userEntity.hasSpreedFeatureCapability("no-ping")) {
              bundle.putString(
                  KEY_ROOM_ID,
                  decryptedPushMessage!!.id
              )
            } else {
              bundle.putString(
                  KEY_ROOM_TOKEN,
                  decryptedPushMessage!!.id
              )
            }
            bundle.putParcelable(
                KEY_USER_ENTITY,
                signatureVerification!!.userEntity
            )
            bundle.putBoolean(
                KEY_FROM_NOTIFICATION_START_CALL,
                startACall
            )
            intent.putExtras(bundle)
            when (decryptedPushMessage!!.type) {
              "call" -> if (!bundle.containsKey(
                      KEY_ROOM_TOKEN
                  )
              ) {
                context!!.startActivity(intent)
              } else {
                showNotificationForCallWithNoPing(intent)
              }
              "room" -> if (bundle.containsKey(
                      KEY_ROOM_TOKEN
                  )
              ) {
                showNotificationWithObjectData(intent)
              }
              "chat" -> if (decryptedPushMessage!!.notificationId != Long.MIN_VALUE) {
                showNotificationWithObjectData(intent)
              } else {
                showNotification(intent)
              }
              else -> {
              }
            }
          }
        }
      }
    } catch (e1: NoSuchAlgorithmException) {
      Log.d(
          TAG,
          "No proper algorithm to decrypt the message " + e1.localizedMessage
      )
    } catch (e1: NoSuchPaddingException) {
      Log.d(
          TAG,
          "No proper padding to decrypt the message " + e1.localizedMessage
      )
    } catch (e1: InvalidKeyException) {
      Log.d(
          TAG, "Invalid private key " + e1.localizedMessage
      )
    }
    return Result.success()
  }

  companion object {
    const val TAG = "NotificationWorker"
  }
}