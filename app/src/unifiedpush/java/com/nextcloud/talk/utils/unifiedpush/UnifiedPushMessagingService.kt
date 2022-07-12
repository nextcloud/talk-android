/*
 * Nextcloud Talk application
 *
 * @author Jindrich Kolman
 * Copyright (C) 2022 Jindrich Kolman <kolman.jindrich@gmail.com>
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

package com.nextcloud.talk.utils.unifiedpush

import android.annotation.SuppressLint
import android.app.job.JobParameters
import android.app.job.JobService
import android.net.Uri
import android.os.*
import android.util.Log
import androidx.work.Configuration
import com.nextcloud.talk.application.NextcloudTalkApplication
import org.unifiedpush.android.connector.ACTION_MESSAGE
import org.unifiedpush.android.connector.ACTION_NEW_ENDPOINT
import org.unifiedpush.android.connector.EXTRA_ENDPOINT
import org.unifiedpush.android.connector.EXTRA_MESSAGE

@SuppressLint("LongLogTag")
open class UnifiedPushMessagingService : JobService() {
    var isServiceInForeground: Boolean = false
    private var params: JobParameters? = null

    open fun onNewToken(token: String) {}
    open fun onMessageReceived(remoteMessage: RemoteMessage) {}

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "onStartJob")
        this.params = params
        params?.let {
            handleMessage(
                it.extras.getString("action")!!,
                it.extras.getString(EXTRA_MESSAGE),
                it.extras.getString(EXTRA_ENDPOINT)
            )
        }
        return isServiceInForeground
    }

    private fun handleMessage(action: String, message: String?, endpoint: String?) {
        when (action) {
            ACTION_NEW_ENDPOINT -> {
                onNewEndpoint(endpoint!!)
            }
            ACTION_MESSAGE -> {
                onMessage(message!!)
            }
        }
    }

    private fun onMessage(message: String) {
        try {
            onMessageReceived(
                RemoteMessage().fromJson(message)
            )
        } catch (e: RemoteMessageJsonParseException) {
            Log.d(TAG, "while parsing received message as JSON: $e")
        }
        checkForeground()
    }

    private fun checkForeground() {
        Log.d(TAG, "checkForeground")
        val handler = Handler()

        if (isServiceInForeground) {
            handler.postDelayed({ checkForeground() }, 10000)
        } else {
            jobFinished(this.params, false)
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Not yet implemented")
        stopForeground(true)
        isServiceInForeground = false
        return true
    }

    open fun onNewEndpoint(endpoint: String) {
        Log.d(TAG, "New Endpoint: $endpoint")
        val uri: Uri = Uri.parse(endpoint)
        val protocol: String? = uri.scheme
        val server: String? = uri.authority
        val path: String? = uri.path
        val args: Set<String> = uri.queryParameterNames
        Log.d(UnifiedPushMessagingReceiver.TAG, "Parsed Endpoint $protocol $server $path")
        var token: String? = null
        if (path == "/UP") {
            setPushServerUrl("$protocol://$server$path")
            token = uri.getQueryParameter("token")
        }
        if (token is String) {
            onNewToken(token)
        }
    }

    private fun setPushServerUrl(url: String) {
        NextcloudTalkApplication.sharedApplication!!.appPreferences.pushServerUrl = url
    }

    companion object {
        const val TAG = "UnifiedPushMessagingService"
    }
}
