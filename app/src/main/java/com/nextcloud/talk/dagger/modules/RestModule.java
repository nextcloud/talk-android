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
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.github.aurae.retrofit2.LoganSquareConverterFactory;
import com.nextcloud.talk.BuildConfig;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.api.helpers.api.ApiHelper;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.ssl.MagicTrustManager;
import com.nextcloud.talk.utils.ssl.SSLSocketFactoryCompat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Authenticator;
import okhttp3.Cache;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

@Module(includes = DatabaseModule.class)
public class RestModule {

    private static final String TAG = "RestModule";

    @Provides
    @Singleton
    NcApi provideNcApi(Retrofit retrofit) {
        return retrofit.create(NcApi.class);
    }

    @Provides
    @Singleton
    Proxy provideProxy(AppPreferences appPreferences) {
        if (!TextUtils.isEmpty(appPreferences.getProxyType()) && !"No proxy".equals(appPreferences.getProxyType())
                && !TextUtils.isEmpty(appPreferences.getProxyHost())) {
            GetProxyRunnable getProxyRunnable = new GetProxyRunnable(appPreferences);
            new Thread(getProxyRunnable).start();
            return getProxyRunnable.getProxyValue();
        } else {
            return Proxy.NO_PROXY;
        }
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
    MagicTrustManager provideMagicTrustManager() {
        return new MagicTrustManager();
    }

    @Provides
    @Singleton
    SSLSocketFactoryCompat provideSslSocketFactoryCompat(MagicTrustManager magicTrustManager) {
        return new SSLSocketFactoryCompat(magicTrustManager);
    }

    @Provides
    @Singleton
    CookieManager provideCookieManager() {
        return new CookieManager();
    }

    @Provides
    @Singleton
    Cache provideCache() {
        int cacheSize = 128 * 1024 * 1024; // 128 MB
        return new Cache(NextcloudTalkApplication.getSharedApplication().getCacheDir(), cacheSize);
    }

    @Provides
    @Singleton
    OkHttpClient provideHttpClient(Proxy proxy, AppPreferences appPreferences,
                                   MagicTrustManager magicTrustManager,
                                   SSLSocketFactoryCompat sslSocketFactoryCompat, Cache cache,
                                   CookieManager cookieManager) {
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();

        httpClient.connectTimeout(30, TimeUnit.SECONDS);
        httpClient.readTimeout(30, TimeUnit.SECONDS);
        httpClient.writeTimeout(30, TimeUnit.SECONDS);

        httpClient.cookieJar(new JavaNetCookieJar(cookieManager));
        httpClient.cache(cache);

        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            httpClient.addInterceptor(loggingInterceptor);
        }

        httpClient.sslSocketFactory(sslSocketFactoryCompat, magicTrustManager);
        httpClient.retryOnConnectionFailure(true);
        httpClient.hostnameVerifier(magicTrustManager.getHostnameVerifier(OkHostnameVerifier.INSTANCE));

        if (!Proxy.NO_PROXY.equals(proxy)) {
            httpClient.proxy(proxy);

            if (appPreferences.getProxyCredentials() &&
                    !TextUtils.isEmpty(appPreferences.getProxyUsername()) &&
                    !TextUtils.isEmpty(appPreferences.getProxyPassword())) {
                httpClient.proxyAuthenticator(new ProxyAuthenticator(Credentials.basic(
                        appPreferences.getProxyUsername(),
                        appPreferences.getProxyPassword())));
            }
        }

        httpClient.addInterceptor(new HeadersInterceptor());

        return httpClient.build();
    }

    private class ProxyAuthenticator implements Authenticator {

        private String credentials;

        private ProxyAuthenticator(String credentials) {
            this.credentials = credentials;
        }

        @Nullable
        @Override
        public Request authenticate(@NonNull Route route, @NonNull Response response) throws IOException {
            if (credentials.equals(response.request().header("Proxy-Authorization"))) {
                return null;
            }

            int attemptsCount = 0;
            Response countedResponse = response;

            while ((countedResponse = countedResponse.priorResponse()) != null) {
                attemptsCount++;
                if (attemptsCount == 3) {
                    return null;
                }
            }

            return response.request().newBuilder()
                    .header("Proxy-Authorization", credentials)
                    .build();
        }
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

    private class GetProxyRunnable implements Runnable {
        private volatile Proxy proxy;
        private AppPreferences appPreferences;

        public GetProxyRunnable(AppPreferences appPreferences) {
            this.appPreferences = appPreferences;
        }

        @Override
        public void run() {
            if (Proxy.Type.SOCKS.equals(Proxy.Type.valueOf(appPreferences.getProxyType()))) {
                proxy = new Proxy(Proxy.Type.valueOf(appPreferences.getProxyType()),
                        InetSocketAddress.createUnresolved(appPreferences.getProxyHost(), Integer.parseInt(
                                appPreferences.getProxyPort())));
            } else {
                proxy = new Proxy(Proxy.Type.valueOf(appPreferences.getProxyType()),
                        new InetSocketAddress(appPreferences.getProxyHost(),
                                Integer.parseInt(appPreferences.getProxyPort())));
            }
        }

        public Proxy getProxyValue() {
            return proxy;
        }
    }
}
