/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.window.SplashScreen
import com.nextcloud.talk.activities.DeepLinkRoutingViewModel.Request
import com.nextcloud.talk.conversationlist.ConversationsListActivity

/**
 * Exported entry point for the intents of other apps that open a conversation: nextcloudtalk:// deep links and the
 * chat action of a phone book contact. It shows nothing and passes only the uri and type of such an intent on to the
 * conversation list, which routes it in the task of the app, wherever this activity was started.
 */
class DeepLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewIntent = createViewIntent(this, intent)
        // A link that opens no conversation does nothing: passed on, it would close the screens open in the app above
        // the conversation list. Other intents open the app.
        val isInvalidLink = intent.action == Intent.ACTION_VIEW && Request.of(viewIntent) == null
        if (!isInvalidLink) startActivity(viewIntent, splashScreenWithIcon())
        // the theme shows nothing, which requires to finish before onResume
        finish()
    }

    /**
     * The conversation list keeps the splash screen while it routes the link. Since Android 13, the system shows it
     * without the icon of the app for a start that isn't one of the launcher, unless the icon is asked for.
     */
    private fun splashScreenWithIcon(): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityOptions.makeBasic().setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON).toBundle()
        } else {
            null
        }

    companion object {
        /**
         * Creates an intent for the internal entry point of the conversation list that opens the nextcloudtalk:// deep
         * link or the phone book contact of the VIEW [intent] of another app, in the task of the app, where the
         * screens above the list are closed. Only its uri and type are taken, as its extras could come from any app,
         * and a read grant of the uri is passed on.
         */
        fun createViewIntent(context: Context, intent: Intent): Intent {
            val viewIntent = ConversationsListActivity.createIntent(context).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            val uri = intent.data?.takeIf { intent.action == Intent.ACTION_VIEW } ?: return viewIntent
            return viewIntent
                .setAction(Intent.ACTION_VIEW)
                .setDataAndType(uri, intent.type)
                .addFlags(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
