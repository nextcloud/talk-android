/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
 * Copyright (C) 2021 Andy Scherzinger (info@andy-scherzinger.de)
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
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.security.KeyChain
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import autodagger.AutoInjector
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.controllers.base.NewBaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.databinding.ControllerServerSelectionBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.generic.Status
import com.nextcloud.talk.utils.AccountUtils.findAccounts
import com.nextcloud.talk.utils.AccountUtils.getAppNameBasedOnPackage
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.UriUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_IS_ACCOUNT_IMPORT
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.security.cert.CertificateException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ServerSelectionController :
    NewBaseController(R.layout.controller_server_selection) {

    private val binding: ControllerServerSelectionBinding by viewBinding(ControllerServerSelectionBinding::bind)

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userUtils: UserUtils

    private var statusQueryDisposable: Disposable? = null

    fun onCertClick() {
        if (activity != null) {
            KeyChain.choosePrivateKeyAlias(
                activity!!,
                { alias: String? ->
                    if (alias != null) {
                        appPreferences!!.temporaryClientCertAlias = alias
                    } else {
                        appPreferences!!.removeTemporaryClientCertAlias()
                    }
                    setCertTextView()
                },
                arrayOf("RSA", "EC"), null, null, -1, null
            )
        }
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        sharedApplication!!.componentApplication.inject(this)
        if (activity != null) {
            activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        actionBar?.hide()

        binding.hostUrlInputHelperText.setText(
            String.format(
                resources!!.getString(R.string.nc_server_helper_text),
                resources!!.getString(R.string.nc_server_product_name)
            )
        )
        binding.serverEntryTextInputLayout.setEndIconOnClickListener { checkServerAndProceed() }
        if (resources!!.getBoolean(R.bool.hide_auth_cert)) {
            binding.certTextView.visibility = View.GONE
        }
        if (resources!!.getBoolean(R.bool.hide_provider) ||
            TextUtils.isEmpty(resources!!.getString(R.string.nc_providers_url)) &&
            TextUtils.isEmpty(resources!!.getString(R.string.nc_import_account_type))
        ) {
            binding.helperTextView.visibility = View.INVISIBLE
        } else {
            if (
                (
                    TextUtils.isEmpty(resources!!.getString(R.string.nc_import_account_type)) ||
                        findAccounts(userUtils.users as List<UserEntity>).isEmpty()
                    ) &&
                userUtils.users.size == 0
            ) {
                binding.helperTextView.setText(R.string.nc_get_from_provider)
                binding.helperTextView.setOnClickListener {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            resources!!
                                .getString(R.string.nc_providers_url)
                        )
                    )
                    startActivity(browserIntent)
                }
            } else if (findAccounts(userUtils.users as List<UserEntity>).size > 0) {
                if (!TextUtils.isEmpty(
                        getAppNameBasedOnPackage(resources!!.getString(R.string.nc_import_accounts_from))
                    )
                ) {
                    if (findAccounts(userUtils.users as List<UserEntity>).size > 1) {
                        binding.helperTextView.setText(
                            String.format(
                                resources!!.getString(R.string.nc_server_import_accounts),
                                getAppNameBasedOnPackage(resources!!.getString(R.string.nc_import_accounts_from))
                            )
                        )
                    } else {
                        binding.helperTextView.setText(
                            String.format(
                                resources!!.getString(R.string.nc_server_import_account),
                                getAppNameBasedOnPackage(resources!!.getString(R.string.nc_import_accounts_from))
                            )
                        )
                    }
                } else {
                    if (findAccounts(userUtils.users as List<UserEntity>).size > 1) {
                        binding.helperTextView.text = resources!!.getString(R.string.nc_server_import_accounts_plain)
                    } else {
                        binding.helperTextView.text = resources!!.getString(R.string.nc_server_import_account_plain)
                    }
                }
                binding.helperTextView.setOnClickListener {
                    val bundle = Bundle()
                    bundle.putBoolean(KEY_IS_ACCOUNT_IMPORT, true)
                    router.pushController(
                        RouterTransaction.with(
                            SwitchAccountController(bundle)
                        )
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler())
                    )
                }
            } else {
                binding.helperTextView.visibility = View.INVISIBLE
            }
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
    }

    @SuppressLint("LongLogTag")
    private fun checkServerAndProceed() {
        dispose()
        try {
            var url: String = binding.serverEntryTextInputEditText.text.toString().trim { it <= ' ' }
            binding.serverEntryTextInputEditText.isEnabled = false
            showserverEntryProgressBar()
            if (binding.helperTextView.visibility != View.INVISIBLE) {
                binding.helperTextView.visibility = View.INVISIBLE
                binding.certTextView.visibility = View.INVISIBLE
            }
            if (url.endsWith("/")) {
                url = url.substring(0, url.length - 1)
            }
            val queryUrl = url + ApiUtils.getUrlPostfixForStatus()
            if (UriUtils.hasHttpProtocollPrefixed(url)) {
                checkServer(queryUrl, false)
            } else {
                checkServer("https://$queryUrl", true)
            }
        } catch (npe: NullPointerException) {
            // view binding can be null
            // since this is called asynchronously and UI might have been destroyed in the meantime
            Log.i(TAG, "UI destroyed - view binding already gone")
        }
    }

    private fun checkServer(queryUrl: String, checkForcedHttps: Boolean) {
        statusQueryDisposable = ncApi.getServerStatus(queryUrl)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ status: Status ->
                val productName = resources!!.getString(R.string.nc_server_product_name)
                val versionString: String = status.getVersion().substring(0, status.getVersion().indexOf("."))
                val version: Int = versionString.toInt()
                if (isServerStatusQueryable(status) && version >= MIN_SERVER_MAJOR_VERSION) {
                    router.pushController(
                        RouterTransaction.with(
                            WebViewLoginController(
                                queryUrl.replace("/status.php", ""),
                                false
                            )
                        )
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler())
                    )
                } else if (!status.isInstalled) {
                    setErrorText(
                        String.format(
                            resources!!.getString(R.string.nc_server_not_installed), productName
                        )
                    )
                } else if (status.isNeedsUpgrade) {
                    setErrorText(
                        String.format(
                            resources!!.getString(R.string.nc_server_db_upgrade_needed),
                            productName
                        )
                    )
                } else if (status.isMaintenance) {
                    setErrorText(
                        String.format(
                            resources!!.getString(R.string.nc_server_maintenance),
                            productName
                        )
                    )
                } else if (!status.getVersion().startsWith("13.")) {
                    setErrorText(
                        String.format(
                            resources!!.getString(R.string.nc_server_version),
                            resources!!.getString(R.string.nc_app_product_name),
                            productName
                        )
                    )
                }
            }, { throwable: Throwable ->
                if (checkForcedHttps) {
                    checkServer(queryUrl.replace("https://", "http://"), false)
                } else {
                    if (throwable.localizedMessage != null) {
                        setErrorText(throwable.localizedMessage)
                    } else if (throwable.cause is CertificateException) {
                        setErrorText(resources!!.getString(R.string.nc_certificate_error))
                    } else {
                        hideserverEntryProgressBar()
                    }

                    binding.serverEntryTextInputEditText.isEnabled = true

                    if (binding.helperTextView.visibility != View.INVISIBLE) {
                        binding.helperTextView.visibility = View.VISIBLE
                        binding.certTextView.visibility = View.VISIBLE
                    }
                    dispose()
                }
            }) {
                hideserverEntryProgressBar()
                if (binding.helperTextView.visibility != View.INVISIBLE) {
                    binding.helperTextView.visibility = View.VISIBLE
                    binding.certTextView.visibility = View.VISIBLE
                }
                dispose()
            }
    }

    private fun isServerStatusQueryable(status: Status): Boolean {
        return status.isInstalled && !status.isMaintenance && !status.isNeedsUpgrade
    }

    private fun setErrorText(text: String) {
        binding.errorText.text = text
        binding.errorText.visibility = View.VISIBLE
        binding.serverEntryProgressBar.visibility = View.GONE
    }

    private fun showserverEntryProgressBar() {
        binding.errorText.visibility = View.GONE
        binding.serverEntryProgressBar.visibility = View.VISIBLE
    }

    private fun hideserverEntryProgressBar() {
        binding.errorText.visibility = View.GONE
        binding.serverEntryProgressBar.visibility = View.INVISIBLE
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        if (ApplicationWideMessageHolder.getInstance().messageType != null) {
            if (ApplicationWideMessageHolder.getInstance().messageType
                == ApplicationWideMessageHolder.MessageType.ACCOUNT_SCHEDULED_FOR_DELETION
            ) {
                setErrorText(resources!!.getString(R.string.nc_account_scheduled_for_deletion))
                ApplicationWideMessageHolder.getInstance().messageType = null
            } else if (ApplicationWideMessageHolder.getInstance().messageType
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
        setCertTextView()
    }

    private fun setCertTextView() {
        if (activity != null) {
            activity!!.runOnUiThread {
                if (!TextUtils.isEmpty(appPreferences!!.temporaryClientCertAlias)) {
                    binding.certTextView.setText(R.string.nc_change_cert_auth)
                } else {
                    binding.certTextView.setText(R.string.nc_configure_cert_auth)
                }
                hideserverEntryProgressBar()
            }
        }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        if (activity != null) {
            activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
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
        const val TAG = "ServerSelectionController"
        const val MIN_SERVER_MAJOR_VERSION = 13
    }
}
