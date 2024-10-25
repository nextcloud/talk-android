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
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ApiUtils.getConversationApiVersion
import com.nextcloud.talk.utils.ApiUtils.getCredentials
import com.nextcloud.talk.utils.ApiUtils.getUrlForParticipantsSelf
import com.nextcloud.talk.utils.bundle.BundleKeys
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class LeaveConversationWorker(val context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager


    override fun doWork(): Result {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        val data = inputData
        val conversationToken = data.getString(BundleKeys.KEY_ROOM_TOKEN)
        val currentUser = userManager.currentUser.blockingGet()
        lateinit var workResult:Result

        if (currentUser != null) {
            val credentials = getCredentials(currentUser.username, currentUser.token)

            val apiVersion = getConversationApiVersion(currentUser, intArrayOf(ApiUtils.API_V4, 1))

            ncApi.removeSelfFromRoom(
                credentials, getUrlForParticipantsSelf(
                    apiVersion,
                    currentUser.baseUrl,
                    conversationToken
                )
            )
                .subscribeOn(Schedulers.io())
                .subscribe(object : Observer<GenericOverall?> {

                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onNext(p0: GenericOverall) {

                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "failed to remove self from room", e)
                        if(e.message?.contains("HTTP 400") == true){
                            workResult = Result.failure()
                        }
                    }

                    override fun onComplete() {
                        workResult = Result.success()

                    }
                })
        }else{

            workResult = Result.failure()
        }

        return workResult
    }

    companion object {
        private const val TAG = "LeaveConversationWorker"
    }
}
