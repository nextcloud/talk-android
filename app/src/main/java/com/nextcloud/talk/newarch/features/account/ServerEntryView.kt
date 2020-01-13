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

package com.nextcloud.talk.newarch.features.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseView
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.android.synthetic.main.server_entry_view.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class ServerEntryView : BaseView() {
    private lateinit var viewModel: ServerEntryViewModel
    val factory: ServerEntryViewModelFactory by inject()

    override fun getLayoutId(): Int {
        return R.layout.server_entry_view
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        actionBar?.hide()
        viewModel = viewModelProvider(factory).get(ServerEntryViewModel::class.java)
        val view = super.onCreateView(inflater, container)

        view.serverEntryTextInputEditText.setCompoundDrawablesWithIntrinsicBounds(null, null, resources?.getDrawable(R.drawable.ic_arrow_forward_white_24px), null)
        view.serverEntryTextInputEditText.compoundDrawables[2].alpha = 99

        viewModel.apply {
            checkState.observe(this@ServerEntryView, Observer {
                when(it.checkState) {
                    ServerEntryCapabilitiesCheckState.WAITING_FOR_INPUT -> {
                        view.serverEntryTextInputLayout.isEnabled = true
                        view.serverEntryProgressBar.isVisible = false
                    }
                    ServerEntryCapabilitiesCheckState.CHECKING -> {
                        view.serverEntryTextInputLayout.isEnabled = false
                        view.serverEntryTextInputEditText.compoundDrawables[2].alpha = 0
                        view.serverEntryProgressBar.isVisible = true
                    }
                    ServerEntryCapabilitiesCheckState.SERVER_SUPPORTED -> {
                        val bundle = Bundle()
                        bundle.putString(BundleKeys.KEY_BASE_URL, it.url)
                        //router.pushController(RouterTransaction.with(LoginEntryView(bundle)).popChangeHandler(HorizontalChangeHandler()).pushChangeHandler(HorizontalChangeHandler()))
                    }
                    // Unsupported
                    else -> {
                        view.serverEntryTextInputLayout.isEnabled = true
                        view.serverEntryProgressBar.isVisible = false
                        view.serverEntryTextInputLayout.error = resources?.getString(R.string.nc_server_unsupported)
                        view.serverEntryTextInputEditText.compoundDrawables[2].alpha = 99
                    }
                }
            })
        }

        view.serverEntryTextInputEditText.doOnTextChanged { text, start, count, after ->
            view.serverEntryTextInputLayout.error = null

            if (text.isNullOrBlank()) {
                view.serverEntryTextInputEditText.compoundDrawables[2].alpha = 99
            } else {
                view.serverEntryTextInputEditText.compoundDrawables[2].alpha = 255
            }
        }

        view.serverEntryTextInputEditText.setOnTouchListener { v, event ->
            val drawableLeft = 0
            val drawableTop = 1
            val drawableRight = 2
            val drawableBottom = 3

            if(event.action == MotionEvent.ACTION_UP) {
                if(event.rawX >= (view.serverEntryTextInputEditText.right - view.serverEntryTextInputEditText.compoundDrawables[drawableRight].bounds.width())) {
                    if (view.serverEntryTextInputEditText.compoundDrawables[drawableRight].alpha == 255) {
                        view.serverEntryTextInputEditText?.text?.let { serverUrl ->
                            var baseUrl = serverUrl.toString()
                            if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                                baseUrl = "https://$serverUrl"
                            }
                            viewModel.fetchCapabilities(baseUrl)
                            true
                        }
                    }
                }
            }

            false
        }

        return view
    }
}