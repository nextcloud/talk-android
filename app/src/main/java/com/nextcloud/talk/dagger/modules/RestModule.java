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

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.github.aurae.retrofit2.LoganSquareConverterFactory;
import com.nextcloud.talk.BuildConfig;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.LoggingUtils;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.AvatarStatusCodeHolder;
import com.nextcloud.talk.utils.ssl.MagicKeyManager;
import com.nextcloud.talk.utils.ssl.MagicTrustManager;
import com.nextcloud.talk.utils.ssl.SSLSocketFactoryCompat;
import dagger.Module;
import dagger.Provides;
import io.reactivex.schedulers.Schedulers;
import okhttp3.*;
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

import javax.inject.Singleton;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.io.IOException;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

@Module(includes = DatabaseModule.class)
public class RestModule {

    private static final String TAG = "RestModule";
    private final Context context;

    public RestModule(Context context) {
        this.context = context;
    }

    @Singleton
    @Provides
    NcApi provideNcApi(Retrofit retrofit) {
        return retrofit.create(NcApi.class);
    }

    @Singleton
    @Provides
    Proxy provideProxy(AppPreferences appPreferences) {
        if (!TextUtils.isEmpty(appPreferences.getProxyType()) && !"No proxy".equals(appPreferences.getProxyType())
                && !TextUtils.isEmpty(appPreferences.getProxyHost())) {
            GetProxyRunnable getProxyRunnable = new GetProxyRunnable(appPreferences);
            Thread getProxyThread = new Thread(getProxyRunnable);
            getProxyThread.start();
            try {
                getProxyThread.join();
                return getProxyRunnable.getProxyValue();
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to join the thread while getting proxy: " + e.getLocalizedMessage());
                return Proxy.NO_PROXY;
            }
        } else {
            return Proxy.NO_PROXY;
        }
    }

    @Singleton
    @Provides
    Retrofit provideRetrofit(OkHttpClient httpClient) {
        Retrofit.Builder retrofitBuilder = new Retrofit.Builder()
                .client(httpClient)
                .baseUrl("https://nextcloud.com")
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
                .addConverterFactory(LoganSquareConverterFactory.create());

        return retrofitBuilder.build();
    }

    @Singleton
    @Provides
    MagicTrustManager provideMagicTrustManager() {
        return new MagicTrustManager();
    }

