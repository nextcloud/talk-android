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

package com.nextcloud.talk.utils;

import android.content.Context;
import android.text.TextUtils;

import com.nextcloud.talk.R;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.utils.database.user.UserUtils;

import androidx.annotation.Nullable;

public class ShareUtils {

    public static String getStringForIntent(Context context, @Nullable String password, UserUtils userUtils, Conversation
            conversation) {
        UserEntity userEntity = userUtils.getCurrentUser();

        String shareString = "";
        if (userEntity != null && context != null) {
            shareString = String.format(context.getResources().getString(R.string.nc_share_text),
                    userEntity.getBaseUrl(), conversation.getToken());

            if (!TextUtils.isEmpty(password)) {
                shareString += String.format(context.getResources().getString(R.string.nc_share_text_pass), password);
            }
        }

        return shareString;
    }
}
