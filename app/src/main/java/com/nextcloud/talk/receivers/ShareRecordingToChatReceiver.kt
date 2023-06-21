/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022-2023 Marcel Hibbe <dev@mhibbe.de>
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
import android.util.Log
import android.widget.Toast
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SYSTEM_NOTIFICATION_ID
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ShareRecordingToChatReceiver : BroadcastReceiver() {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var ncApi: NcApi

    lateinit var context: Context
    lateinit var currentUser: User
    private var systemNotificationId: Int? = null
    private var link: String? = null

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    override fun onReceive(receiveContext: Context, intent: Intent?) {
        context = receiveContext
        systemNotificationId = intent!!.getIntExtra(KEY_SYSTEM_NOTIFICATION_ID, 0)
        link = intent.getStringExtra(BundleKeys.KEY_SHARE_RECORDING_TO_CHAT_URL)

        val id = intent.getLongExtra(KEY_INTERNAL_USER_ID, userManager.currentUser.blockingGet().id!!)
        currentUser = userManager.getUserWithId(id).blockingGet()

        shareRecordingToChat()
    }

    private fun shareRecordingToChat() {
        val credentials = ApiUtils.getCredentials(currentUser.username, currentUser.token)

        ncApi.sendCommonPostRequest(credentials, link)
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(genericOverall: GenericOverall) {
                    cancelNotification(systemNotificationId!!)

                    // Here it would make sense to open the chat where the recording was shared to (startActivity...).
                    // However, as we are in a broadcast receiver, this needs a TaskStackBuilder
                    // combined with addNextIntentWithParentStack. For further reading, see
                    // https://developer.android.com/develop/ui/views/notifications/navigation#DirectEntry
                    // As we are using the conductor framework it might be hard the combine this or to keep an overview.
                    // For this reason there is only a toast for now until we got rid of conductor.

                    Toast.makeText(
                        context,
                        context.resources.getString(R.string.nc_all_ok_operation),
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to share recording to chat request", e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun cancelNotification(notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    companion object {
        private val TAG = ShareRecordingToChatReceiver::class.java.simpleName
    }
}
