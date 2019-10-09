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

import androidx.annotation.Nullable;
import com.nextcloud.talk.components.filebrowser.interfaces.ListingInterface;
import com.nextcloud.talk.components.filebrowser.models.DavResponse;
import com.nextcloud.talk.components.filebrowser.webdav.ReadFilesystemOperation;
import com.nextcloud.talk.models.database.UserEntity;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;

import java.util.concurrent.Callable;

public class DavListing extends ListingAbstractClass {
    private DavResponse davResponse = new DavResponse();

    public DavListing(ListingInterface listingInterface) {
        super(listingInterface);
    }

    @Override
    public void getFiles(String path, UserEntity currentUser, @Nullable OkHttpClient okHttpClient) {
        Single.fromCallable(new Callable<ReadFilesystemOperation>() {
            @Override
            public ReadFilesystemOperation call() {
                return new ReadFilesystemOperation(okHttpClient, currentUser, path, 1);
            }
        }).subscribeOn(Schedulers.io())
                .subscribe(new SingleObserver<ReadFilesystemOperation>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onSuccess(ReadFilesystemOperation readFilesystemOperation) {
                        davResponse = readFilesystemOperation.readRemotePath();
                        listingInterface.listingResult(davResponse);
                    }

                    @Override
                    public void onError(Throwable e) {
                        listingInterface.listingResult(davResponse);
                    }
                });
    }
}
