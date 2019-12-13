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
package com.nextcloud.talk.controllers

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.security.KeyChain
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.TextView
import butterknife.BindView
import butterknife.OnClick
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.models.json.generic.Status
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.utils.AccountUtils.findAccounts
import com.nextcloud.talk.utils.AccountUtils.getAppNameBasedOnPackage
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_IS_ACCOUNT_IMPORT
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import com.uber.autodispose.AutoDispose
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import studio.carbonylgroup.textfieldboxes.ExtendedEditText
import studio.carbonylgroup.textfieldboxes.TextFieldBoxes
import java.security.cert.CertificateException

class ServerSelectionController : BaseController() {
    @JvmField
    @BindView(R.id.extended_edit_text)
    var serverEntry: ExtendedEditText? = null
    @JvmField
    @BindView(R.id.text_field_boxes)
    var textFieldBoxes: TextFieldBoxes? = null
    @JvmField
    @BindView(R.id.progress_bar)
    var progressBar: ProgressBar? = null
    @JvmField
    @BindView(R.id.helper_text_view)
    var providersTextView: TextView? = null
    @JvmField
    @BindView(R.id.cert_text_view)
    var certTextView: TextView? = null

    val usersRepository: UsersRepository by inject()
    val ncApi: NcApi by inject()

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.controller_server_selection, container, false)
    }

    @SuppressLint("LongLogTag")
    @OnClick(R.id.cert_text_view)
    fun onCertClick() {
        if (activity != null) {
            KeyChain.choosePrivateKeyAlias(activity!!, { alias: String? ->
                if (alias != null) {
                    appPreferences.temporaryClientCertAlias = alias
                } else {
                    appPreferences.removeTemporaryClientCertAlias()
                }
                setCertTextView()
            }, arrayOf("RSA", "EC"), null, null, -1, null)
        }
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        if (activity != null) {
            activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (actionBar != null) {
            actionBar!!.hide()
        }
        textFieldBoxes!!.endIconImageButton
                .setBackgroundDrawable(resources!!.getDrawable(R.drawable.ic_arrow_forward_white_24px))
        textFieldBoxes!!.endIconImageButton.alpha = 0.5f
        textFieldBoxes!!.endIconImageButton.isEnabled = false
        textFieldBoxes!!.endIconImageButton.visibility = View.VISIBLE
        textFieldBoxes!!.endIconImageButton.setOnClickListener { view1: View? -> checkServerAndProceed() }
        if (TextUtils.isEmpty(resources!!.getString(R.string.nc_providers_url))
                && TextUtils.isEmpty(resources!!.getString(R.string.nc_import_account_type))) {
            providersTextView!!.visibility = View.INVISIBLE
        } else {
            GlobalScope.launch {
                val users = usersRepository.getUsers()
                val usersSize = users.count()

                withContext(Dispatchers.Main) {
                    if ((TextUtils.isEmpty(resources!!.getString(R.string.nc_import_account_type)) ||
                                    findAccounts(users).isEmpty()) &&
                            usersSize == 0) {
                        providersTextView!!.setText(R.string.nc_get_from_provider)
                        providersTextView!!.setOnClickListener { view12: View? ->
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(resources!!
                                    .getString(R.string.nc_providers_url)))
                            startActivity(browserIntent)
                        }
                    } else if (findAccounts(users).isNotEmpty()) {
                        if (!TextUtils.isEmpty(getAppNameBasedOnPackage(resources!!
                                        .getString(R.string.nc_import_accounts_from)))) {
                            if (findAccounts(users).size > 1) {
                                providersTextView!!.text = String.format(resources!!.getString(R.string.nc_server_import_accounts),
                                        getAppNameBasedOnPackage(resources!!
                                                .getString(R.string.nc_import_accounts_from)))
                            } else {
                                providersTextView!!.text = String.format(resources!!.getString(R.string.nc_server_import_account),
                                        getAppNameBasedOnPackage(resources!!
                                                .getString(R.string.nc_import_accounts_from)))
                            }
                        } else {
                            if (findAccounts(users).size > 1) {
                                providersTextView!!.text = resources!!.getString(R.string.nc_server_import_accounts_plain)
                            } else {
                                providersTextView!!.text = resources!!.getString(R.string.nc_server_import_account_plain)
                            }
                        }
                        providersTextView!!.setOnClickListener { view13: View? ->
                            val bundle = Bundle()
                            bundle.putBoolean(KEY_IS_ACCOUNT_IMPORT, true)
                            router.pushController(RouterTransaction.with(
                                    SwitchAccountController(bundle))
                                    .pushChangeHandler(HorizontalChangeHandler())
                                    .popChangeHandler(HorizontalChangeHandler()))
                        }
                    } else {
                        providersTextView!!.visibility = View.INVISIBLE
                    }
                }
                serverEntry!!.requestFocus()
                serverEntry!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                    override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                    override fun afterTextChanged(editable: Editable) {
                        if (!textFieldBoxes!!.isOnError && !TextUtils.isEmpty(serverEntry!!.text)) {
                            toggleProceedButton(true)
                        } else {
                            toggleProceedButton(false)
                        }
                    }
                })
                serverEntry!!.setOnEditorActionListener { textView: TextView?, i: Int, keyEvent: KeyEvent? ->
                    if (i == EditorInfo.IME_ACTION_DONE) {
                        checkServerAndProceed()
                    }
                    false
                }
            }
        }
    }

    private fun toggleProceedButton(show: Boolean) {
        textFieldBoxes!!.endIconImageButton.isEnabled = show
        if (show) {
            textFieldBoxes!!.endIconImageButton.alpha = 1f
        } else {
            textFieldBoxes!!.endIconImageButton.alpha = 0.5f
        }
    }

    private fun checkServerAndProceed() {
        var url = serverEntry!!.text.toString().trim { it <= ' ' }
        serverEntry!!.isEnabled = false
        progressBar!!.visibility = View.VISIBLE
        if (providersTextView!!.visibility != View.INVISIBLE) {
            providersTextView!!.visibility = View.INVISIBLE
            certTextView!!.visibility = View.INVISIBLE
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length - 1)
        }
        val queryUrl = url + ApiUtils.getUrlPostfixForStatus()
        if (url.startsWith("http://") || url.startsWith("https://")) {
            checkServer(queryUrl, false)
        } else {
            checkServer("https://$queryUrl", true)
        }
    }

    private fun checkServer(queryUrl: String, checkForcedHttps: Boolean) {
        ncApi.getServerStatus(queryUrl)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .`as`(AutoDispose.autoDisposable(scopeProvider))
                .subscribe({ status: Status ->
                    val productName = resources!!.getString(R.string.nc_server_product_name)
                    val versionString: String = status.version.substring(0, status.version.indexOf("."))
                    val version = versionString.toInt()
                    if (status.installed && !status.maintenance &&
                            !status.needsUpgrade && version >= 13) {
                        router.pushController(RouterTransaction.with(
                                WebViewLoginController(queryUrl.replace("/status.php", ""),
                                        false))
                                .pushChangeHandler(HorizontalChangeHandler())
                                .popChangeHandler(HorizontalChangeHandler()))
                    } else if (!status.installed) {
                        textFieldBoxes!!.setError(String.format(
                                resources!!.getString(R.string.nc_server_not_installed), productName),
                                true)
                        toggleProceedButton(false)
                    } else if (status.needsUpgrade) {
                        textFieldBoxes!!.setError(String.format(resources!!.getString(R.string.nc_server_db_upgrade_needed),
                                productName), true)
                        toggleProceedButton(false)
                    } else if (status.maintenance) {
                        textFieldBoxes!!.setError(String.format(resources!!.getString(R.string.nc_server_maintenance),
                                productName),
                                true)
                        toggleProceedButton(false)
                    } else if (!status.version.startsWith("13.")) {
                        textFieldBoxes!!.setError(String.format(resources!!.getString(R.string.nc_server_version),
                                resources!!.getString(R.string.nc_app_name)
                                , productName), true)
                        toggleProceedButton(false)
                    }
                }, { throwable: Throwable ->
                    if (checkForcedHttps) {
                        checkServer(queryUrl.replace("https://", "http://"), false)
                    } else {
                        if (throwable.localizedMessage != null) {
                            textFieldBoxes!!.setError(throwable.localizedMessage, true)
                        } else if (throwable.cause is CertificateException) {
                            textFieldBoxes!!.setError(resources!!.getString(R.string.nc_certificate_error),
                                    false)
                        }
                        if (serverEntry != null) {
                            serverEntry!!.isEnabled = true
                        }
                        progressBar!!.visibility = View.INVISIBLE
                        if (providersTextView!!.visibility != View.INVISIBLE) {
                            providersTextView!!.visibility = View.VISIBLE
                            certTextView!!.visibility = View.VISIBLE
                        }
                        toggleProceedButton(false)
                    }
                }) {
                    progressBar!!.visibility = View.INVISIBLE
                    if (providersTextView!!.visibility != View.INVISIBLE) {
                        providersTextView!!.visibility = View.VISIBLE
                        certTextView!!.visibility = View.VISIBLE
                    }
                }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        if (ApplicationWideMessageHolder.getInstance().messageType != null) {
            when (ApplicationWideMessageHolder.getInstance().messageType
                ) {
                ApplicationWideMessageHolder.MessageType.ACCOUNT_SCHEDULED_FOR_DELETION -> {
                    textFieldBoxes!!.setError(
                            resources!!.getString(R.string.nc_account_scheduled_for_deletion),
                            false)
                    ApplicationWideMessageHolder.getInstance().messageType = null
                }
                ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK -> {
                    textFieldBoxes!!.setError(resources!!.getString(R.string.nc_settings_no_talk_installed),
                            false)
                }
                ApplicationWideMessageHolder.MessageType.FAILED_TO_IMPORT_ACCOUNT -> {
                    textFieldBoxes!!.setError(
                            resources!!.getString(R.string.nc_server_failed_to_import_account),
                            false)
                }
            }
            ApplicationWideMessageHolder.getInstance().messageType = null
        }
        setCertTextView()
    }

    private fun setCertTextView() {
        if (activity != null) {
            activity!!.runOnUiThread {
                if (!TextUtils.isEmpty(appPreferences.temporaryClientCertAlias)) {
                    certTextView!!.setText(R.string.nc_change_cert_auth)
                } else {
                    certTextView!!.setText(R.string.nc_configure_cert_auth)
                }
                textFieldBoxes!!.setError("", true)
                toggleProceedButton(true)
            }
        }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        if (activity != null) {
            activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    companion object {
        const val TAG = "ServerSelectionController"
    }
}