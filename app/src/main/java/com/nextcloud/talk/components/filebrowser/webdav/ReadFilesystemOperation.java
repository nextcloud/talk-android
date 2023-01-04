/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.components.filebrowser.webdav;

import android.net.Uri;
import android.util.Log;

import com.nextcloud.talk.components.filebrowser.models.DavResponse;
import com.nextcloud.talk.dagger.modules.RestModule;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.utils.ApiUtils;
import com.owncloud.android.lib.common.network.WebdavUtils;
import com.owncloud.android.lib.common.utils.WebDavFileUtils;
import com.owncloud.android.lib.resources.files.model.RemoteFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import at.bitfire.dav4jvm.DavResource;
import at.bitfire.dav4jvm.MultiResponseCallback;
import at.bitfire.dav4jvm.Response;
import at.bitfire.dav4jvm.exception.DavException;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class ReadFilesystemOperation {
    private static final String TAG = "ReadFilesystemOperation";
    private final OkHttpClient okHttpClient;
    private final String url;
    private final int depth;
    private final String basePath;

    public ReadFilesystemOperation(OkHttpClient okHttpClient, User currentUser, String path, int depth) {
        OkHttpClient.Builder okHttpClientBuilder = okHttpClient.newBuilder();
        okHttpClientBuilder.followRedirects(false);
        okHttpClientBuilder.followSslRedirects(false);
        okHttpClientBuilder.authenticator(
                new RestModule.HttpAuthenticator(
                        ApiUtils.getCredentials(
                                currentUser.getUsername(),
                                currentUser.getToken()
                                               ),
                        "Authorization")
                                         );
        this.okHttpClient = okHttpClientBuilder.build();
        this.basePath = currentUser.getBaseUrl() + ApiUtils.filesApi + currentUser.getUserId();
        this.url = basePath + path;
        this.depth = depth;
    }

    public DavResponse readRemotePath() {
        DavResponse davResponse = new DavResponse();
        final List<Response> memberElements = new ArrayList<>();
        final Response[] rootElement = new Response[1];

        try {
            new DavResource(okHttpClient, HttpUrl.parse(url)).propfind(depth,
                                                                       WebdavUtils.getAllPropertiesList(),
                                                                       new MultiResponseCallback() {
                                                                           @Override
                                                                           public void onResponse(@NonNull Response response, @NonNull Response.HrefRelation hrefRelation) {
                                                                               davResponse.setResponse(response);
                                                                               switch (hrefRelation) {
                                                                                   case MEMBER:
                                                                                       memberElements.add(response);
                                                                                       break;
                                                                                   case SELF:
                                                                                       rootElement[0] = response;
                                                                                       break;
                                                                                   case OTHER:
                                                                                   default:
                                                                               }
                                                                           }
                                                                       });
        } catch (IOException | DavException e) {
            Log.w(TAG, "Error reading remote path");
        }

        WebDavFileUtils webDavFileUtils = new WebDavFileUtils();

        final List<RemoteFile> remoteFiles = new ArrayList<>(1 + memberElements.size());

        remoteFiles.add(webDavFileUtils.parseResponse(rootElement[0],
                                                      Uri.parse(basePath)));

        for (Response memberElement : memberElements) {
            remoteFiles.add(webDavFileUtils.parseResponse(memberElement,
                                                          Uri.parse(basePath)));
        }

        davResponse.setData(remoteFiles);
        return davResponse;
    }
}
