/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Activity
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.nextcloud.talk.R

object ShareUtils {

    fun shareConversationLink(context: Activity, baseUrl: String?, roomToken: String?, canGeneratePrettyURL: Boolean) {
        val conversationLink = buildConversationLink(baseUrl, roomToken, canGeneratePrettyURL) ?: return

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, conversationLink)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.nc_share_link))
        context.startActivity(shareIntent)
    }

    @VisibleForTesting
    fun buildConversationLink(baseUrl: String?, roomToken: String?, canGeneratePrettyURL: Boolean): String? {
        if (baseUrl.isNullOrBlank() || roomToken.isNullOrBlank()) {
            return null
        }

        val uriBuilder = baseUrl.toUri()
            .buildUpon()

        if (!canGeneratePrettyURL) {
            uriBuilder.appendPath("index.php")
        }

        uriBuilder.appendPath("call")
        uriBuilder.appendPath(roomToken)

        return uriBuilder.build().toString()
    }
}
