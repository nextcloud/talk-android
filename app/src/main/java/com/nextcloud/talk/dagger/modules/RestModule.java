/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.dagger.modules;

import android.support.annotation.NonNull;

import com.github.aurae.retrofit2.LoganSquareConverterFactory;
import com.nextcloud.talk.BuildConfig;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.api.helpers.api.ApiHelper;
import com.nextcloud.talk.application.NextcloudTalkApplication;

import java.io.IOException;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

@Module
public class RestModule {

    @Provides
    @Singleton
    NcApi provideNcApi(Retrofit retrofit) {
        return retrofit.create(NcApi.class);
    }

    @Provides
    @Singleton
    Retrofit provideRetrofit(OkHttpClient httpClient) {
        Retrofit.Builder retrofitBuilder = new Retrofit.Builder()
                .client(httpClient)
                .baseUrl("https://nextcloud.com")
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
                .addConverterFactory(LoganSquareConverterFactory.create());

        return retrofitBuilder.build();
    }

    @Provides
    @Singleton
    OkHttpClient provideHttpClient() {
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();

        int cacheSize = 128 * 1024 * 1024; // 128 MB

        httpClient.cache(new Cache(NextcloudTalkApplication.getSharedApplication().getCacheDir(), cacheSize));

        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            httpClient.addInterceptor(loggingInterceptor);
        }
        httpClient.addInterceptor(new HeadersInterceptor());

        return httpClient.build();
    }

    private class HeadersInterceptor implements Interceptor {

        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request original = chain.request();

            Request request = original.newBuilder()
                    .header("User-Agent", ApiHelper.getUserAgent())
                    .header("Accept", "application/json")
                    .header("OCS-APIRequest", "true")
                    .method(original.method(), original.body())
                    .build();

            return chain.proceed(request);
        }
    }
}
