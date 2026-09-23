/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class WebsocketConnectionsWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var userManager: UserManager

    override suspend fun doWork(): Result {
        Log.d(TAG, "WebsocketConnectionsWorker started ")

        sharedApplication!!.componentApplication.inject(this)

        for (user in userManager.getUsers()) {
            val externalSignalingServer = user.externalSignalingServer?.externalSignalingServer
            val externalSignalingTicket = user.externalSignalingServer?.externalSignalingTicket
            if (!externalSignalingServer.isNullOrEmpty() && !externalSignalingTicket.isNullOrEmpty()) {
                Log.d(TAG, "trying to getExternalSignalingInstanceForServer for user " + user.displayName)

                WebSocketConnectionHelper.getExternalSignalingInstanceForServer(
                    externalSignalingServer,
                    user,
                    externalSignalingTicket,
                    false
                )
            } else {
                Log.d(TAG, "skipped to getExternalSignalingInstanceForServer for user " + user.displayName)
            }
        }

        return Result.success()
    }

    companion object {
        const val TAG = "WebsocketConnectionsWorker"
    }
}
