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

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseView
import com.nextcloud.talk.newarch.features.conversationslist.ConversationsListView
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.android.synthetic.main.login_entry_view.view.*
import org.koin.android.ext.android.inject
import org.mozilla.geckoview.*
import org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE
import java.util.*

class LoginEntryView(val bundle: Bundle) : BaseView() {
    private val protocolSuffix = "://"
    private val dataSeparator = ":"

    private lateinit var viewModel: LoginEntryViewModel
    val factory: LoginEntryViewModelFactory by inject()

    private lateinit var geckoView: GeckoView
    private lateinit var geckoSession: GeckoSession
    private val geckoRuntime: GeckoRuntime by inject()

    private val assembledPrefix = resources?.getString(R.string.nc_talk_login_scheme) + protocolSuffix + "login/"

    private val webLoginUserAgent: String
        get() = (Build.MANUFACTURER.substring(0, 1).toUpperCase(
                Locale.getDefault()) +
                Build.MANUFACTURER.substring(1).toLowerCase(
                        Locale.getDefault()) + " " + Build.MODEL + " ("
                + resources!!.getString(R.string.nc_app_name) + ")")

    override fun getLayoutId(): Int {
        return R.layout.login_entry_view
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        actionBar?.hide()
        viewModel = viewModelProvider(factory).get(LoginEntryViewModel::class.java)
        val view = super.onCreateView(inflater, container)

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
                    geckoView.isVisible = false
                }
                else -> {
                    if (router?.hasRootController() == true) {
                        router.popController(this)
                    } else {
                        router.setRoot(RouterTransaction.with(ConversationsListView())
                                .pushChangeHandler(HorizontalChangeHandler())
                                .popChangeHandler(HorizontalChangeHandler()))
                    }
                    // all good, proceed
                }
            }
        })

        geckoView = view.geckoView
        activity?.let {
            val settings = GeckoSessionSettings.Builder()
                    //.usePrivateMode(true)
                    //.useTrackingProtection(true)
                    .userAgentMode(USER_AGENT_MODE_MOBILE)
                    .userAgentOverride(webLoginUserAgent)
                    .suspendMediaWhenInactive(true)
                    .allowJavascript(true)

            geckoView.autofillEnabled = true
            geckoSession = GeckoSession(settings.build())
            geckoSession.open(geckoRuntime)
            geckoSession.progressDelegate = createProgressDelegate()
            geckoSession.navigationDelegate = createNavigationDelegate()
            geckoView.setSession(geckoSession)
            bundle.getString(BundleKeys.KEY_BASE_URL)?.let { baseUrl ->
                geckoSession.loadUri("$baseUrl/index.php/login/flow", mapOf<String, String>("OCS-APIRequest" to "true"))
            }
        }

        return view
    }

    private fun createNavigationDelegate(): GeckoSession.NavigationDelegate {
        return object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(p0: GeckoSession, p1: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                if (p1.uri.startsWith(assembledPrefix)) {
                    viewModel.parseData(assembledPrefix, dataSeparator, p1.uri)
                    return GeckoResult.DENY
                }
                return super.onLoadRequest(p0, p1)
            }
        }
    }

    private fun createProgressDelegate(): GeckoSession.ProgressDelegate {
        return object : GeckoSession.ProgressDelegate {
            private var initialLoad = true

            override fun onPageStop(session: GeckoSession, success: Boolean) = Unit

            override fun onSecurityChange(
                    session: GeckoSession,
                    securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
            ) = Unit

            override fun onPageStart(session: GeckoSession, url: String) = Unit

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                if (initialLoad) {
                    view?.pageProgressBar?.progress = progress
                    view?.pageProgressBar?.isVisible = progress in 1..99
                }

                if (progress == 100) {
                    initialLoad = false
                    view?.pageProgressBar?.isVisible = false
                    view?.geckoView?.isVisible = true
                }
            }
        }
    }
}