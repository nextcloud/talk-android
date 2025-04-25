/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.github.aurae.retrofit2.LoganSquareConverterFactory
import com.nextcloud.android.common.ui.color.ColorUtil
import com.nextcloud.android.common.ui.theme.MaterialSchemes
import com.nextcloud.android.common.ui.theme.utils.AndroidViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.AndroidXViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.DialogViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.MaterialViewThemeUtils
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.io.AudioFocusRequestManager
import com.nextcloud.talk.chat.data.io.MediaRecorderManager
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.chat.data.network.OfflineFirstChatRepository
import com.nextcloud.talk.chat.data.network.RetrofitChatNetwork
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.contacts.ContactsRepository
import com.nextcloud.talk.contacts.ContactsRepositoryImpl
import com.nextcloud.talk.contacts.ContactsViewModel
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.conversationlist.data.network.ConversationsNetworkDataSource
import com.nextcloud.talk.conversationlist.data.network.OfflineFirstConversationsRepository
import com.nextcloud.talk.conversationlist.data.network.RetrofitConversationsNetwork
import com.nextcloud.talk.dagger.modules.RestModule
import com.nextcloud.talk.dagger.modules.RestModule.HeadersInterceptor
import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.network.NetworkMonitorImpl
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.UsersDao
import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.data.user.UsersRepositoryImpl
import com.nextcloud.talk.repositories.reactions.ReactionsRepository
import com.nextcloud.talk.repositories.reactions.ReactionsRepositoryImpl
import com.nextcloud.talk.ui.theme.MaterialSchemesProviderImpl
import com.nextcloud.talk.ui.theme.TalkSpecificViewThemeUtils
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.database.user.CurrentUserProviderImpl
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.message.MessageUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.preferences.AppPreferencesImpl
import com.nextcloud.talk.utils.ssl.KeyManager
import com.nextcloud.talk.utils.ssl.SSLSocketFactoryCompat
import com.nextcloud.talk.utils.ssl.TrustManager
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.Cache
import okhttp3.Credentials.basic
import okhttp3.Dispatcher
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
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
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.X509KeyManager

/**
 * TODO - basically a reimplementation of common dependencies for use in Previewing Advanced Compose Views
 * It's a hard coded Dependency Injector
 *
 */
class ComposePreviewUtils private constructor(context: Context) {
    private val mContext = context

