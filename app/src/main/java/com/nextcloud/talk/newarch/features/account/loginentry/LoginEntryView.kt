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

package com.nextcloud.talk.newarch.features.account.loginentry

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseView
import com.nextcloud.talk.newarch.features.conversationslist.ConversationsListView
import com.nextcloud.talk.utils.bundle.BundleKeys
import de.cotech.hw.fido.WebViewFidoBridge
import kotlinx.android.synthetic.main.login_entry_view.view.*
import org.koin.android.ext.android.inject
import java.util.*

class LoginEntryView(val bundle: Bundle) : BaseView() {
    private val protocolSuffix = "://"
    private val dataSeparator = ":"

    private lateinit var viewModel: LoginEntryViewModel
    val factory: LoginEntryViewModelFactory by inject()

    private var assembledPrefix = ""

    private val webLoginUserAgent: String
        get() = (Build.MANUFACTURER.substring(0, 1).toUpperCase(
                Locale.getDefault()) +
                Build.MANUFACTURER.substring(1).toLowerCase(
                        Locale.getDefault()) + " " + Build.MODEL + " ("
                + resources!!.getString(R.string.nc_app_name) + ")")

    override fun getLayoutId(): Int {
        return R.layout.login_entry_view
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        viewModel = viewModelProvider(factory).get(LoginEntryViewModel::class.java)
        val view = super.onCreateView(inflater, container)

        appBar?.isVisible = false
        actionBar?.hide()

        assembledPrefix = resources?.getString(R.string.nc_talk_login_scheme) + protocolSuffix + "login/"

        viewModel.state.observe(this@LoginEntryView, Observer {
            when (it.state) {
                LoginEntryState.FAILED -> {
                    router.popController(this)
                }
                LoginEntryState.PENDING_CHECK -> {
                    // everything is already setup in XML
                }
                LoginEntryState.CHECKING -> {
                    view.progressBar.isVisible = true
                    view.webView.isVisible = false
                }
                else -> {
                    router.setRoot(RouterTransaction.with(ConversationsListView())
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler()))
                }
            }
        })



        val baseUrl = bundle.get(BundleKeys.KEY_BASE_URL)
        val headers: MutableMap<String, String> = hashMapOf()
        headers["OCS-APIRequest"] = "true"

        setupWebView(view)
        view.webView.loadUrl("$baseUrl/index.php/login/flow", headers)

        return view
    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        view.webView.saveState(outState)
        super.onSaveViewState(view, outState)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        view.webView.restoreState(savedViewState)
    }

    private fun setupWebView(loginEntryView: View) {
        loginEntryView.webView.apply {
            settings.allowFileAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.javaScriptEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.domStorageEnabled = true
            settings.userAgentString = webLoginUserAgent
            settings.saveFormData = false
            settings.savePassword = false
            settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
            clearCache(true)
            clearFormData()
            clearHistory()
            clearSslPreferences()
        }

        val webViewFidoBridge = WebViewFidoBridge.createInstanceForWebView(activity as AppCompatActivity?, loginEntryView.webView)
        CookieSyncManager.createInstance(activity)
        CookieManager.getInstance().removeAllCookies(null)

        loginEntryView.webView.webViewClient = object : WebViewClient() {
            var initialPageLoad = true
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                webViewFidoBridge?.delegateShouldInterceptRequest(view, request)
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request?.url.toString().startsWith(assembledPrefix)) {
                    viewModel.parseData(assembledPrefix, dataSeparator, request?.url.toString())
                    return true
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (initialPageLoad) {
                    initialPageLoad = false
                    loginEntryView.progressBar?.isVisible = false
                    loginEntryView.webView?.isVisible = true
                }
                super.onPageFinished(view, url)
            }
        }
    }

}