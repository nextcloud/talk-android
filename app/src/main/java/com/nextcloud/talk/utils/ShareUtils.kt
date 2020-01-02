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
 *
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.text.TextUtils
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import org.koin.core.KoinComponent
import org.koin.core.inject

object ShareUtils : KoinComponent {
    val usersRepository: UsersRepository by inject()

    @JvmStatic
    fun getStringForIntent(context: Context?, password: String?, conversation: Conversation): String {
        var shareString = ""
        val userEntity: UserNgEntity? = usersRepository.getActiveUser()
        if (userEntity != null && context != null) {
            shareString = java.lang.String.format(context.resources.getString(R.string.nc_share_text),
                    userEntity.baseUrl, conversation.token)
            if (!TextUtils.isEmpty(password)) {
                shareString += String.format(context.resources.getString(R.string.nc_share_text_pass), password)
            }
        }

        return shareString
    }
}