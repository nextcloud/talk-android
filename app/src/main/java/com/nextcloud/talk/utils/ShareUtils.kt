/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel

object ShareUtils {
    fun getStringForIntent(context: Context, user: User, conversation: ConversationModel?): String {
        return String.format(
            context.resources.getString(R.string.nc_share_text),
            user.baseUrl,
            conversation?.token
        )
    }
}
