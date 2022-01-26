/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.jobs;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.nextcloud.talk.utils.ClosedInterfaceImpl;
import com.nextcloud.talk.utils.PushUtils;

public class PushRegistrationWorker extends Worker {
    public static final String TAG = "PushRegistrationWorker";
    public static final String ORIGIN = "origin";

    public PushRegistrationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        if(new ClosedInterfaceImpl().isGooglePlayServicesAvailable()){
            Data data = getInputData();
            String origin = data.getString("origin");
            Log.d(TAG, "PushRegistrationWorker called via " + origin);

            PushUtils pushUtils = new PushUtils();
            pushUtils.generateRsa2048KeyPair();
            pushUtils.pushRegistrationToServer();

            return Result.success();
        }
        Log.w(TAG, "executing PushRegistrationWorker doesn't make sense because Google Play Services are not " +
            "available");
        return Result.failure();
    }
}
