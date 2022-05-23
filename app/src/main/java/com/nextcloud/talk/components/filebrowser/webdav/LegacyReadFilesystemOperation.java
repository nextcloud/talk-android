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

import android.util.Log;

import com.nextcloud.talk.components.filebrowser.models.BrowserFile;
import com.nextcloud.talk.components.filebrowser.models.DavResponse;
import com.nextcloud.talk.dagger.modules.RestModule;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.ApiUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import at.bitfire.dav4jvm.DavResource;
import at.bitfire.dav4jvm.Response;
import at.bitfire.dav4jvm.exception.DavException;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

@Deprecated
public class LegacyReadFilesystemOperation {
    private static final String TAG = "ReadFilesystemOperation";
    private final OkHttpClient okHttpClient;
    private final String url;
    private final int depth;
    private final String basePath;

    public LegacyReadFilesystemOperation(OkHttpClient okHttpClient, UserEntity currentUser, String path, int depth) {
        OkHttpClient.Builder okHttpClientBuilder = okHttpClient.newBuilder();
        okHttpClientBuilder.followRedirects(false);
        okHttpClientBuilder.followSslRedirects(false);
        okHttpClientBuilder.authenticator(
                new RestModule.MagicAuthenticator(
                        ApiUtils.getCredentials(
                                currentUser.getUsername(),
                                currentUser.getToken()
                                               ),
                        "Authorization")
                                         );
        this.okHttpClient = okHttpClientBuilder.build();
        this.basePath = currentUser.getBaseUrl() + DavUtils.DAV_PATH + currentUser.getUserId();
        this.url = basePath + path;
        this.depth = depth;
    }

    public DavResponse readRemotePath() {
        DavResponse davResponse = new DavResponse();
        final List<Response> memberElements = new ArrayList<>();
        final Response[] rootElement = new Response[1];
        final List<BrowserFile> remoteFiles = new ArrayList<>();

        try {
            new DavResource(okHttpClient, HttpUrl.parse(url)).propfind(depth, DavUtils.getAllPropSet(),
                    new Function2<Response, Response.HrefRelation, Unit>() {
                        @Override
                        public Unit invoke(Response response, Response.HrefRelation hrefRelation) {
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
                            return Unit.INSTANCE;
                        }
                    });
        } catch (IOException | DavException e) {
            Log.w("", "Error reading remote path");
        }

        remoteFiles.add(BrowserFile.Companion.getModelFromResponse(rootElement[0],
                rootElement[0].getHref().toString().substring(basePath.length())));
        for (Response memberElement : memberElements) {
            remoteFiles.add(BrowserFile.Companion.getModelFromResponse(memberElement,
                    memberElement.getHref().toString().substring(basePath.length())));
        }

        davResponse.setData(remoteFiles);
        return davResponse;
    }
}
