/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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
package com.nextcloud.talk.controllers

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.security.KeyChain
import android.security.KeyChainException
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.webkit.ClientCertRequest
import android.webkit.CookieSyncManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.controllers.base.NewBaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.databinding.ControllerWebViewLoginBinding
import com.nextcloud.talk.events.CertificateEvent
import com.nextcloud.talk.jobs.PushRegistrationWorker
import com.nextcloud.talk.models.LoginData
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BASE_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ORIGINAL_PROTOCOL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USERNAME
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import com.nextcloud.talk.utils.ssl.MagicTrustManager
import de.cotech.hw.fido.WebViewFidoBridge
import io.reactivex.disposables.Disposable
import org.greenrobot.eventbus.EventBus
import java.lang.reflect.Field
import java.net.CookieManager
import java.net.URLDecoder
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Locale
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class WebViewLoginController(args: Bundle? = null) : NewBaseController(
    R.layout.controller_web_view_login,
    args
) {
    private val binding: ControllerWebViewLoginBinding by viewBinding(ControllerWebViewLoginBinding::bind)

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var magicTrustManager: MagicTrustManager

    @Inject
    lateinit var eventBus: EventBus

    @Inject
    lateinit var cookieManager: CookieManager

    private var assembledPrefix: String? = null
    private var userQueryDisposable: Disposable? = null
    private var baseUrl: String? = null
    private var isPasswordUpdate = false
    private var username: String? = null
    private var password: String? = null
    private var loginStep = 0
    private var automatedLoginAttempted = false
    private var webViewFidoBridge: WebViewFidoBridge? = null

    constructor(baseUrl: String?, isPasswordUpdate: Boolean) : this() {
        this.baseUrl = baseUrl
        this.isPasswordUpdate = isPasswordUpdate
    }

    constructor(baseUrl: String?, isPasswordUpdate: Boolean, username: String?, password: String?) : this() {
        this.baseUrl = baseUrl
        this.isPasswordUpdate = isPasswordUpdate
        this.username = username
        this.password = password
    }

    private val webLoginUserAgent: String
        get() = (
            Build.MANUFACTURER.substring(0, 1).toUpperCase(Locale.getDefault()) +
                Build.MANUFACTURER.substring(1).toLowerCase(Locale.getDefault()) +
                " " +
                Build.MODEL +
                " (" +
                resources!!.getString(R.string.nc_app_product_name) +
                ")"
            )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewBound(view: View) {
        super.onViewBound(view)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        actionBar?.hide()

        assembledPrefix = resources!!.getString(R.string.nc_talk_login_scheme) + PROTOCOL_SUFFIX + "login/"
        binding.webview.settings.allowFileAccess = false
        binding.webview.settings.allowFileAccessFromFileURLs = false
        binding.webview.settings.javaScriptEnabled = true
        binding.webview.settings.javaScriptCanOpenWindowsAutomatically = false
        binding.webview.settings.domStorageEnabled = true
        binding.webview.settings.setUserAgentString(webLoginUserAgent)
        binding.webview.settings.saveFormData = false
        binding.webview.settings.savePassword = false
        binding.webview.settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
        binding.webview.clearCache(true)
        binding.webview.clearFormData()
        binding.webview.clearHistory()
        WebView.clearClientCertPreferences(null)
        webViewFidoBridge = WebViewFidoBridge.createInstanceForWebView(activity as AppCompatActivity?, binding.webview)
        CookieSyncManager.createInstance(activity)
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        val headers: MutableMap<String, String> = HashMap()
        headers.put("OCS-APIRequest", "true")
        binding.webview.webViewClient = object : WebViewClient() {
            private var basePageLoaded = false
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                webViewFidoBridge?.delegateShouldInterceptRequest(view, request)
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                webViewFidoBridge?.delegateOnPageStarted(view, url, favicon)
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith(assembledPrefix!!)) {
                    parseAndLoginFromWebView(url)
                    return true
                }
                return false
            }

            @Suppress("Detekt.TooGenericExceptionCaught")
            override fun onPageFinished(view: WebView, url: String) {
                try {
                    loginStep++
                    if (!basePageLoaded) {
                        binding.progressBar.visibility = View.GONE
                        binding.webview.visibility = View.VISIBLE

                        basePageLoaded = true
                    }
                    if (!TextUtils.isEmpty(username)) {
                        if (loginStep == 1) {
                            binding.webview.loadUrl(
                                "javascript: {document.getElementsByClassName('login')[0].click(); };"
                            )
                        } else if (!automatedLoginAttempted) {
                            automatedLoginAttempted = true
                            if (TextUtils.isEmpty(password)) {
                                binding.webview.loadUrl(
                                    "javascript:var justStore = document.getElementById('user').value = '$username';"
                                )
                            } else {
                                binding.webview.loadUrl(
                                    "javascript: {" +
                                        "document.getElementById('user').value = '" + username + "';" +
                                        "document.getElementById('password').value = '" + password + "';" +
                                        "document.getElementById('submit').click(); };"
                                )
                            }
                        }
                    }
                } catch (npe: NullPointerException) {
                    // view binding can be null
                    // since this is called asynchronously and UI might have been destroyed in the meantime
                    Log.i(TAG, "UI destroyed - view binding already gone")
                }

                super.onPageFinished(view, url)
            }

            override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
                val user = userManager.currentUser.blockingGet()
                var alias: String? = null
                if (!isPasswordUpdate) {
                    alias = appPreferences!!.temporaryClientCertAlias
                }
                if (TextUtils.isEmpty(alias) && user != null) {
                    alias = user.clientCertificate
                }
                if (!TextUtils.isEmpty(alias)) {
                    val finalAlias = alias
                    Thread {
                        try {
                            val privateKey = KeyChain.getPrivateKey(activity!!, finalAlias!!)
                            val certificates = KeyChain.getCertificateChain(
                                activity!!, finalAlias
                            )
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
                    }.start()
                } else {
                    KeyChain.choosePrivateKeyAlias(
                        activity!!,
                        { chosenAlias: String? ->
                            if (chosenAlias != null) {
                                appPreferences!!.temporaryClientCertAlias = chosenAlias
                                Thread {
                                    var privateKey: PrivateKey? = null
                                    try {
                                        privateKey = KeyChain.getPrivateKey(activity!!, chosenAlias)
                                        val certificates = KeyChain.getCertificateChain(
                                            activity!!, chosenAlias
                                        )
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
                                }.start()
                            } else {
                                request.cancel()
                            }
                        },
                        arrayOf("RSA", "EC"),
                        null,
                        request.host,
                        request.port,
                        null
                    )
                }
            }

            @Suppress("Detekt.TooGenericExceptionCaught")
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                try {
                    val sslCertificate = error.certificate
                    val f: Field = sslCertificate.javaClass.getDeclaredField("mX509Certificate")
                    f.isAccessible = true
                    val cert = f[sslCertificate] as X509Certificate
                    if (cert == null) {
                        handler.cancel()
                    } else {
                        try {
                            magicTrustManager.checkServerTrusted(arrayOf(cert), "generic")
                            handler.proceed()
                        } catch (exception: CertificateException) {
                            eventBus.post(CertificateEvent(cert, magicTrustManager, handler))
                        }
                    }
                } catch (exception: Exception) {
                    handler.cancel()
                }
            }

            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                super.onReceivedError(view, errorCode, description, failingUrl)
            }
        }
        binding.webview.loadUrl("$baseUrl/index.php/login/flow", headers)
    }

    private fun dispose() {
        if (userQueryDisposable != null && !userQueryDisposable!!.isDisposed) {
            userQueryDisposable!!.dispose()
        }
        userQueryDisposable = null
    }

    private fun parseAndLoginFromWebView(dataString: String) {
        val loginData = parseLoginData(assembledPrefix, dataString)
        if (loginData != null) {
            dispose()
            val currentUser = userManager.currentUser.blockingGet()
            var messageType: ApplicationWideMessageHolder.MessageType? = null
            if (!isPasswordUpdate &&
                userManager.checkIfUserExists(loginData.username!!, baseUrl!!).blockingGet()
            ) {
                messageType = ApplicationWideMessageHolder.MessageType.ACCOUNT_UPDATED_NOT_ADDED
            }
            if (userManager.checkIfUserIsScheduledForDeletion(loginData.username!!, baseUrl!!).blockingGet()) {
                ApplicationWideMessageHolder.getInstance().messageType =
                    ApplicationWideMessageHolder.MessageType.ACCOUNT_SCHEDULED_FOR_DELETION
                if (!isPasswordUpdate) {
                    router.popToRoot()
                } else {
                    router.popCurrentController()
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
                router.pushController(
                    RouterTransaction.with(AccountVerificationController(bundle))
                        .pushChangeHandler(HorizontalChangeHandler())
                        .popChangeHandler(HorizontalChangeHandler())
                )
            } else {
                if (isPasswordUpdate) {
                    if (currentUser != null) {
                        currentUser.clientCertificate = appPreferences!!.temporaryClientCertAlias
                        currentUser.token = loginData.token
                        val rowsUpdated = userManager.updateOrCreateUser(currentUser).blockingGet()
                        Log.d(TAG, "User rows updated: $rowsUpdated")

                        if (finalMessageType != null) {
                            ApplicationWideMessageHolder.getInstance().messageType = finalMessageType
                        }

                        val data = Data.Builder().putString(
                            PushRegistrationWorker.ORIGIN,
                            "WebViewLoginController#parseAndLoginFromWebView"
                        ).build()

                        val pushRegistrationWork = OneTimeWorkRequest.Builder(
                            PushRegistrationWorker::class.java
                        )
                            .setInputData(data)
                            .build()

                        WorkManager.getInstance().enqueue(pushRegistrationWork)
                        router.popCurrentController()
                    }
                } else {
                    if (finalMessageType != null) {
                        // FIXME when the user registers a new account that was setup before (aka
                        //  ApplicationWideMessageHolder.MessageType.ACCOUNT_UPDATED_NOT_ADDED)
                        //  The token is not updated in the database and therefore the account not visible/usable
                        ApplicationWideMessageHolder.getInstance().messageType = finalMessageType
                    }
                    router.popToRoot()
                }
            }
        }
    }

    private fun parseLoginData(prefix: String?, dataString: String): LoginData? {
        if (dataString.length < prefix!!.length) {
            return null
        }
        val loginData = LoginData()

        // format is xxx://login/server:xxx&user:xxx&password:xxx
        val data: String = dataString.substring(prefix.length)
        val values: Array<String> = data.split("&").toTypedArray()
        if (values.size != PARAMETER_COUNT) {
            return null
        }
        for (value in values) {
            if (value.startsWith("user" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR)) {
                loginData.username = URLDecoder.decode(
                    value.substring(("user" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR).length)
                )
            } else if (value.startsWith("password" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR)) {
                loginData.token = URLDecoder.decode(
                    value.substring(("password" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR).length)
                )
            } else if (value.startsWith("server" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR)) {
                loginData.serverUrl = URLDecoder.decode(
                    value.substring(("server" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR).length)
                )
            } else {
                return null
            }
        }
        return if (!TextUtils.isEmpty(loginData.serverUrl) && !TextUtils.isEmpty(loginData.username) &&
            !TextUtils.isEmpty(loginData.token)
        ) {
            loginData
        } else {
            null
        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        if (activity != null && resources != null) {
            DisplayUtils.applyColorToStatusBar(
                activity,
                ResourcesCompat.getColor(resources!!, R.color.colorPrimary, null)
            )
            DisplayUtils.applyColorToNavigationBar(
                activity!!.window,
                ResourcesCompat.getColor(resources!!, R.color.colorPrimary, null)
            )
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        dispose()
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    }

    init {
        sharedApplication!!.componentApplication.inject(this)
    }

    override val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.EMPTY

    companion object {
        const val TAG = "WebViewLoginController"
        private const val PROTOCOL_SUFFIX = "://"
        private const val LOGIN_URL_DATA_KEY_VALUE_SEPARATOR = ":"
        private const val PARAMETER_COUNT = 3
    }
}
