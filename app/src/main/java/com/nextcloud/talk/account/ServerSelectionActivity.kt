/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.account

import android.Manifest
import android.accounts.Account
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.security.KeyChain
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import autodagger.AutoInjector
import com.blikoon.qrcodescanner.QrCodeActivity
import com.github.dhaval2404.imagepicker.util.PermissionUtil
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ActivityServerSelectionBinding
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.models.json.generic.Status
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.AccountUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.UriUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.ADD_ADDITIONAL_ACCOUNT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_IS_ACCOUNT_IMPORT
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.security.cert.CertificateException
import javax.inject.Inject

@Suppress("TooManyFunctions")
@AutoInjector(NextcloudTalkApplication::class)
class ServerSelectionActivity : BaseActivity() {

    private lateinit var binding: ActivityServerSelectionBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    private var statusQueryDisposable: Disposable? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (intent.hasExtra(ADD_ADDITIONAL_ACCOUNT) && intent.getBooleanExtra(ADD_ADDITIONAL_ACCOUNT, false)) {
                finish()
            } else {
                finishAffinity()
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        binding = ActivityServerSelectionBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        actionBar?.hide()
        initSystemBars()

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onResume() {
        super.onResume()

        binding.hostUrlInputHelperText.text = String.format(
            resources!!.getString(R.string.nc_server_helper_text),
            resources!!.getString(R.string.nc_server_product_name)
        )
        binding.serverEntryTextInputLayout.setEndIconOnClickListener { checkServerAndProceed() }

        if (resources!!.getBoolean(R.bool.hide_auth_cert)) {
            binding.certTextView.visibility = View.GONE
        }

        val loggedInUsers = userManager.users.blockingGet()
        val availableAccounts = AccountUtils.findAvailableAccountsOnDevice(loggedInUsers)

        if (isImportAccountNameSet() && availableAccounts.isNotEmpty()) {
            showImportAccountsInfo(availableAccounts)
        } else if (isAbleToShowProviderLink() && loggedInUsers.isEmpty()) {
            showVisitProvidersInfo()
        } else {
            binding.importOrChooseProviderText.visibility = View.INVISIBLE
        }

        binding.serverEntryTextInputEditText.requestFocus()
        if (!TextUtils.isEmpty(resources!!.getString(R.string.weblogin_url))) {
            binding.serverEntryTextInputEditText.setText(resources!!.getString(R.string.weblogin_url))
            checkServerAndProceed()
        }
        binding.serverEntryTextInputEditText.setOnEditorActionListener { _: TextView?, i: Int, _: KeyEvent? ->
            if (i == EditorInfo.IME_ACTION_DONE) {
                checkServerAndProceed()
            }
            false
        }
        binding.certTextView.setOnClickListener { onCertClick() }

        binding.scanQr.setOnClickListener { onScan() }

        if (ApplicationWideMessageHolder.getInstance().messageType != null) {
            if (ApplicationWideMessageHolder.getInstance().messageType
                == ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK
            ) {
                setErrorText(resources!!.getString(R.string.nc_settings_no_talk_installed))
            } else if (ApplicationWideMessageHolder.getInstance().messageType
                == ApplicationWideMessageHolder.MessageType.FAILED_TO_IMPORT_ACCOUNT
            ) {
                setErrorText(resources!!.getString(R.string.nc_server_failed_to_import_account))
            }
            ApplicationWideMessageHolder.getInstance().messageType = null
        }
        setCertTextView()
    }

    fun onCertClick() {
        KeyChain.choosePrivateKeyAlias(
            this,
            { alias: String? ->
                if (alias != null) {
                    appPreferences.temporaryClientCertAlias = alias
                } else {
                    appPreferences.removeTemporaryClientCertAlias()
                }
                setCertTextView()
            },
            arrayOf("RSA", "EC"),
            null,
            null,
            -1,
            null
        )
    }

    private fun isAbleToShowProviderLink(): Boolean =
        !resources!!.getBoolean(R.bool.hide_provider) &&
            !TextUtils.isEmpty(resources!!.getString(R.string.nc_providers_url))

    private fun showImportAccountsInfo(availableAccounts: List<Account>) {
        if (!TextUtils.isEmpty(
                AccountUtils.getAppNameBasedOnPackage(resources!!.getString(R.string.nc_import_accounts_from))
            )
        ) {
            if (availableAccounts.size > 1) {
                binding.importOrChooseProviderText.text = String.format(
                    resources!!.getString(R.string.nc_server_import_accounts),
                    AccountUtils.getAppNameBasedOnPackage(resources!!.getString(R.string.nc_import_accounts_from))
                )
            } else {
                binding.importOrChooseProviderText.text = String.format(
                    resources!!.getString(R.string.nc_server_import_account),
                    AccountUtils.getAppNameBasedOnPackage(resources!!.getString(R.string.nc_import_accounts_from))
                )
            }
        } else {
            if (availableAccounts.size > 1) {
                binding.importOrChooseProviderText.text =
                    resources!!.getString(R.string.nc_server_import_accounts_plain)
            } else {
                binding.importOrChooseProviderText.text =
                    resources!!.getString(R.string.nc_server_import_account_plain)
            }
        }
        binding.importOrChooseProviderText.setOnClickListener {
            val bundle = Bundle()
            bundle.putBoolean(KEY_IS_ACCOUNT_IMPORT, true)
            val intent = Intent(context, SwitchAccountActivity::class.java)
            intent.putExtras(bundle)
            startActivity(intent)
        }
    }

    private fun showVisitProvidersInfo() {
        binding.importOrChooseProviderText.setText(R.string.nc_get_from_provider)
        binding.importOrChooseProviderText.setOnClickListener {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                resources!!.getString(R.string.nc_providers_url).toUri()
            )
            startActivity(browserIntent)
        }
    }

