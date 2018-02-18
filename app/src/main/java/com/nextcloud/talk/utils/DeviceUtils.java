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
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;

import com.nextcloud.talk.application.NextcloudTalkApplication;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class DeviceUtils {
    private static final String TAG = "DeviceUtils";

    public static void ignoreSpecialBatteryFeatures() {
        if (Build.MANUFACTURER.equalsIgnoreCase("xiaomi")) {
            try {
                @SuppressLint("PrivateApi") Class<?> aClass = Class.forName("android.miui.AppOpsUtils");
                if (aClass != null) {
                    Method getApplicationAutoStart = aClass.getDeclaredMethod("getApplicationAutoStart", Context.class, String.class);
                    Context applicationContext = NextcloudTalkApplication.getSharedApplication().getApplicationContext();
                    Object result = getApplicationAutoStart.invoke(aClass, applicationContext, applicationContext.getPackageName());
                    if (result instanceof Integer) {
                        Integer integerResult = (Integer) result;
                        if (integerResult == 0) {
                            Class<ApplicationInfo> clazz = ApplicationInfo.class;
                            Field[] fields = clazz.getDeclaredFields();

                            for (Field field : fields) {
                                field.setAccessible(true);
                                if (field.getName().equals("FLAG_DISABLE_AUTOSTART")) {
                                    int value = field.getInt(ApplicationInfo.class);
                                    if (value != 0) {
                                        field.setInt(ApplicationInfo.class, 0);
                                        field.setAccessible(false);
                                    }
                                    break;
                                }
                                field.setAccessible(false);
                            }
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
        } else if (Build.MANUFACTURER.equalsIgnoreCase("huawei")) {
            try {
                @SuppressLint("PrivateApi") Class<?> aClass = Class.forName("com.huawei.systemmanager.optimize.process" +
                        ".ProtectAppControl");
                if (aClass != null) {
                    Context applicationContext = NextcloudTalkApplication.getSharedApplication().getApplicationContext();

                    Method method = aClass.getMethod("getInstance", Context.class);
                    // ProtectAppControl instance
                    Object protectAppControlInstance = method.invoke(null, applicationContext);

                    Method isProtected = aClass.getDeclaredMethod("isProtect", String.class);
                    Object result = isProtected.invoke(protectAppControlInstance, applicationContext.getPackageName());
                    if (result instanceof Boolean) {
                        boolean booleanResult = (boolean) result;
                        if (!booleanResult) {
                            Method setProtect = aClass.getDeclaredMethod("setProtect", List.class);
                            List<String> appsList = new ArrayList<>();
                            appsList.add(applicationContext.getPackageName());
                            setProtect.invoke(protectAppControlInstance, appsList);
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