    companion object {
        fun getInstance(context: Context) = ComposePreviewUtils(context)
        val TAG: String = ComposePreviewUtils::class.java.simpleName
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val appPreferences: AppPreferences
        get() = AppPreferencesImpl(mContext)

    val usersDao: UsersDao
        get() = TalkDatabase.getInstance(mContext, appPreferences).usersDao()

    val userRepository: UsersRepository
        get() = UsersRepositoryImpl(usersDao)

    val userManager: UserManager
        get() = UserManager(userRepository)

    val userProvider: CurrentUserProviderNew
        get() = CurrentUserProviderImpl(userManager)

    val colorUtil: ColorUtil
        get() = ColorUtil(mContext)

    val materialScheme: MaterialSchemes
        get() = MaterialSchemesProviderImpl(userProvider, colorUtil).getMaterialSchemesForCurrentUser()

    @OptIn(ExperimentalCoroutinesApi::class)
    val viewThemeUtils: ViewThemeUtils
        get() {
            val android = AndroidViewThemeUtils(materialScheme, colorUtil)
            val material = MaterialViewThemeUtils(materialScheme, colorUtil)
            val androidx = AndroidXViewThemeUtils(materialScheme, android)
            val talk = TalkSpecificViewThemeUtils(materialScheme, androidx)
            val dialog = DialogViewThemeUtils(materialScheme)
            return ViewThemeUtils(materialScheme, android, material, androidx, talk, dialog)
        }

    val messageUtils: MessageUtils
        get() = MessageUtils(mContext)

    val trustManager: TrustManager
        get() = TrustManager()

    val keyManager: KeyManager?
        get() {
            var keyStore: KeyStore? = null
            try {
                keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                kmf.init(keyStore, null)
                val origKm = kmf.getKeyManagers()[0] as X509KeyManager?
                return KeyManager(origKm, userManager, appPreferences)
            } catch (e: KeyStoreException) {
                Log.e(TAG, "KeyStoreException " + e.getLocalizedMessage())
            } catch (e: CertificateException) {
                Log.e(TAG, "CertificateException " + e.getLocalizedMessage())
            } catch (e: NoSuchAlgorithmException) {
                Log.e(TAG, "NoSuchAlgorithmException " + e.getLocalizedMessage())
            } catch (e: IOException) {
                Log.e(TAG, "IOException " + e.getLocalizedMessage())
            } catch (e: UnrecoverableKeyException) {
                Log.e(TAG, "UnrecoverableKeyException " + e.getLocalizedMessage())
            }

            return null
        }

    val sslSocketFactoryCompat: SSLSocketFactoryCompat
        get() = SSLSocketFactoryCompat(keyManager, trustManager)

    val cookieManager: CookieManager
        get() = CookieManager()

    val cache: Cache
        get() {
            val cacheSize = 128 * 1024 * 1024 // 128 MB
            return Cache(sharedApplication!!.getCacheDir(), cacheSize.toLong())
        }

    val dispatcher: Dispatcher
        get() {
            val dispatcher = Dispatcher()
            dispatcher.maxRequestsPerHost = 100
            dispatcher.maxRequests = 100
            return dispatcher
        }

    inner class GetProxyRunnable internal constructor(private val appPreferences: AppPreferences) : Runnable {
        @Volatile
        var proxyValue: Proxy? = null
            private set

        override fun run() {
            if (Proxy.Type.valueOf(appPreferences.getProxyType()) == Proxy.Type.SOCKS) {
                this.proxyValue = Proxy(
                    Proxy.Type.valueOf(appPreferences.getProxyType()),
                    InetSocketAddress.createUnresolved(
                        appPreferences.getProxyHost(),
                        appPreferences.getProxyPort().toInt()
                    )
                )
            } else {
                this.proxyValue = Proxy(
                    Proxy.Type.valueOf(appPreferences.getProxyType()),
                    InetSocketAddress(
                        appPreferences.getProxyHost(),
                        appPreferences.getProxyPort().toInt()
                    )
                )
            }
        }
    }

    val proxy: Proxy?
        get() {
            if (!TextUtils.isEmpty(appPreferences.getProxyType()) && ("No proxy" != appPreferences.getProxyType()) && !TextUtils.isEmpty(
                    appPreferences.getProxyHost()
                )
            ) {
                val getProxyRunnable: GetProxyRunnable = GetProxyRunnable(appPreferences)
                val getProxyThread = Thread(getProxyRunnable)
                getProxyThread.start()
                try {
                    getProxyThread.join()
                    return getProxyRunnable.proxyValue
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Failed to join the thread while getting proxy: " + e.getLocalizedMessage())
                    return Proxy.NO_PROXY
                }
            } else {
                return Proxy.NO_PROXY
            }
        }

    val okHttpClient: OkHttpClient
        get() {
            val httpClient = OkHttpClient.Builder()

            httpClient.retryOnConnectionFailure(true)
            httpClient.connectTimeout(45, TimeUnit.SECONDS)
            httpClient.readTimeout(45, TimeUnit.SECONDS)
            httpClient.writeTimeout(45, TimeUnit.SECONDS)

            httpClient.cookieJar(JavaNetCookieJar(cookieManager))
            httpClient.cache(cache)

            // Trust own CA and all self-signed certs
            httpClient.sslSocketFactory(sslSocketFactoryCompat, trustManager)
            httpClient.retryOnConnectionFailure(true)
            httpClient.hostnameVerifier(trustManager.getHostnameVerifier(OkHostnameVerifier))

            httpClient.dispatcher(dispatcher)
            if (Proxy.NO_PROXY != proxy) {
                httpClient.proxy(proxy)

                if (appPreferences.getProxyCredentials() && !TextUtils.isEmpty(appPreferences.getProxyUsername()) && !TextUtils.isEmpty(
                        appPreferences.getProxyPassword()
                    )
                ) {
                    httpClient.proxyAuthenticator(
                        RestModule.HttpAuthenticator(
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

            if (BuildConfig.DEBUG && !mContext.getResources().getBoolean(R.bool.nc_is_debug)) {
                val loggingInterceptor = HttpLoggingInterceptor()
                loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
                loggingInterceptor.redactHeader("Authorization")
                loggingInterceptor.redactHeader("Proxy-Authorization")
                httpClient.addInterceptor(loggingInterceptor)
            } else if (mContext.getResources().getBoolean(R.bool.nc_is_debug)) {
                val fileLogger =
                    HttpLoggingInterceptor.Logger { s: String? -> LoggingUtils.writeLogEntryToFile(mContext, s!!) }
                val loggingInterceptor = HttpLoggingInterceptor(fileLogger)
                loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
                loggingInterceptor.redactHeader("Authorization")
                loggingInterceptor.redactHeader("Proxy-Authorization")
                httpClient.addInterceptor(loggingInterceptor)
            }

            return httpClient.build()
        }

    val retrofit: Retrofit
        get() {
            val retrofitBuilder = Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl("https://nextcloud.com")
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
                .addConverterFactory(LoganSquareConverterFactory.create())

            return retrofitBuilder.build()
        }

    val ncApi: NcApi
        get() = retrofit.create(NcApi::class.java)

    val ncApiCoroutines: NcApiCoroutines
        get() = retrofit.create(NcApiCoroutines::class.java)

    val chatNetworkDataSource: ChatNetworkDataSource
        get() = RetrofitChatNetwork(ncApi, ncApiCoroutines)

    val chatMessagesDao: ChatMessagesDao
        get() = TalkDatabase.getInstance(mContext, appPreferences).chatMessagesDao()

    val chatBlocksDao: ChatBlocksDao
        get() = TalkDatabase.getInstance(mContext, appPreferences).chatBlocksDao()

    val conversationsDao: ConversationsDao
        get() = TalkDatabase.getInstance(mContext, appPreferences).conversationsDao()

    val networkMonitor: NetworkMonitor
        get() = NetworkMonitorImpl(mContext)

    val chatRepository: ChatMessageRepository
        get() = OfflineFirstChatRepository(
            chatMessagesDao,
            chatBlocksDao,
            chatNetworkDataSource,
            networkMonitor,
            userProvider
        )

    val conversationNetworkDataSource: ConversationsNetworkDataSource
        get() = RetrofitConversationsNetwork(ncApi)

    val conversationRepository: OfflineConversationsRepository
        get() = OfflineFirstConversationsRepository(
            conversationsDao,
            conversationNetworkDataSource,
            chatNetworkDataSource,
            networkMonitor,
            userProvider
        )

    val reactionsRepository: ReactionsRepository
        get() = ReactionsRepositoryImpl(ncApi, userProvider, chatMessagesDao)

    val mediaRecorderManager: MediaRecorderManager
        get() = MediaRecorderManager()

    val audioFocusRequestManager: AudioFocusRequestManager
        get() = AudioFocusRequestManager(mContext)

    val chatViewModel: ChatViewModel
        get() = ChatViewModel(
            appPreferences,
            chatNetworkDataSource,
            chatRepository,
            conversationRepository,
            reactionsRepository,
            mediaRecorderManager,
            audioFocusRequestManager,
            userProvider
        )

    val contactsRepository: ContactsRepository
        get() = ContactsRepositoryImpl(ncApiCoroutines, userProvider)

    val contactsViewModel: ContactsViewModel
        get() = ContactsViewModel(contactsRepository)
}
