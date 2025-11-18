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
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import autodagger.AutoInjector
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.account.viewmodels.BrowserLoginActivityViewModel
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ActivityWebViewLoginBinding
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BASE_URL
import kotlinx.coroutines.launch
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class BrowserLoginActivity : BaseActivity() {

    private lateinit var binding: ActivityWebViewLoginBinding

    @Inject
    lateinit var viewModel: BrowserLoginActivityViewModel

    private var reauthorizeAccount = false
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
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        actionBar?.hide()
        initSystemBars()
        initViews()
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        handleIntent()
        observe()
    }

    override fun onResume() {
        super.onResume()

        if (viewModel.waitingForBrowser) {
            viewModel.savedResponse?.let {
                viewModel.waitingForBrowser = false
                viewModel.loginNormally2(it)
            }
        }
    }

    init {
        sharedApplication!!.componentApplication.inject(this)
    }

    private fun observe() {
        lifecycleScope.launch {
            viewModel.initialLoginRequestState.collect { state ->
                when (state) {
                    BrowserLoginActivityViewModel.InitialLoginViewState.InitialLoginRequestError -> {
                        Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_SHORT).show()
                    }
                    is BrowserLoginActivityViewModel.InitialLoginViewState.InitialLoginRequestSuccess -> {
                        launchDefaultWebBrowser(state.loginUrl)
                        viewModel.waitingForBrowser = true
                    }
                    BrowserLoginActivityViewModel.InitialLoginViewState.None -> {}
                }
            }
        }

        lifecycleScope.launch {
            viewModel.postLoginState.collect { state ->
                when (state) {
                    BrowserLoginActivityViewModel.PostLoginViewState.None -> {}

                    is BrowserLoginActivityViewModel.PostLoginViewState.PostLoginContinue -> {
                        if (!state.data.isEmpty) {
                            startAccountVerification(state.data)
                        }
                    }
                    BrowserLoginActivityViewModel.PostLoginViewState.PostLoginError -> {
                        Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_SHORT).show()
                    }
                    BrowserLoginActivityViewModel.PostLoginViewState.PostLoginRestartApp -> {
                        restartApp()
                    }
                }
            }
        }
    }

    private fun handleIntent() {
        val extras = intent.extras!!
        val baseUrl = extras.getString(KEY_BASE_URL)

        if (extras.containsKey(BundleKeys.KEY_REAUTHORIZE_ACCOUNT)) {
            reauthorizeAccount = extras.getBoolean(BundleKeys.KEY_REAUTHORIZE_ACCOUNT)
        }

        if (extras.containsKey(BundleKeys.KEY_FROM_QR)) {
            val uri = extras.getString(BundleKeys.KEY_FROM_QR)!!
            viewModel.loginWithQR(uri, reauthorizeAccount)
        } else if (baseUrl != null) {
            viewModel.loginNormally(baseUrl, reauthorizeAccount)
        }
    }

    private fun initViews() {
        viewThemeUtils.material.colorMaterialButtonFilledOnPrimary(binding.cancelLoginBtn)
        viewThemeUtils.material.colorProgressBar(binding.progressBar)

        binding.cancelLoginBtn.setOnClickListener {
            viewModel.cancelLogin()
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun launchDefaultWebBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun startAccountVerification(bundle: Bundle) {
        val intent = Intent(context, AccountVerificationActivity::class.java)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun restartApp() {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    public override fun onDestroy() {
        super.onDestroy()
    }

    override val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.EMPTY

    companion object {
        private val TAG = BrowserLoginActivity::class.java.simpleName
    }
}
