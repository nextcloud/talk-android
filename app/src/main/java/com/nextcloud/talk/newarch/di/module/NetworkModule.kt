/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.di.module

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import android.text.TextUtils
import android.util.Log
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import com.github.aurae.retrofit2.LoganSquareConverterFactory
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.newarch.data.repository.online.NextcloudTalkRepositoryImpl
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.data.source.remote.ApiService
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import com.nextcloud.talk.newarch.utils.NetworkComponents
import com.nextcloud.talk.newarch.utils.NetworkUtils
import com.nextcloud.talk.newarch.utils.NetworkUtils.GetProxyRunnable
import com.nextcloud.talk.newarch.utils.NetworkUtils.MagicAuthenticator
import com.nextcloud.talk.utils.LoggingUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.singletons.AvatarStatusCodeHolder
import com.nextcloud.talk.utils.ssl.MagicKeyManager
import com.nextcloud.talk.utils.ssl.MagicTrustManager
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Logger
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy.ACCEPT_ALL
import java.net.Proxy
import java.security.*
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509KeyManager

val NetworkModule = module {
    single { createService(get()) }
    single { createLegacyNcApi(get()) }
    single { createRetrofit(get()) }
    single { createProxy(get()) }
    single { createTrustManager() }
    single { createCookieManager() }
    single { createDispatcher() }
    single { createKeyManager(get(), get()) }
    single { createSslSocketFactory(get(), get()) }
    single { createCache(androidApplication() as NextcloudTalkApplication) }
    single { createOkHttpClient(androidContext(), get(), get(), get(), get(), get(), get(), get()) }
    factory { createApiErrorHandler() }
    single { createNextcloudTalkRepository(get()) }
    single { createComponentsWithEmptyCookieJar(get(), get(), androidApplication()) }
    single { createImageLoader(androidApplication(), get()) }

}

fun createComponentsWithEmptyCookieJar(okHttpClient: OkHttpClient, retrofit: Retrofit, androidApplication: Application): NetworkComponents {
    return NetworkComponents(okHttpClient, retrofit, androidApplication)
}

fun createCookieManager(): CookieManager {
    val cookieManager = CookieManager()
    cookieManager.setCookiePolicy(ACCEPT_ALL)
    return cookieManager
}

fun createOkHttpClient(
        context: Context,
        proxy: Proxy,
        appPreferences: AppPreferences,
        magicTrustManager: MagicTrustManager,
        sslSocketFactory: SSLSocketFactory,
        cache: Cache,
        cookieManager: CookieManager,
        dispatcher: Dispatcher
): OkHttpClient {
    val httpClient = OkHttpClient.Builder()

    httpClient.retryOnConnectionFailure(true)
    httpClient.connectTimeout(300, TimeUnit.SECONDS)
    httpClient.readTimeout(300, TimeUnit.SECONDS)
    httpClient.writeTimeout(300, TimeUnit.SECONDS)

    httpClient.cookieJar(JavaNetCookieJar(cookieManager))
    httpClient.cache(cache)

    // Trust own CA and all self-signed certs
    httpClient.sslSocketFactory(sslSocketFactory, magicTrustManager)
    httpClient.hostnameVerifier(magicTrustManager.getHostnameVerifier(OkHostnameVerifier.INSTANCE))

    if (SDK_INT == Build.VERSION_CODES.N) {
        val suites = sslSocketFactory.defaultCipherSuites
        val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).cipherSuites(*suites).build()
        httpClient.connectionSpecs(listOf(tlsSpec, ConnectionSpec.CLEARTEXT))
    }

    httpClient.dispatcher(dispatcher)
    if (Proxy.NO_PROXY != proxy) {
        httpClient.proxy(proxy)

        if (appPreferences.proxyCredentials &&
                !TextUtils.isEmpty(appPreferences.proxyUsername) &&
                !TextUtils.isEmpty(appPreferences.proxyPassword)
        ) {
            httpClient.proxyAuthenticator(
                    MagicAuthenticator(
                            Credentials.basic(
                                    appPreferences.proxyUsername,
                                    appPreferences.proxyPassword
                            ), "Proxy-Authorization"
                    )
            )
        }
    }

    httpClient.addNetworkInterceptor { chain ->
        var response = chain.proceed(chain.request())

        if (response.request().url().encodedPath().contains("/avatar/")) {
            if (response.header("x-nc-iscustomavatar", "0") == "1") {
                AvatarStatusCodeHolder.getInstance().statusCode = response.code()
            } else  {
                AvatarStatusCodeHolder.getInstance().statusCode = response.code()
            }

            if (response.code() == 201) {
                response = response.newBuilder().code(200).message("OK").build()
            }
        }


        response
    }

    httpClient.addInterceptor(NetworkUtils.HeadersInterceptor())

    if (BuildConfig.DEBUG && !context.resources.getBoolean(R.bool.nc_is_debug)) {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
        loggingInterceptor.redactHeader("Authorization")
        loggingInterceptor.redactHeader("Proxy-Authorization")
        loggingInterceptor.redactHeader("Cookie")
        httpClient.addInterceptor(loggingInterceptor)
    } else if (context.resources.getBoolean(R.bool.nc_is_debug)) {

        val fileLogger = HttpLoggingInterceptor(object : Logger {
            override fun log(message: String) {
                LoggingUtils.writeLogEntryToFile(context, message)
            }
        })

        fileLogger.level = HttpLoggingInterceptor.Level.BODY
        fileLogger.redactHeader("Authorization")
        fileLogger.redactHeader("Proxy-Authorization")
        fileLogger.redactHeader("Cookie")
        httpClient.addInterceptor(fileLogger)
    }

    return httpClient.build()

}

