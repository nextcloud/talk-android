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
 * Part of the code in ShareUtils was inspired by BottomSheet under the Apache licence
 * located here: https://github.com/Kennyc1012/BottomSheet/blob/master/library/src/main/java/com/kennyc/bottomsheet/BottomSheet.java#L425
 */

package com.nextcloud.talk.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.kennyc.bottomsheet.adapters.AppAdapter;
import com.nextcloud.talk.R;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.rooms.Conversation;
import com.nextcloud.talk.utils.database.user.UserUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    public static List<AppAdapter.AppInfo> getShareApps(Context context, Intent intent,
                                                        @Nullable Set<String> appsFilter, @Nullable Set<String> toExclude) {

        if (context == null || intent == null) return null;

        PackageManager manager = context.getPackageManager();
        List<ResolveInfo> apps = manager.queryIntentActivities(intent, 0);

        if (apps != null && !apps.isEmpty()) {
            List<AppAdapter.AppInfo> appResources = new ArrayList<>(apps.size());
            boolean shouldCheckPackages = appsFilter != null && !appsFilter.isEmpty();

            for (ResolveInfo resolveInfo : apps) {
                String packageName = resolveInfo.activityInfo.packageName;

                if (shouldCheckPackages && !appsFilter.contains(packageName)) {
                    continue;
                }

                String title = resolveInfo.loadLabel(manager).toString();
                String name = resolveInfo.activityInfo.name;
                Drawable drawable = resolveInfo.loadIcon(manager);
                appResources.add(new AppAdapter.AppInfo(title, packageName, name, drawable));
            }

            if (toExclude != null && !toExclude.isEmpty()) {
                List<AppAdapter.AppInfo> toRemove = new ArrayList<>();

                for (AppAdapter.AppInfo appInfo : appResources) {
                    if (toExclude.contains(appInfo.packageName)) {
                        toRemove.add(appInfo);
                    }
                }

                if (!toRemove.isEmpty()) appResources.removeAll(toRemove);
            }

            return appResources;

        }

        return null;
    }

}
