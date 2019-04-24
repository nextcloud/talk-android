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

package com.nextcloud.talk.components.filebrowser.operations;

import android.os.Handler;
import androidx.annotation.Nullable;
import com.nextcloud.talk.components.filebrowser.interfaces.ListingInterface;
import com.nextcloud.talk.models.database.UserEntity;
import okhttp3.OkHttpClient;

public abstract class ListingAbstractClass {
    Handler handler;
    ListingInterface listingInterface;

    ListingAbstractClass(ListingInterface listingInterface) {
        handler = new Handler();
        this.listingInterface = listingInterface;
    }

    public abstract void getFiles(String path, UserEntity currentUser, @Nullable OkHttpClient okHttpClient);

    public void cancelAllJobs() {
        handler.removeCallbacksAndMessages(null);
    }

    public void tearDown() {
        cancelAllJobs();
        handler = null;
    }
}
