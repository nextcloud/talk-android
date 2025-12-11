/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.services.CallForegroundService

class EndCallReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "EndCallReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "com.nextcloud.talk.END_CALL") {
            Log.d(TAG, "Received end call broadcast")
            
            // Stop the foreground service
            context?.let {
                CallForegroundService.stop(it)
                
                // Send broadcast to CallActivity to end the call
                val endCallIntent = Intent("com.nextcloud.talk.END_CALL_FROM_NOTIFICATION")
                endCallIntent.setPackage(context.packageName)
                context.sendBroadcast(endCallIntent)
            }
        }
    }
}