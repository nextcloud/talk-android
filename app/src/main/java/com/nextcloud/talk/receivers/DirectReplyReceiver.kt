/*
 * Nextcloud Talk application
 *
 * @author Dariusz Olszewski
 * Copyright (C) 2022 Dariusz Olszewski <starypatyk@gmail.com>
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

package com.nextcloud.talk.receivers

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import autodagger.AutoInjector
import com.facebook.common.executors.UiThreadImmediateExecutorService
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.facebook.imagepipeline.postprocessors.RoundAsCirclePostprocessor
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_NOTIFICATION_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.database.user.UserUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class DirectReplyReceiver : BroadcastReceiver() {

    @Inject
    @JvmField
    var userUtils: UserUtils? = null

    @Inject
    @JvmField
    var ncApi: NcApi? = null

    lateinit var context: Context
    lateinit var currentUser: UserEntity
    private var notificationId: Int? = null
    private var roomToken: String? = null
    private var replyMessage: CharSequence? = null

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    override fun onReceive(receiveContext: Context, intent: Intent?) {
        context = receiveContext
        currentUser = userUtils!!.currentUser!!

        // NOTE - This notificationId is an internal ID used on the device only.
        // It is NOT the same as the notification ID used in communication with the server.
        notificationId = intent!!.getIntExtra(KEY_NOTIFICATION_ID, 0)
        roomToken = intent.getStringExtra(KEY_ROOM_TOKEN)

        replyMessage = getMessageText(intent)
        sendDirectReply()
    }

    private fun getMessageText(intent: Intent): CharSequence? {
        return RemoteInput.getResultsFromIntent(intent)?.getCharSequence(NotificationUtils.KEY_DIRECT_REPLY)
    }

    private fun sendDirectReply() {
        val credentials = ApiUtils.getCredentials(currentUser.username, currentUser.token)
        val apiVersion = ApiUtils.getChatApiVersion(currentUser, intArrayOf(1))
        val url = ApiUtils.getUrlForChat(apiVersion, currentUser.baseUrl, roomToken)

        ncApi!!.sendChatMessage(credentials, url, replyMessage, currentUser.displayName, null)
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                @RequiresApi(Build.VERSION_CODES.N)
                override fun onNext(genericOverall: GenericOverall) {
                    loadAvatar(::confirmReplySent)
                }

                override fun onError(e: Throwable) {
                    // TODO - inform the user that sending of the reply failed
                    // unused atm
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun loadAvatar(callback: (avatarIcon: IconCompat) -> Unit) {
        val avatarUrl = ApiUtils.getUrlForAvatar(currentUser.baseUrl, currentUser.userId, false)
        val imageRequest = DisplayUtils.getImageRequestForUrl(avatarUrl, currentUser)
        val dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, null)
        dataSource.subscribe(object : BaseBitmapDataSubscriber() {
            override fun onNewResultImpl(bitmap: Bitmap?) {
                if (bitmap != null) {
                    RoundAsCirclePostprocessor(true).process(bitmap)
                    callback(IconCompat.createWithBitmap(bitmap))
                }
            }

            override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage?>>) {
                // unused atm
            }
        }, UiThreadImmediateExecutorService.getInstance())
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun findActiveNotification(notificationId: Int): Notification? {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.activeNotifications.find { it.id == notificationId }?.notification
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun confirmReplySent(avatarIcon: IconCompat) {
        // Implementation inspired by the SO question and article below:
        // https://stackoverflow.com/questions/51549456/android-o-notification-for-direct-reply-message
        // https://medium.com/@sidorovroman3/android-how-to-use-messagingstyle-for-notifications-without-caching-messages-c414ef2b816c
        //
        // Tries to follow "Best practices for messaging apps" described here:
        // https://developer.android.com/training/notify-user/build-notification#messaging-best-practices

        // Find the original (active) notification
        val previousNotification = findActiveNotification(notificationId!!) ?: return

        // Recreate builder based on the active notification
        val previousBuilder = NotificationCompat.Builder(context, previousNotification)

        // Extract MessagingStyle from the active notification
        val previousStyle = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(previousNotification)

        // Add reply
        val me = Person.Builder()
            .setName(currentUser.displayName)
            // .setIcon(IconCompat.createWithResource(context, R.drawable.ic_user))
            .setIcon(avatarIcon)
            .build()
        val message = NotificationCompat.MessagingStyle.Message(replyMessage, System.currentTimeMillis(), me)
        previousStyle?.addMessage(message)

        // Set the updated style
        previousBuilder.setStyle(previousStyle)

        // Update the active notification.
        NotificationManagerCompat.from(context).notify(notificationId!!, previousBuilder.build())
    }

}
