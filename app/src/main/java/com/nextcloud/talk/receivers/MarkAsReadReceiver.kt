/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Dariusz Olszewski
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_MESSAGE_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SYSTEM_NOTIFICATION_ID
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MarkAsReadReceiver : BroadcastReceiver() {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var ncApi: NcApi

    lateinit var context: Context
    lateinit var currentUser: User
    private var systemNotificationId: Int? = null
    private var roomToken: String? = null
    private var messageId: Int = 0

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    override fun onReceive(receiveContext: Context, intent: Intent?) {
        context = receiveContext

        // NOTE - systemNotificationId is an internal ID used on the device only.
        // It is NOT the same as the notification ID used in communication with the server.
        systemNotificationId = intent!!.getIntExtra(KEY_SYSTEM_NOTIFICATION_ID, 0)
        roomToken = intent.getStringExtra(KEY_ROOM_TOKEN)
        messageId = intent.getIntExtra(KEY_MESSAGE_ID, 0)

        val id = intent.getLongExtra(KEY_INTERNAL_USER_ID, userManager.currentUser.blockingGet().id!!)
        currentUser = userManager.getUserWithId(id).blockingGet()

        markAsRead()
    }

    private fun markAsRead() {
        val credentials = ApiUtils.getCredentials(currentUser.username, currentUser.token)
        val apiVersion = ApiUtils.getChatApiVersion(currentUser, intArrayOf(1))
        val url = ApiUtils.getUrlForChatReadMarker(
            apiVersion,
            currentUser.baseUrl,
            roomToken
        )

        ncApi.setChatReadMarker(credentials, url, messageId)
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                @RequiresApi(Build.VERSION_CODES.N)
                override fun onNext(genericOverall: GenericOverall) {
                    cancelNotification(systemNotificationId!!)
                }

                @RequiresApi(Build.VERSION_CODES.N)
                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to set chat read marker", e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun cancelNotification(notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    companion object {
        const val TAG = "MarkAsReadReceiver"
    }
}
