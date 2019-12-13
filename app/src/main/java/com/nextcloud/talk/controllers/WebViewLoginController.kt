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
package com.nextcloud.talk.controllers

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.security.KeyChain
import android.security.KeyChainException
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.webkit.WebSettings.RenderPriority.HIGH
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequest.Builder
import androidx.work.WorkManager
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R.layout
import com.nextcloud.talk.R.string
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.events.CertificateEvent
import com.nextcloud.talk.jobs.PushRegistrationWorker
import com.nextcloud.talk.models.LoginData
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.other.UserStatus
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BASE_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ORIGINAL_PROTOCOL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USERNAME
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder.MessageType
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder.MessageType.ACCOUNT_SCHEDULED_FOR_DELETION
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder.MessageType.ACCOUNT_UPDATED_NOT_ADDED
import com.nextcloud.talk.utils.ssl.MagicTrustManager
import de.cotech.hw.fido.WebViewFidoBridge
import kotlinx.android.synthetic.main.controller_web_view_login.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.net.CookieManager
import java.net.URLDecoder
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*

class WebViewLoginController : BaseController {
    private val PROTOCOL_SUFFIX = "://"
    private val LOGIN_URL_DATA_KEY_VALUE_SEPARATOR = ":"

    val magicTrustManager: MagicTrustManager by inject()
    val cookieManager: CookieManager by inject()
    val usersRepository: UsersRepository by inject()

    private var assembledPrefix: String? = null
    private var baseUrl: String? = null
    private var isPasswordUpdate = false
    private var username: String? = null
    private var password: String? = null
    private var loginStep = 0
    private var automatedLoginAttempted = false
    private var webViewFidoBridge: WebViewFidoBridge? = null

    constructor(bundle: Bundle)
    constructor(
            baseUrl: String?,
            isPasswordUpdate: Boolean
    ) {
        this.baseUrl = baseUrl
        this.isPasswordUpdate = isPasswordUpdate
    }

    constructor(
            baseUrl: String?,
            isPasswordUpdate: Boolean,
            username: String?,
            password: String?
    ) {
        this.baseUrl = baseUrl
        this.isPasswordUpdate = isPasswordUpdate
        this.username = username
        this.password = password
    }

    private val webLoginUserAgent: String
        private get() = (Build.MANUFACTURER.substring(0, 1).toUpperCase(
                Locale.getDefault()
        ) +
                Build.MANUFACTURER.substring(1).toLowerCase(
                        Locale.getDefault()
                ) + " " + Build.MODEL + " ("
                + resources!!.getString(string.nc_app_name) + ")")