    private fun isImportAccountNameSet(): Boolean =
        !TextUtils.isEmpty(resources!!.getString(R.string.nc_import_account_type))

    @SuppressLint("LongLogTag")
    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun checkServerAndProceed() {
        dispose()
        var url: String = binding.serverEntryTextInputEditText.text.toString().trim()
        showserverEntryProgressBar()
        if (binding.importOrChooseProviderText.visibility != View.INVISIBLE) {
            binding.importOrChooseProviderText.visibility = View.INVISIBLE
            binding.certTextView.visibility = View.INVISIBLE
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length - 1)
        }

        if (UriUtils.hasHttpProtocolPrefixed(url)) {
            checkServer(url, false)
        } else {
            checkServer("https://$url", true)
        }
    }

    private fun checkServer(url: String, checkForcedHttps: Boolean) {
        val queryStatusUrl = url + ApiUtils.getUrlPostfixForStatus()

        statusQueryDisposable = ncApi.getServerStatus(queryStatusUrl)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ status: Status ->
                val versionString: String = status.version!!.substring(0, status.version!!.indexOf("."))
                val version: Int = versionString.toInt()

                if (isServerStatusQueryable(status) && version >= MIN_SERVER_MAJOR_VERSION) {
                    findServerTalkApp(url)
                } else {
                    showErrorTextForStatus(status)
                }
            }, { throwable: Throwable ->
                if (checkForcedHttps) {
                    checkServer(queryStatusUrl.replace("https://", "http://"), false)
                } else {
                    if (throwable.localizedMessage != null) {
                        setErrorText(throwable.localizedMessage)
                    } else if (throwable.cause is CertificateException) {
                        setErrorText(resources!!.getString(R.string.nc_certificate_error))
                    } else {
                        hideserverEntryProgressBar()
                    }

                    if (binding.importOrChooseProviderText.visibility != View.INVISIBLE) {
                        binding.importOrChooseProviderText.visibility = View.VISIBLE
                        binding.certTextView.visibility = View.VISIBLE
                    }
                    dispose()
                }
            }) {
                hideserverEntryProgressBar()
                if (binding.importOrChooseProviderText.visibility != View.INVISIBLE) {
                    binding.importOrChooseProviderText.visibility = View.VISIBLE
                    binding.certTextView.visibility = View.VISIBLE
                }
                dispose()
            }
    }

    private fun showErrorTextForStatus(status: Status) {
        if (!status.installed) {
            setErrorText(
                String.format(
                    resources!!.getString(R.string.nc_server_not_installed),
                    resources!!.getString(R.string.nc_server_product_name)
                )
            )
        } else if (status.needsUpgrade) {
            setErrorText(
                String.format(
                    resources!!.getString(R.string.nc_server_db_upgrade_needed),
                    resources!!.getString(R.string.nc_server_product_name)
                )
            )
        } else if (status.maintenance) {
            setErrorText(
                String.format(
                    resources!!.getString(R.string.nc_server_maintenance),
                    resources!!.getString(R.string.nc_server_product_name)
                )
            )
        } else if (!status.version!!.startsWith("13.")) {
            setErrorText(
                String.format(
                    resources!!.getString(R.string.nc_server_version),
                    resources!!.getString(R.string.nc_app_product_name),
                    resources!!.getString(R.string.nc_server_product_name)
                )
            )
        }
    }

    private fun findServerTalkApp(queryUrl: String) {
        ncApi.getCapabilities(ApiUtils.getUrlForCapabilities(queryUrl))
            .subscribeOn(Schedulers.io())
            .subscribe(object : Observer<CapabilitiesOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(capabilitiesOverall: CapabilitiesOverall) {
                    val capabilities = capabilitiesOverall.ocs?.data?.capabilities

                    val hasTalk =
                        capabilities?.spreedCapability != null &&
                            capabilities.spreedCapability?.features != null &&
                            capabilities.spreedCapability?.features?.isNotEmpty() == true

                    if (hasTalk) {
                        runOnUiThread {
                            if (CapabilitiesUtil.isServerEOL(capabilitiesOverall.ocs?.data?.serverVersion?.major)) {
                                if (resources != null) {
                                    runOnUiThread {
                                        setErrorText(resources!!.getString(R.string.nc_settings_server_eol))
                                    }
                                }
                            } else {
                                val bundle = Bundle()
                                bundle.putString(BundleKeys.KEY_BASE_URL, queryUrl.replace("/status.php", ""))

                                val intent = Intent(context, WebViewLoginActivity::class.java)
                                intent.putExtras(bundle)
                                startActivity(intent)
                            }
                        }
                    } else {
                        if (resources != null) {
                            runOnUiThread {
                                setErrorText(resources!!.getString(R.string.nc_server_unsupported))
                            }
                        }
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error while checking capabilities", e)
                    if (resources != null) {
                        runOnUiThread {
                            setErrorText(resources!!.getString(R.string.nc_common_error_sorry))
                        }
                    }
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun isServerStatusQueryable(status: Status): Boolean =
        status.installed && !status.maintenance && !status.needsUpgrade

    private fun setErrorText(text: String?) {
        binding.errorWrapper.visibility = View.VISIBLE
        binding.errorText.text = text
        hideserverEntryProgressBar()
    }

    private fun showserverEntryProgressBar() {
        binding.errorWrapper.visibility = View.INVISIBLE
        binding.serverEntryProgressBar.visibility = View.VISIBLE
    }

    private fun hideserverEntryProgressBar() {
        binding.serverEntryProgressBar.visibility = View.INVISIBLE
    }

    @SuppressLint("LongLogTag")
    private fun setCertTextView() {
        runOnUiThread {
            if (!TextUtils.isEmpty(appPreferences.temporaryClientCertAlias)) {
                binding.certTextView.setText(R.string.nc_change_cert_auth)
            } else {
                binding.certTextView.setText(R.string.nc_configure_cert_auth)
            }
            hideserverEntryProgressBar()
        }
    }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission was granted
                startQRScanner()
            }
        }

    fun onScan() {
        if (PermissionUtil.isPermissionGranted(this, Manifest.permission.CAMERA)) {
            startQRScanner()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startQRScanner() {
        val intent = Intent(this, QrCodeActivity::class.java)
        qrScanResultLauncher.launch(intent)
    }

    private val qrScanResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data

                if (data == null) {
                    return@registerForActivityResult
                }

                val resultData = data.getStringExtra(QR_URI)

                if (resultData == null || !resultData.startsWith("nc")) {
                    Snackbar.make(binding.root, getString(R.string.qr_code_error), Snackbar.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                val intent = Intent(this, WebViewLoginActivity::class.java)
                val bundle = bundleOf().apply {
                    putString(BundleKeys.KEY_FROM_QR, resultData)
                }
                intent.putExtras(bundle)
                startActivity(intent)
            }
        }

    public override fun onDestroy() {
        super.onDestroy()
        dispose()
    }

    private fun dispose() {
        if (statusQueryDisposable != null && !statusQueryDisposable!!.isDisposed) {
            statusQueryDisposable!!.dispose()
        }
        statusQueryDisposable = null
    }

    override val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.EMPTY

    companion object {
        private val TAG = ServerSelectionActivity::class.java.simpleName
        const val MIN_SERVER_MAJOR_VERSION = 13
        private const val QR_URI = "com.blikoon.qrcodescanner.got_qr_scan_relult"
    }
}
