/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.account

import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.activity.OnBackPressedCallback
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ActivityWebViewLoginBinding
import com.nextcloud.talk.events.CertificateEvent
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.models.LoginData
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BASE_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ORIGINAL_PROTOCOL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USERNAME
import com.nextcloud.talk.utils.ssl.TrustManager
import de.cotech.hw.fido.WebViewFidoBridge
import de.cotech.hw.fido2.WebViewWebauthnBridge
import de.cotech.hw.fido2.ui.WebauthnDialogOptions
import io.reactivex.disposables.Disposable
import java.lang.reflect.Field
import java.net.CookieManager
import java.net.URLDecoder
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Locale
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class WebViewLoginActivity : BaseActivity() {

    private lateinit var binding: ActivityWebViewLoginBinding

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var trustManager: TrustManager

    @Inject
    lateinit var cookieManager: CookieManager

    private var assembledPrefix: String? = null
    private var userQueryDisposable: Disposable? = null
    private var baseUrl: String? = null
    private var reauthorizeAccount = false
    private var username: String? = null
    private var password: String? = null
    private var loginStep = 0
    private var automatedLoginAttempted = false
    private var webViewFidoBridge: WebViewFidoBridge? = null
    private var webViewWebauthnBridge: WebViewWebauthnBridge? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val intent = Intent(context, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
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

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        binding = ActivityWebViewLoginBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        actionBar?.hide()
        setupSystemColors()

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        handleIntent()
        setupWebView()
    }

    private fun handleIntent() {
        val extras = intent.extras!!
        baseUrl = extras.getString(KEY_BASE_URL)
        username = extras.getString(KEY_USERNAME)

        if (extras.containsKey(BundleKeys.KEY_REAUTHORIZE_ACCOUNT)) {
            reauthorizeAccount = extras.getBoolean(BundleKeys.KEY_REAUTHORIZE_ACCOUNT)
        }

        if (extras.containsKey(BundleKeys.KEY_PASSWORD)) {
            password = extras.getString(BundleKeys.KEY_PASSWORD)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        assembledPrefix = resources!!.getString(R.string.nc_talk_login_scheme) + PROTOCOL_SUFFIX + "login/"
        binding.webview.settings.allowFileAccess = false
        binding.webview.settings.allowFileAccessFromFileURLs = false
        binding.webview.settings.javaScriptEnabled = true
        binding.webview.settings.javaScriptCanOpenWindowsAutomatically = false
        binding.webview.settings.domStorageEnabled = true
        binding.webview.settings.userAgentString = webLoginUserAgent
        binding.webview.settings.saveFormData = false
        binding.webview.settings.savePassword = false
        binding.webview.settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
        binding.webview.clearCache(true)
        binding.webview.clearFormData()
        binding.webview.clearHistory()
        WebView.clearClientCertPreferences(null)
        webViewFidoBridge = WebViewFidoBridge.createInstanceForWebView(this, binding.webview)

        val webauthnOptionsBuilder = WebauthnDialogOptions.builder().setShowSdkLogo(true).setAllowSkipPin(true)
        webViewWebauthnBridge = WebViewWebauthnBridge.createInstanceForWebView(
            this, binding.webview, webauthnOptionsBuilder
        )

        CookieSyncManager.createInstance(this)
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        val headers: MutableMap<String, String> = HashMap()
        headers["OCS-APIRequest"] = "true"
        binding.webview.webViewClient = object : WebViewClient() {
            private var basePageLoaded = false
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                webViewFidoBridge?.delegateShouldInterceptRequest(view, request)
                webViewWebauthnBridge?.delegateShouldInterceptRequest(view, request)
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                webViewFidoBridge?.delegateOnPageStarted(view, url, favicon)
                webViewWebauthnBridge?.delegateOnPageStarted(view, url, favicon)
            }

            @Deprecated("Use shouldOverrideUrlLoading(WebView view, WebResourceRequest request)")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith(assembledPrefix!!)) {
                    parseAndLoginFromWebView(url)
                    return true
                }
                return false
            }

            @Suppress("Detekt.TooGenericExceptionCaught")
            override fun onPageFinished(view: WebView, url: String) {
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

                super.onPageFinished(view, url)
            }

            override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
                val user = userManager.currentUser.blockingGet()
                var alias: String? = null
                if (!reauthorizeAccount) {
                    alias = appPreferences.temporaryClientCertAlias
                }
                if (TextUtils.isEmpty(alias) && user != null) {
                    alias = user.clientCertificate
                }
                if (!TextUtils.isEmpty(alias)) {
                    val finalAlias = alias
                    Thread {
                        try {
                            val privateKey = KeyChain.getPrivateKey(applicationContext, finalAlias!!)
                            val certificates = KeyChain.getCertificateChain(
                                applicationContext,
                                finalAlias
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
                        this@WebViewLoginActivity,
                        { chosenAlias: String? ->
                            if (chosenAlias != null) {
                                appPreferences!!.temporaryClientCertAlias = chosenAlias
                                Thread {
                                    var privateKey: PrivateKey? = null
                                    try {
                                        privateKey = KeyChain.getPrivateKey(applicationContext, chosenAlias)
                                        val certificates = KeyChain.getCertificateChain(
                                            applicationContext,
                                            chosenAlias
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
                            trustManager.checkServerTrusted(arrayOf(cert), "generic")
                            handler.proceed()
                        } catch (exception: CertificateException) {
                            eventBus.post(CertificateEvent(cert, trustManager, handler))
                        }
                    }
                } catch (exception: Exception) {
                    handler.cancel()
                }
            }

            @Deprecated("Deprecated in super implementation")
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
            cookieManager.cookieStore.removeAll()

            if (userManager.checkIfUserIsScheduledForDeletion(loginData.username!!, baseUrl!!).blockingGet()) {
                Log.e(TAG, "Tried to add already existing user who is scheduled for deletion.")
                Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                // however the user is not yet deleted, just start AccountRemovalWorker again to make sure to delete it.
                startAccountRemovalWorkerAndRestartApp()
            } else if (userManager.checkIfUserExists(loginData.username!!, baseUrl!!).blockingGet()) {
                if (reauthorizeAccount) {
                    updateUserAndRestartApp(loginData)
                } else {
                    Log.w(TAG, "It was tried to add an account that account already exists. Skipped user creation.")
                    restartApp()
                }
            } else {
                startAccountVerification(loginData)
            }
        }
    }

    private fun startAccountVerification(loginData: LoginData) {
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
        val intent = Intent(context, AccountVerificationActivity::class.java)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun restartApp() {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun updateUserAndRestartApp(loginData: LoginData) {
        val currentUser = userManager.currentUser.blockingGet()
        if (currentUser != null) {
            currentUser.clientCertificate = appPreferences.temporaryClientCertAlias
            currentUser.token = loginData.token
            val rowsUpdated = userManager.updateOrCreateUser(currentUser).blockingGet()
            Log.d(TAG, "User rows updated: $rowsUpdated")
            restartApp()
        }
    }

    private fun startAccountRemovalWorkerAndRestartApp() {
        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java).build()
        WorkManager.getInstance(applicationContext).enqueue(accountRemovalWork)

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(accountRemovalWork.id)
            .observeForever { workInfo: WorkInfo ->

                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        restartApp()
                    }

                    else -> {}
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

    public override fun onDestroy() {
        super.onDestroy()
        dispose()
    }

    init {
        sharedApplication!!.componentApplication.inject(this)
    }

    override val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.EMPTY

    companion object {
        private val TAG = WebViewLoginActivity::class.java.simpleName
        private const val PROTOCOL_SUFFIX = "://"
        private const val LOGIN_URL_DATA_KEY_VALUE_SEPARATOR = ":"
        private const val PARAMETER_COUNT = 3
    }
}
