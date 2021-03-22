/*
 * Nextcloud Talk application
 *
 * @author Tobias Kaminsky
 * Copyright (C) 2021 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
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

package com.nextcloud.talk.components.filebrowser.controllers;

import android.os.Bundle;

import com.nextcloud.talk.jobs.ShareOperationWorker;
import com.nextcloud.talk.utils.bundle.BundleKeys;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class BrowserForSharingController extends BrowserController {
    private final String roomToken;

    public BrowserForSharingController(Bundle args) {
        super(args);

        roomToken = args.getString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN());
    }

    @Override
    void onFileSelectionDone() {
        synchronized (selectedPaths) {
            Iterator<String> iterator = selectedPaths.iterator();

            List<String> paths = new ArrayList<>();
            Data data;
            OneTimeWorkRequest shareWorker;

            while (iterator.hasNext()) {
                String path = iterator.next();
                paths.add(path);
                iterator.remove();
                if (paths.size() == 10 || !iterator.hasNext()) {
                    data = new Data.Builder()
                            .putLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(), activeUser.getId())
                            .putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), roomToken)
                            .putStringArray(BundleKeys.INSTANCE.getKEY_FILE_PATHS(), paths.toArray(new String[0]))
                            .build();
                    shareWorker = new OneTimeWorkRequest.Builder(ShareOperationWorker.class)
                            .setInputData(data)
                            .build();
                    WorkManager.getInstance().enqueue(shareWorker);
                    paths = new ArrayList<>();
                }
            }
        }

        getRouter().popCurrentController();
    }

    @Override
    public boolean shouldOnlySelectOneImageFile() {
        return false;
    }
}
