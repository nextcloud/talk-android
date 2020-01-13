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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import com.nextcloud.talk.R
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseView
import kotlinx.android.synthetic.main.server_entry_view.view.*
import org.koin.android.ext.android.inject

class ServerEntryView : BaseView() {
    private lateinit var viewModel: ServerEntryViewModel
    val factory: ServerEntryVideModelFactory by inject()

    override fun getLayoutId(): Int {
        return R.layout.server_entry_view
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        actionBar?.hide()
        viewModel = viewModelProvider(factory).get(ServerEntryViewModel::class.java)
        val view = super.onCreateView(inflater, container)

        viewModel.apply {
            checkState.observe(this@ServerEntryView, Observer {
                when(it) {
                    ServerEntryCapabilitiesCheckState.WAITING_FOR_INPUT -> {
                        view.serverEntryTextInputLayout.isEnabled = true
                        view.serverEntryProgressBar.isVisible = false
                        view.serverEntryTextInputEditText.setCompoundDrawablesRelative(null, null, resources?.getDrawable(R.drawable.ic_arrow_forward_white_24px), null)
                    }
                    ServerEntryCapabilitiesCheckState.CHECKING -> {
                        view.serverEntryTextInputLayout.isEnabled = false
                        view.serverEntryTextInputEditText.setCompoundDrawablesRelative(null, null, null, null)
                        view.serverEntryProgressBar.isVisible = true
                    }
                    ServerEntryCapabilitiesCheckState.SERVER_SUPPORTED -> {

                    }
                    // Unsupported
                    else -> {
                        view.serverEntryTextInputLayout.isEnabled = true
                        view.serverEntryProgressBar.isVisible = false
                        view.serverEntryTextInputEditText.setCompoundDrawablesRelative(null, null, resources?.getDrawable(R.drawable.ic_arrow_forward_white_24px), null)
                    }
                }
            })
        }

        return view
    }
}