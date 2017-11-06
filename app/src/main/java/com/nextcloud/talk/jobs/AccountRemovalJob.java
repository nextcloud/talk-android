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

package com.nextcloud.talk.jobs;


import android.support.annotation.NonNull;

import com.evernote.android.job.Job;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.utils.database.user.UserUtils;

import javax.inject.Inject;

import autodagger.AutoInjector;

@AutoInjector(NextcloudTalkApplication.class)
public class AccountRemovalJob extends Job {
    public static final String TAG = "AccountRemovalJob";

    @Inject
    UserUtils userUtils;

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        return Result.SUCCESS;
    }
}
