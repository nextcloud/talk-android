/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

import android.util.Log;

import com.nextcloud.talk.components.filebrowser.interfaces.ListingInterface;
import com.nextcloud.talk.components.filebrowser.models.DavResponse;
import com.nextcloud.talk.components.filebrowser.webdav.LegacyReadFilesystemOperation;
import com.nextcloud.talk.models.database.UserEntity;

import java.util.concurrent.Callable;

import androidx.annotation.Nullable;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;

public class DavListing extends ListingAbstractClass {
    private static final String TAG = DavListing.class.getSimpleName();

    private DavResponse davResponse = new DavResponse();

    public DavListing(ListingInterface listingInterface) {
        super(listingInterface);
    }

    @Override
    public void getFiles(String path, UserEntity currentUser, @Nullable OkHttpClient okHttpClient) {
        Single.fromCallable(new Callable<LegacyReadFilesystemOperation>() {
            @Override
            public LegacyReadFilesystemOperation call() {
                return new LegacyReadFilesystemOperation(okHttpClient, currentUser, path, 1);
            }
        }).subscribeOn(Schedulers.io())
                .subscribe(new SingleObserver<LegacyReadFilesystemOperation>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {

                    }

                    @Override
                    public void onSuccess(@NonNull LegacyReadFilesystemOperation readFilesystemOperation) {
                        davResponse = readFilesystemOperation.readRemotePath();
                        try {
                            listingInterface.listingResult(davResponse);
                        } catch (NullPointerException npe) {
                            Log.i(TAG, "Error loading remote folder - due to view already been terminated", npe);
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        listingInterface.listingResult(davResponse);
                    }
                });
    }
}
