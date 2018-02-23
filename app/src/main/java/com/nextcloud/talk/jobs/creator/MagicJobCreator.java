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

package com.nextcloud.talk.jobs.creator;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.nextcloud.talk.jobs.AccountRemovalJob;
import com.nextcloud.talk.jobs.CapabilitiesJob;
import com.nextcloud.talk.jobs.NotificationJob;
import com.nextcloud.talk.jobs.PushRegistrationJob;

public class MagicJobCreator implements JobCreator {

    @Nullable
    @Override
    public Job create(@NonNull String tag) {
        switch (tag) {
            case PushRegistrationJob.TAG:
                return new PushRegistrationJob();
            case AccountRemovalJob.TAG:
                return new AccountRemovalJob();
            case NotificationJob.TAG:
                return new NotificationJob();
            case CapabilitiesJob.TAG:
                return new CapabilitiesJob();
            default:
                return null;
        }
    }
}
