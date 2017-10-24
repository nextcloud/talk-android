/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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

package com.nextcloud.talk.utils;


import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.nextcloud.talk.application.NextcloudTalkApplication;

import java.io.InputStream;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

@GlideModule
public class CachingGlideModule extends AppGlideModule {
    // 128 MB
    private static final int OK_HTTP_CLIENT_CACHE = 128 * 1024 * 1024;
    // 256 MB
    private static final int IMAGE_CACHE_SIZE = 256 * 1024 * 1024;

    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        httpClient.cache(new Cache(NextcloudTalkApplication.getSharedApplication().getCacheDir(),
                OK_HTTP_CLIENT_CACHE));
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(httpClient.build()));
    }

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, IMAGE_CACHE_SIZE));
    }
}
