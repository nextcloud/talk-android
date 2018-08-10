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

package com.nextcloud.talk.services.firebase;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.jobs.PushRegistrationWorker;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import javax.inject.Inject;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import autodagger.AutoInjector;

@AutoInjector(NextcloudTalkApplication.class)
public class MagicFirebaseInstanceIDService extends FirebaseInstanceIdService {

    @Inject
    AppPreferences appPreferences;

    public MagicFirebaseInstanceIDService() {
        super();
        NextcloudTalkApplication.getSharedApplication().getComponentApplication()
                .inject(this);
    }

    @Override
    public void onTokenRefresh() {
        appPreferences.setPushToken(FirebaseInstanceId.getInstance().getToken());
        OneTimeWorkRequest pushRegistrationWork = new OneTimeWorkRequest.Builder(PushRegistrationWorker.class).build();
        WorkManager.getInstance().enqueue(pushRegistrationWork);
    }
}