    override fun inflateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        return inflater.inflate(layout.controller_web_view_login, container, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewBound(view: View) {
        super.onViewBound(view)
        if (activity != null) {
            activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (actionBar != null) {
            actionBar!!.hide()
        }
        assembledPrefix =
                resources!!.getString(string.nc_talk_login_scheme) + PROTOCOL_SUFFIX + "login/"

        view.webview.apply {
            settings.allowFileAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.javaScriptEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.domStorageEnabled = true
            settings.userAgentString = webLoginUserAgent
            settings.saveFormData = false
            settings.savePassword = false
            settings.setRenderPriority(HIGH)
            clearCache(true)
            clearFormData()
            clearHistory()
            clearSslPreferences()
        }

        WebView.clearClientCertPreferences(null)
        webViewFidoBridge =
                WebViewFidoBridge.createInstanceForWebView(activity as AppCompatActivity?, view.webview)
        CookieSyncManager.createInstance(activity)
        android.webkit.CookieManager.getInstance()
                .removeAllCookies(null)
        val headers: MutableMap<String, String> = hashMapOf()
        headers["OCS-APIRequest"] = "true"

        view.webview.webViewClient = object : WebViewClient() {
            private var basePageLoaded = false
            override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
            ): WebResourceResponse? {
                webViewFidoBridge?.delegateShouldInterceptRequest(view, request)
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                    view: WebView,
                    url: String
            ): Boolean {
                if (url.startsWith(assembledPrefix!!)) {
                    parseAndLoginFromWebView(url)
                    return true
                }
                return false
            }

            override fun onPageFinished(
                    view: WebView,
                    url: String
            ) {
                loginStep++
                if (!basePageLoaded) {
                    if (view.progress_bar != null) {
                        view.progress_bar!!.visibility = View.GONE
                    }
                    if (view.webview != null) {
                        view.webview.visibility = View.VISIBLE
                    }
                    basePageLoaded = true
                }
                if (!TextUtils.isEmpty(username)) {
                    if (loginStep == 1) {
                        view.webview.loadUrl(
                                "javascript: {document.getElementsByClassName('login')[0].click(); };"
                        )
                    } else if (!automatedLoginAttempted) {
                        automatedLoginAttempted = true
                        if (TextUtils.isEmpty(password)) {
                            view.webview.loadUrl(
                                    "javascript:var justStore = document.getElementById('user').value = '"
                                            + username
                                            + "';"
                            )
                        } else {
                            view.webview.loadUrl(
                                    "javascript: {" +
                                            "document.getElementById('user').value = '" + username + "';" +
                                            "document.getElementById('password').value = '" + password + "';" +
                                            "document.getElementById('submit').click(); };"
                            )
                        }
                    }
                }
                super.onPageFinished(view, url)
            }

            override fun onReceivedClientCertRequest(
                    view: WebView,
                    request: ClientCertRequest
            ) {
                val userEntity = usersRepository.getActiveUser()
                var alias: String? = null
                if (!isPasswordUpdate) {
                    alias = appPreferences.temporaryClientCertAlias
                }
                if (TextUtils.isEmpty(alias)) {
                    alias = userEntity!!.clientCertificate
                }
                if (!TextUtils.isEmpty(alias)) {
                    val finalAlias = alias
                    Thread(Runnable {
                        try {
                            val privateKey =
                                    KeyChain.getPrivateKey(activity!!, finalAlias!!)
                            val certificates =
                                    KeyChain.getCertificateChain(activity!!, finalAlias)
                            if (privateKey != null && certificates != null) {
                                request.proceed(privateKey, certificates)
                            } else {
                                request.cancel()
                            }
                        } catch (e: KeyChainException) {
                            request.cancel()
                        } catch (e: InterruptedException) {
                            request.cancel()
                        }
                    })
                            .start()
                } else {
                    KeyChain.choosePrivateKeyAlias(
                            activity!!, { chosenAlias: String? ->
                        if (chosenAlias != null) {
                            appPreferences.temporaryClientCertAlias = chosenAlias
                            Thread(Runnable {
                                var privateKey: PrivateKey? = null
                                try {
                                    privateKey = KeyChain.getPrivateKey(activity!!, chosenAlias)
                                    val certificates =
                                            KeyChain.getCertificateChain(activity!!, chosenAlias)
                                    if (privateKey != null && certificates != null) {
                                        request.proceed(privateKey, certificates)
                                    } else {
                                        request.cancel()
                                    }
                                } catch (e: KeyChainException) {
                                    request.cancel()
                                } catch (e: InterruptedException) {
                                    request.cancel()
                                }
                            })
                                    .start()
                        } else {
                            request.cancel()
                        }
                    }, arrayOf("RSA", "EC"), null, request.host, request.port, null
                    )
                }
            }

            override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: SslError
            ) {
                try {
                    val sslCertificate = error.certificate
                    val f =
                            sslCertificate.javaClass.getDeclaredField("mX509Certificate")
                    f.isAccessible = true
                    val cert =
                            f[sslCertificate] as X509Certificate
                    if (cert == null) {
                        handler.cancel()
                    } else {
                        try {
                            magicTrustManager.checkServerTrusted(
                                    arrayOf(cert), "generic"
                            )
                            handler.proceed()
                        } catch (exception: CertificateException) {
                            eventBus.post(CertificateEvent(cert, magicTrustManager, handler))
                        }
                    }
                } catch (exception: Exception) {
                    handler.cancel()
                }
            }
        }
        view.webview.loadUrl("$baseUrl/index.php/login/flow", headers)
    }

