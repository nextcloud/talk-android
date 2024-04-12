/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.nextcloud.talk.R

object ShareUtils {

    fun shareConversationLink(context: Activity, baseUrl: String?, roomToken: String?, conversationName: String?) {
        if (baseUrl.isNullOrBlank() || roomToken.isNullOrBlank() || conversationName.isNullOrBlank()) {
            return
        }

        val uriToShareConversation = Uri.parse(baseUrl)
            .buildUpon()
            .appendPath("index.php")
            .appendPath("call")
            .appendPath(roomToken)
            .build()

        val shareConversationLink = String.format(
            context.getString(
                R.string.share_link_to_conversation,
                conversationName,
                uriToShareConversation.toString()
            )
        )

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareConversationLink)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.nc_share_link))
        context.startActivity(shareIntent)
    }
}
