/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.utils

import android.content.Context
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.database.user.UserUtils

object ShareUtils {
    fun getStringForIntent(
        context: Context?,
        password: String?,
        userUtils: UserUtils?,
        conversation: Conversation?
    ): String {
        val userEntity = userUtils?.currentUser
        var shareString = ""
        if (userEntity != null && context != null) {
            shareString = String.format(
                context.resources.getString(R.string.nc_share_text),
                userEntity.baseUrl, conversation?.token
            )
            if (!password.isNullOrEmpty()) {
                shareString += String.format(context.resources.getString(R.string.nc_share_text_pass), password)
            }
        }
        return shareString
    }
}