    private fun parseAndLoginFromWebView(dataString: String) {
        val loginData = parseLoginData(assembledPrefix, dataString)
        if (loginData != null) {
            GlobalScope.launch {
                val targetUser =
                        usersRepository.getUserWithUsernameAndServer(loginData.username!!, baseUrl!!)
                var messageType: MessageType? = null

                if (!isPasswordUpdate && targetUser != null) {
                    messageType = ACCOUNT_UPDATED_NOT_ADDED
                }

                if (targetUser != null && UserStatus.PENDING_DELETE.equals(targetUser.status)) {
                    ApplicationWideMessageHolder.getInstance().messageType = ACCOUNT_SCHEDULED_FOR_DELETION
                    if (!isPasswordUpdate) {
                        withContext(Dispatchers.Main) {
                            router.popToRoot()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            router.popCurrentController()
                        }
                    }
                }

                val finalMessageType = messageType
                cookieManager.cookieStore.removeAll()

                if (!isPasswordUpdate && finalMessageType == null) {
                    val bundle = Bundle()
                    bundle.putString(KEY_USERNAME, loginData.username)
                    bundle.putString(KEY_TOKEN, loginData.token)
                    bundle.putString(KEY_BASE_URL, loginData.serverUrl)
                    var protocol = ""
                    if (baseUrl!!.startsWith("http://")) {
                        protocol = "http://"
                    } else if (baseUrl!!.startsWith("https://")) {
                        protocol = "https://"
                    }
                    if (!TextUtils.isEmpty(protocol)) {
                        bundle.putString(KEY_ORIGINAL_PROTOCOL, protocol)
                    }

                    withContext(Dispatchers.Main) {
                        router.pushController(
                                RouterTransaction.with(AccountVerificationController(bundle)).pushChangeHandler(
                                        HorizontalChangeHandler()
                                )
                                        .popChangeHandler(HorizontalChangeHandler())
                        )
                    }
                } else {
                    if (isPasswordUpdate && targetUser != null) {
                        targetUser.token = loginData.token
                        val updatedRows = usersRepository.updateUser(targetUser)
                        if (updatedRows > 0) {
                            if (finalMessageType != null) {
                                ApplicationWideMessageHolder.getInstance().messageType = finalMessageType
                            }

                            val pushRegistrationWork = Builder(PushRegistrationWorker::class.java).build()
                            WorkManager.getInstance()
                                    .enqueue(pushRegistrationWork)
                            withContext(Dispatchers.Main) {
                                router.popCurrentController()
                            }
                        } else {
                            // do nothing
                        }
                    } else {
                        if (finalMessageType != null) {
                            ApplicationWideMessageHolder.getInstance()
                                    .messageType = finalMessageType
                        }
                        withContext(Dispatchers.Main) {
                            router.popToRoot()
                        }
                    }
                }
            }
        }
    }

    private fun parseLoginData(
            prefix: String?,
            dataString: String
    ): LoginData? {
        if (dataString.length < prefix!!.length) {
            return null
        }
        val loginData = LoginData()
        // format is xxx://login/server:xxx&user:xxx&password:xxx
        val data = dataString.substring(prefix.length)
        val values = data.split("&")
                .toTypedArray()
        if (values.size != 3) {
            return null
        }
        for (value in values) {
            if (value.startsWith("user$LOGIN_URL_DATA_KEY_VALUE_SEPARATOR")) {
                loginData.username = URLDecoder.decode(
                        value.substring("user$LOGIN_URL_DATA_KEY_VALUE_SEPARATOR".length)
                )
            } else if (value.startsWith("password$LOGIN_URL_DATA_KEY_VALUE_SEPARATOR")) {
                loginData.token = URLDecoder.decode(
                        value.substring("password$LOGIN_URL_DATA_KEY_VALUE_SEPARATOR".length)
                )
            } else if (value.startsWith("server$LOGIN_URL_DATA_KEY_VALUE_SEPARATOR")) {
                loginData.serverUrl = URLDecoder.decode(
                        value.substring("server$LOGIN_URL_DATA_KEY_VALUE_SEPARATOR".length)
                )
            } else {
                return null
            }
        }
        return if (!TextUtils.isEmpty(loginData.serverUrl)
                && !TextUtils.isEmpty(loginData.username)
                &&
                !TextUtils.isEmpty(loginData.token)
        ) {
            loginData
        } else {
            null
        }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        if (activity != null) {
            activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    companion object {
        const val TAG = "WebViewLoginController"
    }
}