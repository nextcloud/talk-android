/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import com.nextcloud.talk.application.NextcloudTalkApplication;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class DeviceUtils {
    private static final String TAG = "DeviceUtils";

    public static void ignoreSpecialBatteryFeatures() {
        if (Build.MANUFACTURER.equalsIgnoreCase("xiaomi") || Build.MANUFACTURER.equalsIgnoreCase("meizu")) {
            try {
                @SuppressLint("PrivateApi") Class<?> appOpsUtilsClass = Class.forName("android.miui.AppOpsUtils");
                if (appOpsUtilsClass != null) {
                    Method setApplicationAutoStartMethod = appOpsUtilsClass.getMethod("setApplicationAutoStart", Context
                            .class, String.class, Boolean.TYPE);
                    if (setApplicationAutoStartMethod != null) {
                        Context applicationContext = NextcloudTalkApplication.getSharedApplication().getApplicationContext();
                        setApplicationAutoStartMethod.invoke(appOpsUtilsClass, applicationContext, applicationContext
                                .getPackageName(), Boolean.TRUE);
                    }
                }
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Class not found");
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "No such method");
            } catch (IllegalAccessException e) {
                Log.e(TAG, "IllegalAccessException");
            } catch (InvocationTargetException e) {
                Log.e(TAG, "InvocationTargetException");
            }
        } else if (Build.MANUFACTURER.equalsIgnoreCase("huawei")) {
            try {
                @SuppressLint("PrivateApi") Class<?> protectAppControlClass = Class.forName("com.huawei.systemmanager.optimize.process" +
                        ".ProtectAppControl");
                if (protectAppControlClass != null) {
                    Context applicationContext = NextcloudTalkApplication.getSharedApplication().getApplicationContext();

                    Method getInstanceMethod = protectAppControlClass.getMethod("getInstance", Context.class);
                    // ProtectAppControl instance
                    if (getInstanceMethod != null) {
                        Object protectAppControlInstance = getInstanceMethod.invoke(null, applicationContext);

                        Method setProtectMethod = protectAppControlClass.getDeclaredMethod("setProtect", List.class);
                        if (setProtectMethod != null) {
                            List<String> appsList = new ArrayList<>();
                            appsList.add(applicationContext.getPackageName());
                            setProtectMethod.invoke(protectAppControlInstance, appsList);
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Class not found");
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "No such method");
            } catch (IllegalAccessException e) {
                Log.e(TAG, "IllegalAccessException");
            } catch (InvocationTargetException e) {
                Log.e(TAG, "InvocationTargetException");
            }
        }
    }
}
