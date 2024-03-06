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
package com.nextcloud.talk.dagger.modules

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.github.aurae.retrofit2.LoganSquareConverterFactory
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils.userAgent
import com.nextcloud.talk.utils.LoggingUtils.writeLogEntryToFile
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.ssl.KeyManager
import com.nextcloud.talk.utils.ssl.SSLSocketFactoryCompat
import com.nextcloud.talk.utils.ssl.TrustManager
import dagger.Module
import dagger.Provides
import io.reactivex.schedulers.Schedulers
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Credentials.basic
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import java.io.IOException
import java.net.CookieManager
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.X509KeyManager
import kotlin.concurrent.Volatile

@Module(includes = [DatabaseModule::class])
class RestModule(private val context: Context) {
    @Singleton
    @Provides
    fun provideNcApi(retrofit: Retrofit): NcApi {
        return retrofit.create(NcApi::class.java)
    }

    @Singleton
    @Provides
    fun provideProxy(appPreferences: AppPreferences): Proxy? {
        return if (!TextUtils.isEmpty(appPreferences.getProxyType()) && "No proxy" != appPreferences.getProxyType() &&
            !TextUtils.isEmpty(appPreferences.getProxyHost())
        ) {
            val getProxyRunnable = GetProxyRunnable(appPreferences)
            val getProxyThread = Thread(getProxyRunnable)
            getProxyThread.start()
            try {
                getProxyThread.join()
                getProxyRunnable.proxyValue
            } catch (e: InterruptedException) {
                Log.e(TAG, "Failed to join the thread while getting proxy: " + e.localizedMessage)
                Proxy.NO_PROXY
            }
        } else {
            Proxy.NO_PROXY
        }
    }

    @Singleton
    @Provides
    fun provideRetrofit(httpClient: OkHttpClient?): Retrofit {
        val retrofitBuilder = Retrofit.Builder()
            .client(httpClient!!)
            .baseUrl("https://nextcloud.com")
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
            .addConverterFactory(LoganSquareConverterFactory.create())
        return retrofitBuilder.build()
    }

    @Singleton
    @Provides
    fun provideTrustManager(): TrustManager {
        return TrustManager()
    }

