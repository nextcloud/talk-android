/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.filebrowser.webdav;

import android.util.Log;

import com.nextcloud.talk.filebrowser.models.BrowserFile;
import com.nextcloud.talk.filebrowser.models.DavResponse;
import com.nextcloud.talk.dagger.modules.RestModule;
import com.nextcloud.talk.data.user.model.User;
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
        this.basePath = currentUser.getBaseUrl() + DavUtils.DAV_PATH + currentUser.getUserId();
        this.url = basePath + path;
        this.depth = depth;
    }

    public DavResponse readRemotePath() {
        DavResponse davResponse = new DavResponse();
        final List<Response> memberElements = new ArrayList<>();
        final Response[] rootElement = new Response[1];

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
            Log.w(TAG, "Error reading remote path");
        }

        final List<BrowserFile> remoteFiles = new ArrayList<>(1 + memberElements.size());
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
