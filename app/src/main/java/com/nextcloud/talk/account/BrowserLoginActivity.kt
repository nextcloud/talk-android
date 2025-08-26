/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.account

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonParser
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ActivityWebViewLoginBinding
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.models.LoginData
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BASE_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ORIGINAL_PROTOCOL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USERNAME
import com.nextcloud.talk.utils.ssl.SSLSocketFactoryCompat
import com.nextcloud.talk.utils.ssl.TrustManager
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSession

@AutoInjector(NextcloudTalkApplication::class)
class BrowserLoginActivity : BaseActivity() {

    private lateinit var binding: ActivityWebViewLoginBinding

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var trustManager: TrustManager

    @Inject
    lateinit var socketFactory: SSLSocketFactoryCompat

    private var userQueryDisposable: Disposable? = null
    private var baseUrl: String? = null
    private var reauthorizeAccount = false
    private var username: String? = null
    private var password: String? = null
    private val loginFlowExecutorService: ScheduledExecutorService? = Executors.newSingleThreadScheduledExecutor()
    private var isLoginProcessCompleted = false
    private var token: String = ""

    private lateinit var okHttpClient: OkHttpClient

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val intent = Intent(context, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        binding = ActivityWebViewLoginBinding.inflate(layoutInflater)
        okHttpClient = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .connectionSpecs(listOf(ConnectionSpec.COMPATIBLE_TLS))
            .sslSocketFactory(socketFactory, trustManager)
            .hostnameVerifier { _: String?, _: SSLSession? -> true }
            .build()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        actionBar?.hide()
        initSystemBars()
        initViews()
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        handleIntent()
        anonymouslyPostLoginRequest()
        lifecycle.addObserver(lifecycleEventObserver)
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

    private fun initViews() {
        viewThemeUtils.material.colorMaterialButtonFilledOnPrimary(binding.cancelLoginBtn)
        viewThemeUtils.material.colorProgressBar(binding.progressBar)

        binding.cancelLoginBtn.setOnClickListener {
            lifecycle.removeObserver(lifecycleEventObserver)
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun anonymouslyPostLoginRequest() {
        CoroutineScope(Dispatchers.IO).launch {
            val url = "$baseUrl/index.php/login/v2"
            try {
                val response = getResponseOfAnonymouslyPostLoginRequest(url)
                val jsonObject: com.google.gson.JsonObject = JsonParser.parseString(response).asJsonObject
                val loginUrl: String = getLoginUrl(jsonObject)
                withContext(Dispatchers.Main) {
                    launchDefaultWebBrowser(loginUrl)
                }
                token = jsonObject.getAsJsonObject("poll").get("token").asString
            } catch (e: SSLHandshakeException) {
                Log.e(TAG, "Error caught at anonymouslyPostLoginRequest: $e")
            }
        }
    }

    private fun getResponseOfAnonymouslyPostLoginRequest(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .addHeader("Clear-Site-Data", "cookies")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }
            return response.body?.string()
        }
    }

    private fun getLoginUrl(response: com.google.gson.JsonObject): String {
        var result: String? = response.get("login").asString
        if (result == null) {
            result = ""
        }

        return result
    }

    private fun launchDefaultWebBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private val lifecycleEventObserver = LifecycleEventObserver { lifecycleOwner, event ->
        if (event === Lifecycle.Event.ON_START && token != "") {
            Log.d(TAG, "Start poolLogin")
            poolLogin()
        }
    }

    private fun poolLogin() {
        loginFlowExecutorService?.scheduleWithFixedDelay({
            if (!isLoginProcessCompleted) {
                performLoginFlowV2()
            }
        }, 0, INTERVAL, TimeUnit.SECONDS)
    }

    private fun performLoginFlowV2() {
        val postRequestUrl = "$baseUrl/login/v2/poll"

        val requestBody: RequestBody = FormBody.Builder()
            .add("token", token)
            .build()

        val request = Request.Builder()
            .url(postRequestUrl)
            .post(requestBody)
            .build()

        try {
            okHttpClient.newCall(request).execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected code $response")
                    }
                    val status: Int = response.code
                    val response = response.body?.string()

                    Log.d(TAG, "performLoginFlowV2 status: $status")
                    Log.d(TAG, "performLoginFlowV2 response: $response")

                    if (response?.isNotEmpty() == true) {
                        runOnUiThread { completeLoginFlow(response, status) }
                    }
                }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error caught at performLoginFlowV2: $e")
        }
    }

    private fun completeLoginFlow(response: String, status: Int) {
        try {
            val jsonObject = JSONObject(response)

            val server: String = jsonObject.getString("server")
            val loginName: String = jsonObject.getString("loginName")
            val appPassword: String = jsonObject.getString("appPassword")

            val loginData = LoginData()
            loginData.serverUrl = server
            loginData.username = loginName
            loginData.token = appPassword

            isLoginProcessCompleted =
                (status == HTTP_OK && !server.isEmpty() && !loginName.isEmpty() && !appPassword.isEmpty())

            parseAndLogin(loginData)
        } catch (e: JSONException) {
            Log.e(TAG, "Error caught at completeLoginFlow: $e")
        }

        loginFlowExecutorService?.shutdown()
        lifecycle.removeObserver(lifecycleEventObserver)
    }

    private fun dispose() {
        if (userQueryDisposable != null && !userQueryDisposable!!.isDisposed) {
            userQueryDisposable!!.dispose()
        }
        userQueryDisposable = null
    }

    private fun parseAndLogin(loginData: LoginData) {
        dispose()

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
        val currentUser = currentUserProvider.currentUser.blockingGet()
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
            .observeForever { workInfo: WorkInfo? ->

                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        restartApp()
                    }

                    else -> {}
                }
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
        private val TAG = BrowserLoginActivity::class.java.simpleName
        private const val INTERVAL = 30L
        private const val HTTP_OK = 200
    }
}
