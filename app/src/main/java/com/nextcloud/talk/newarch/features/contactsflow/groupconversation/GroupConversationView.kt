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

package com.nextcloud.talk.newarch.features.contactsflow.groupconversation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.archlifecycle.ControllerLifecycleOwner
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.newarch.features.contactsflow.ContactsViewOperationState
import com.nextcloud.talk.newarch.features.contactsflow.contacts.ContactsView
import com.nextcloud.talk.newarch.mvvm.BaseView
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import kotlinx.android.synthetic.main.new_group_conversation_view.view.*
import org.koin.android.ext.android.inject

class GroupConversationView : BaseView() {
    override val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)
    override val lifecycleOwner = ControllerLifecycleOwner(this)

    private lateinit var viewModel: GroupConversationViewModel
    val factory: GroupConversationViewModelFactory by inject()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        setHasOptionsMenu(true)
        viewModel = viewModelProvider(factory).get(GroupConversationViewModel::class.java)
        val view = super.onCreateView(inflater, container)

        view.apply {
            conversationNameInputEditText.doOnTextChanged { text, start, count, after ->
                floatingActionButton?.isVisible = !text.isNullOrBlank()
            }

            allowGuestsSwitchMaterial.setOnCheckedChangeListener { buttonView, isChecked ->
                passwordTextInputLayout.isVisible = isChecked
            }
        }

        viewModel.operationState.observe(this, Observer { operationState ->
            when (operationState.operationState) {
                ContactsViewOperationState.WAITING -> {
                    // do nothing, just sit there and wait
                }
                ContactsViewOperationState.PROCESSING -> {
                    view.passwordInputEditText.isEnabled = false
                    view.conversationNameInputEditText.isEnabled = false
                    view.allowGuestsSwitchMaterial.isEnabled = false
                    toolbarProgressBar?.isVisible = true

                }
                ContactsViewOperationState.OK -> {
                    val bundle = Bundle()
                    bundle.putString(BundleKeys.KEY_CONVERSATION_TOKEN, operationState.conversationToken)
                    bundle.putBoolean(BundleKeys.KEY_NEW_GROUP_CONVERSATION, true)
                    router.replaceTopController(RouterTransaction.with(ContactsView(bundle))
                            .popChangeHandler(HorizontalChangeHandler())
                            .pushChangeHandler(HorizontalChangeHandler()))
                }
                else -> {
                    // we should do something else as well, but this will do for now
                    // we failed, I'm afraid :(
                    toolbarProgressBar?.isVisible = false
                    view.passwordInputEditText.isEnabled = true
                    view.conversationNameInputEditText.isEnabled = true
                    view.allowGuestsSwitchMaterial.isEnabled = true
                }
            }
        })

        return view
    }

    override fun getLayoutId(): Int {
        return R.layout.new_group_conversation_view
    }

    override fun getTitle(): String? {
        return context.getString(R.string.nc_new_group)
    }

    override fun onFloatingActionButtonClick() {
        view?.conversationNameInputEditText?.text?.let { conversationName ->
            val conversationType = if (view?.allowGuestsSwitchMaterial?.isChecked == true) 3 else 2
            viewModel.createConversation(conversationType, conversationName.toString(), view?.passwordInputEditText?.text?.toString())
        }
    }

    override fun getFloatingActionButtonDrawableRes(): Int {
        return R.drawable.ic_arrow_forward_white_24px
    }
}
