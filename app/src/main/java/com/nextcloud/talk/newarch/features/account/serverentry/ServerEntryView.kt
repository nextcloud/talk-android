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

package com.nextcloud.talk.newarch.features.account.serverentry

import android.os.Bundle
import android.view.*
import androidx.core.view.isInvisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.archlifecycle.ControllerLifecycleOwner
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.newarch.features.account.loginentry.LoginEntryView
import com.nextcloud.talk.newarch.mvvm.BaseView
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import kotlinx.android.synthetic.main.server_entry_view.view.*
import org.koin.android.ext.android.inject

class ServerEntryView : BaseView() {
    override val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)
    override val lifecycleOwner = ControllerLifecycleOwner(this)

    private lateinit var viewModel: ServerEntryViewModel
    val factory: ServerEntryViewModelFactory by inject()

    override fun getLayoutId(): Int {
        return R.layout.server_entry_view
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        viewModel = viewModelProvider(factory).get(ServerEntryViewModel::class.java)
        val view = super.onCreateView(inflater, container)

        view.serverEntryTextInputLayout.endIconDrawable?.alpha = 99
        view.host_url_input_helper_text.setText(String.format(resources!!.getString(R.string.nc_server_helper_text), resources?.getString(R.string.nc_server_product_name)))

        viewModel.apply {
            checkState.observe(this@ServerEntryView, Observer {
                when (it.checkState) {
                    ServerEntryCapabilitiesCheckState.WAITING_FOR_INPUT -> {
                        view.serverEntryTextInputLayout.isEnabled = true
                        view.serverEntryProgressBar.isInvisible = true
                    }
                    ServerEntryCapabilitiesCheckState.CHECKING -> {
                        view.serverEntryTextInputLayout.isEnabled = false
                        view.serverEntryTextInputEditText.compoundDrawables[2].alpha = 0
                        view.serverEntryProgressBar.isInvisible = false
                        view.error_text.isInvisible = true
                    }
                    ServerEntryCapabilitiesCheckState.SERVER_SUPPORTED -> {
                        val bundle = Bundle()
                        bundle.putString(BundleKeys.KEY_BASE_URL, it.url)
                        router.pushController(RouterTransaction.with(LoginEntryView(bundle))
                                .popChangeHandler(HorizontalChangeHandler()).pushChangeHandler(HorizontalChangeHandler()))
                    }
                    // Unsupported
                    else -> {
                        view.serverEntryTextInputLayout.isEnabled = true
                        view.serverEntryProgressBar.isInvisible = true
                        view.error_text.isInvisible = false
                        view.serverEntryTextInputLayout.endIconDrawable?.alpha = 99
                    }
                }
            })
        }

        view.serverEntryTextInputEditText.doOnTextChanged { text, start, count, after ->
            view.serverEntryTextInputLayout.error = null

            if (text.isNullOrBlank()) {
                view.serverEntryTextInputLayout.endIconDrawable?.alpha = 99
            } else {
                view.serverEntryTextInputLayout.endIconDrawable?.alpha = 255
            }
        }

        view.serverEntryTextInputLayout.setEndIconOnClickListener {
            view.serverEntryTextInputEditText?.text?.let { serverUrl ->
                var baseUrl = serverUrl.toString()
                if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                    baseUrl = "https://$serverUrl"
                }
                viewModel.fetchCapabilities(baseUrl)
                true
            }
        }

        return view
    }

    override fun getAppBarLayoutType(): AppBarLayoutType {
        return AppBarLayoutType.EMPTY
    }

    override fun onAttach(view: View) {
        super.onAttach(view)

        DisplayUtils.applyColorToStatusBar(activity!!, resources!!.getColor(R.color.colorPrimary))
        DisplayUtils.applyColorToNavgiationBar(activity!!.window, resources!!.getColor(R.color.colorPrimary))
    }
}