    @Singleton
    @Provides
    MagicKeyManager provideKeyManager(AppPreferences appPreferences, UserUtils userUtils) {
        KeyStore keyStore = null;
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, null);
            X509KeyManager origKm = (X509KeyManager) kmf.getKeyManagers()[0];
            return new MagicKeyManager(origKm, userUtils, appPreferences);
        } catch (KeyStoreException e) {
            Log.e(TAG, "KeyStoreException " + e.getLocalizedMessage());
        } catch (CertificateException e) {
            Log.e(TAG, "CertificateException " + e.getLocalizedMessage());
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "NoSuchAlgorithmException " + e.getLocalizedMessage());
        } catch (IOException e) {
            Log.e(TAG, "IOException " + e.getLocalizedMessage());
        } catch (UnrecoverableKeyException e) {
            Log.e(TAG, "UnrecoverableKeyException " + e.getLocalizedMessage());
        }

        return null;
    }

    @Singleton
    @Provides
    SSLSocketFactoryCompat provideSslSocketFactoryCompat(MagicKeyManager keyManager, MagicTrustManager
            magicTrustManager) {
        return new SSLSocketFactoryCompat(keyManager, magicTrustManager);
    }

    @Singleton
    @Provides
    CookieManager provideCookieManager() {
        return new CookieManager();
    }

    @Singleton
    @Provides
    Cache provideCache() {
        int cacheSize = 128 * 1024 * 1024; // 128 MB

        return new Cache(NextcloudTalkApplication.getSharedApplication().getCacheDir(), cacheSize);
    }

    @Singleton
    @Provides
    Dispatcher provideDispatcher() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequestsPerHost(100);
        dispatcher.setMaxRequests(100);
        return dispatcher;
    }

    @Singleton
    @Provides
    OkHttpClient provideHttpClient(Proxy proxy, AppPreferences appPreferences,
                                   MagicTrustManager magicTrustManager,
                                   SSLSocketFactoryCompat sslSocketFactoryCompat, Cache cache,
                                   CookieManager cookieManager, Dispatcher dispatcher) {
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();

        httpClient.retryOnConnectionFailure(true);
        httpClient.connectTimeout(45, TimeUnit.SECONDS);
        httpClient.readTimeout(45, TimeUnit.SECONDS);
        httpClient.writeTimeout(45, TimeUnit.SECONDS);

        httpClient.cookieJar(new JavaNetCookieJar(cookieManager));
        httpClient.cache(cache);

        // Trust own CA and all self-signed certs
        httpClient.sslSocketFactory(sslSocketFactoryCompat, magicTrustManager);
        httpClient.retryOnConnectionFailure(true);
        httpClient.hostnameVerifier(magicTrustManager.getHostnameVerifier(OkHostnameVerifier.INSTANCE));

        httpClient.dispatcher(dispatcher);
        if (!Proxy.NO_PROXY.equals(proxy)) {
            httpClient.proxy(proxy);

            if (appPreferences.getProxyCredentials() &&
                    !TextUtils.isEmpty(appPreferences.getProxyUsername()) &&
                    !TextUtils.isEmpty(appPreferences.getProxyPassword())) {
                httpClient.proxyAuthenticator(new MagicAuthenticator(Credentials.basic(
                        appPreferences.getProxyUsername(),
                        appPreferences.getProxyPassword()), "Proxy-Authorization"));
            }
        }

        httpClient.addInterceptor(new HeadersInterceptor());

        if (BuildConfig.DEBUG && !context.getResources().getBoolean(R.bool.nc_is_debug)) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.redactHeader("Authorization");
            loggingInterceptor.redactHeader("Proxy-Authorization");
            httpClient.addInterceptor(loggingInterceptor);
        } else if (context.getResources().getBoolean(R.bool.nc_is_debug)) {

            HttpLoggingInterceptor.Logger fileLogger =
                    s -> LoggingUtils.writeLogEntryToFile(context, s);
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(fileLogger);
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.redactHeader("Authorization");
            loggingInterceptor.redactHeader("Proxy-Authorization");
            httpClient.addInterceptor(loggingInterceptor);
        }

        return httpClient.build();
    }

    public static class HeadersInterceptor implements Interceptor {

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request original = chain.request();
            Request request = original.newBuilder()
                    .header("User-Agent", ApiUtils.getUserAgent())
                    .header("Accept", "application/json")
                    .header("OCS-APIRequest", "true")
                    .method(original.method(), original.body())
                    .build();

            Response response = chain.proceed(request);

            if (request.url().encodedPath().contains("/avatar/")) {
                AvatarStatusCodeHolder.getInstance().setStatusCode(response.code());
            }

            return response;
        }
    }

    public static class MagicAuthenticator implements Authenticator {

        private String credentials;
        private String authenticatorType;

        public MagicAuthenticator(@NonNull String credentials, @NonNull String authenticatorType) {
            this.credentials = credentials;
            this.authenticatorType = authenticatorType;
        }

        @Nullable
        @Override
        public Request authenticate(@Nullable Route route, @NonNull Response response) {
            if (response.request().header(authenticatorType) != null) {
                return null;
            }

            Response countedResponse = response;

            int attemptsCount = 0;

            while ((countedResponse = countedResponse.priorResponse()) != null) {
                attemptsCount++;
                if (attemptsCount == 3) {
                    return null;
                }
            }

            return response.request().newBuilder()
                    .header(authenticatorType, credentials)
                    .build();
        }
    }

    private class GetProxyRunnable implements Runnable {
        private volatile Proxy proxy;
        private AppPreferences appPreferences;

        GetProxyRunnable(AppPreferences appPreferences) {
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

        Proxy getProxyValue() {
            return proxy;
        }
    }
}
