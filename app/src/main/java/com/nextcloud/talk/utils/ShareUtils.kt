/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri
import com.nextcloud.talk.R

object ShareUtils {

    @SuppressLint("StringFormatMatches")
    fun shareConversationLink(
        context: Activity,
        baseUrl: String?,
        roomToken: String?,
        conversationName: String?,
        canGeneratePrettyURL: Boolean
    ) {
        if (baseUrl.isNullOrBlank() || roomToken.isNullOrBlank() || conversationName.isNullOrBlank()) {
            return
        }

        val uriBuilder = baseUrl.toUri()
            .buildUpon()

        if (!canGeneratePrettyURL) {
            uriBuilder.appendPath("index.php")
        }

        uriBuilder.appendPath("call")
        uriBuilder.appendPath(roomToken)

        val uriToShareConversation = uriBuilder.build()

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