    @Singleton
    @Provides
    fun provideKeyManager(appPreferences: AppPreferences?, userManager: UserManager?): KeyManager? {
        val keyStore: KeyStore?
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, null)
            val origKm = kmf.keyManagers[0] as X509KeyManager
            return KeyManager(origKm, userManager, appPreferences)
        } catch (e: KeyStoreException) {
            Log.e(TAG, "KeyStoreException " + e.localizedMessage)
        } catch (e: CertificateException) {
            Log.e(TAG, "CertificateException " + e.localizedMessage)
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "NoSuchAlgorithmException " + e.localizedMessage)
        } catch (e: IOException) {
            Log.e(TAG, "IOException " + e.localizedMessage)
        } catch (e: UnrecoverableKeyException) {
            Log.e(TAG, "UnrecoverableKeyException " + e.localizedMessage)
        }
        return null
    }

    @Singleton
    @Provides
    fun provideSslSocketFactoryCompat(keyManager: KeyManager?, trustManager: TrustManager?): SSLSocketFactoryCompat {
        return SSLSocketFactoryCompat(keyManager, trustManager!!)
    }

    @Singleton
    @Provides
    fun provideCookieManager(): CookieManager {
        return CookieManager()
    }

    @Singleton
    @Provides
    fun provideCache(): Cache {
        val cacheSize = 128 * 1024 * 1024 // 128 MB
        return Cache(sharedApplication!!.cacheDir, cacheSize.toLong())
    }

    @Singleton
    @Provides
    fun provideDispatcher(): Dispatcher {
        val dispatcher = Dispatcher()
        dispatcher.maxRequestsPerHost = 100
        dispatcher.maxRequests = 100
        return dispatcher
    }

    @Singleton
    @Provides
    fun provideHttpClient(
        proxy: Proxy?,
        appPreferences: AppPreferences,
        trustManager: TrustManager,
        sslSocketFactoryCompat: SSLSocketFactoryCompat?,
        cache: Cache?,
        cookieManager: CookieManager?,
        dispatcher: Dispatcher?
    ): OkHttpClient {
        val httpClient = OkHttpClient.Builder()
        httpClient.retryOnConnectionFailure(true)
        httpClient.connectTimeout(45, TimeUnit.SECONDS)
        httpClient.readTimeout(45, TimeUnit.SECONDS)
        httpClient.writeTimeout(45, TimeUnit.SECONDS)
        httpClient.cookieJar(JavaNetCookieJar(cookieManager!!))
        httpClient.cache(cache)

        // Trust own CA and all self-signed certs
        httpClient.sslSocketFactory(sslSocketFactoryCompat!!, trustManager)
        httpClient.retryOnConnectionFailure(true)
        httpClient.hostnameVerifier(trustManager.getHostnameVerifier(OkHostnameVerifier))
        httpClient.dispatcher(dispatcher!!)
        if (Proxy.NO_PROXY != proxy) {
            httpClient.proxy(proxy)
            if (appPreferences.getProxyCredentials() &&
                !TextUtils.isEmpty(appPreferences.getProxyUsername()) &&
                !TextUtils.isEmpty(appPreferences.getProxyPassword())
            ) {
                httpClient.proxyAuthenticator(
                    HttpAuthenticator(
                        basic(
                            appPreferences.getProxyUsername(),
                            appPreferences.getProxyPassword(),
                            StandardCharsets.UTF_8
                        ),
                        "Proxy-Authorization"
                    )
                )
            }
        }
        httpClient.addInterceptor(HeadersInterceptor())
        if (BuildConfig.DEBUG && !context.resources.getBoolean(R.bool.nc_is_debug)) {
            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            loggingInterceptor.redactHeader("Authorization")
            loggingInterceptor.redactHeader("Proxy-Authorization")
            httpClient.addInterceptor(loggingInterceptor)
        } else if (context.resources.getBoolean(R.bool.nc_is_debug)) {
            val fileLogger = HttpLoggingInterceptor.Logger { s: String? -> writeLogEntryToFile(context, s!!) }
            val loggingInterceptor = HttpLoggingInterceptor(fileLogger)
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            loggingInterceptor.redactHeader("Authorization")
            loggingInterceptor.redactHeader("Proxy-Authorization")
            httpClient.addInterceptor(loggingInterceptor)
        }
        return httpClient.build()
    }

    class HeadersInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Chain): Response {
            val original: Request = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .header("OCS-APIRequest", "true")
                .header("ngrok-skip-browser-warning", "true")
                .method(original.method, original.body)
                .build()
            return chain.proceed(request)
        }
    }

    class HttpAuthenticator(private val credentials: String, private val authenticatorType: String) : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.request.header(authenticatorType) != null) {
                return null
            }
            var countedResponse = response
            var attemptsCount = 0
            while (countedResponse.priorResponse.also { countedResponse = it!! } != null) {
                attemptsCount++
                if (attemptsCount == 3) {
                    return null
                }
            }
            return response.request.newBuilder()
                .header(authenticatorType, credentials)
                .build()
        }
    }

    private inner class GetProxyRunnable(private val appPreferences: AppPreferences) : Runnable {
        @Volatile
        var proxyValue: Proxy? = null
            private set

        override fun run() {
            proxyValue = if (Proxy.Type.valueOf(appPreferences.getProxyType()) == Proxy.Type.SOCKS) {
                Proxy(
                    Proxy.Type.valueOf(appPreferences.getProxyType()),
                    InetSocketAddress.createUnresolved(
                        appPreferences.getProxyHost(),
                        appPreferences.getProxyPort().toInt()
                    )
                )
            } else {
                Proxy(
                    Proxy.Type.valueOf(appPreferences.getProxyType()),
                    InetSocketAddress(appPreferences.getProxyHost(), appPreferences.getProxyPort().toInt())
                )
            }
        }
    }

    companion object {
        private const val TAG = "RestModule"
    }
}
