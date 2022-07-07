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
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SYSTEM_NOTIFICATION_ID
import com.nextcloud.talk.utils.database.user.UserUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class DirectReplyReceiver : BroadcastReceiver() {

    @Inject
    lateinit var userUtils: UserUtils

    @Inject
    lateinit var ncApi: NcApi

    lateinit var context: Context
    lateinit var currentUser: UserEntity
    private var systemNotificationId: Int? = null
    private var roomToken: String? = null
    private var replyMessage: CharSequence? = null

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    override fun onReceive(receiveContext: Context, intent: Intent?) {
        context = receiveContext

        // NOTE - systemNotificationId is an internal ID used on the device only.
        // It is NOT the same as the notification ID used in communication with the server.
        systemNotificationId = intent!!.getIntExtra(KEY_SYSTEM_NOTIFICATION_ID, 0)
        roomToken = intent.getStringExtra(KEY_ROOM_TOKEN)

        val id = intent.getLongExtra(KEY_INTERNAL_USER_ID, userUtils.currentUser!!.getId())
        currentUser = userUtils.getUserWithId(id)

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

        ncApi.sendChatMessage(credentials, url, replyMessage, currentUser.displayName, null, false)
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                @RequiresApi(Build.VERSION_CODES.N)
                override fun onNext(genericOverall: GenericOverall) {
                    confirmReplySent()
                }

                @RequiresApi(Build.VERSION_CODES.N)
                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to send reply", e)
                    informReplyFailed()
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun confirmReplySent() {
        appendMessageToNotification(replyMessage!!)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun informReplyFailed() {
        val errorColor = ForegroundColorSpan(context.resources.getColor(R.color.medium_emphasis_text, context.theme))
        val errorMessageHeader = context.resources.getString(R.string.nc_message_failed_to_send)
        val errorMessage = SpannableStringBuilder().append("$errorMessageHeader\n$replyMessage", errorColor, 0)
        appendMessageToNotification(errorMessage)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun findActiveNotification(notificationId: Int): Notification? {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.activeNotifications.find { it.id == notificationId }?.notification
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun appendMessageToNotification(reply: CharSequence) {
        // Implementation inspired by the SO question and article below:
        // https://stackoverflow.com/questions/51549456/android-o-notification-for-direct-reply-message
        // https://medium.com/@sidorovroman3/android-how-to-use-messagingstyle-for-notifications-without-caching-messages-c414ef2b816c
        //
        // Tries to follow "Best practices for messaging apps" described here:
        // https://developer.android.com/training/notify-user/build-notification#messaging-best-practices

        // Find the original (active) notification
        val previousNotification = findActiveNotification(systemNotificationId!!) ?: return

        // Recreate builder based on the active notification
        val previousBuilder = NotificationCompat.Builder(context, previousNotification)

        // Extract MessagingStyle from the active notification
        val previousStyle = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(previousNotification)

        // Add reply
        val avatarUrl = ApiUtils.getUrlForAvatar(currentUser.baseUrl, currentUser.userId, false)
        val me = Person.Builder()
            .setName(currentUser.displayName)
            .setIcon(NotificationUtils.loadAvatarSync(avatarUrl))
            .build()
        val message = NotificationCompat.MessagingStyle.Message(reply, System.currentTimeMillis(), me)
        previousStyle?.addMessage(message)

        // Set the updated style
        previousBuilder.setStyle(previousStyle)

        // Update the active notification.
        NotificationManagerCompat.from(context).notify(systemNotificationId!!, previousBuilder.build())
    }

    companion object {
        const val TAG = "DirectReplyReceiver"
    }
}