fun createRetrofit(okHttpClient: OkHttpClient): Retrofit {
    return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://nextcloud.com")
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
            .addConverterFactory(LoganSquareConverterFactory.create())
            .build()
}

fun createTrustManager(): MagicTrustManager {
    return MagicTrustManager()
}

fun createSslSocketFactory(
        magicKeyManager: MagicKeyManager,
        magicTrustManager:
        MagicTrustManager
): SSLSocketFactory {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(
            arrayOf(magicKeyManager),
            arrayOf(magicTrustManager),
            SecureRandom())
    return sslContext.socketFactory
}

fun createKeyManager(
        appPreferences: AppPreferences,
        usersRepository: UsersRepository
): MagicKeyManager? {
    val keyStore: KeyStore?
    try {
        keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, null)
        val origKm = kmf.keyManagers[0] as X509KeyManager
        return MagicKeyManager(origKm, usersRepository, appPreferences)
    } catch (e: KeyStoreException) {
        Log.e("NetworkModule", "KeyStoreException " + e.localizedMessage!!)
    } catch (e: CertificateException) {
        Log.e("NetworkModule", "CertificateException " + e.localizedMessage!!)
    } catch (e: NoSuchAlgorithmException) {
        Log.e("NetworkModule", "NoSuchAlgorithmException " + e.localizedMessage!!)
    } catch (e: IOException) {
        Log.e("NetworkModule", "IOException " + e.localizedMessage!!)
    } catch (e: UnrecoverableKeyException) {
        Log.e("NetworkModule", "UnrecoverableKeyException " + e.localizedMessage!!)
    }

    return null
}

fun createProxy(appPreferences: AppPreferences): Proxy {
    if (!TextUtils.isEmpty(appPreferences.proxyType) && "No proxy" != appPreferences.proxyType
            && !TextUtils.isEmpty(appPreferences.proxyHost)
    ) {
        val getProxyRunnable = GetProxyRunnable(appPreferences)
        val getProxyThread = Thread(getProxyRunnable)
        getProxyThread.start()
        try {
            getProxyThread.join()
            return getProxyRunnable.proxyValue
        } catch (e: InterruptedException) {
            Log.e("NetworkModule", "Failed to join the thread while getting proxy: " + e.localizedMessage)
            return Proxy.NO_PROXY
        }

    } else {
        return Proxy.NO_PROXY
    }

}

fun createDispatcher(): Dispatcher {
    val dispatcher = Dispatcher()
    dispatcher.maxRequestsPerHost = 100
    dispatcher.maxRequests = 100
    return dispatcher
}

fun createCache(androidApplication: Application): Cache {
    //val cacheSize = 128 * 1024 * 1024 // 128 MB
    return Cache(androidApplication.cacheDir, Long.MAX_VALUE)
}

fun createApiErrorHandler(): ApiErrorHandler {
    return ApiErrorHandler()
}

fun createService(retrofit: Retrofit): ApiService {
    return retrofit.create(ApiService::class.java)
}

fun createLegacyNcApi(retrofit: Retrofit): NcApi {
    return retrofit.create(NcApi::class.java)
}

fun createImageLoader(
        androidApplication: Application,
        okHttpClient: OkHttpClient
): ImageLoader {
    return ImageLoader(androidApplication) {
        availableMemoryPercentage(0.5)
        bitmapPoolPercentage(0.5)
        crossfade(false)
        okHttpClient(okHttpClient)
        componentRegistry {
            if (SDK_INT >= P) {
                add(ImageDecoderDecoder())
            } else {
                add(GifDecoder())
            }
            add(SvgDecoder(androidApplication))
        }
    }
}

fun createNextcloudTalkRepository(apiService: ApiService): NextcloudTalkRepository {
    return NextcloudTalkRepositoryImpl(apiService)
}
