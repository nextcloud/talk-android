/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.DialogChooseAccountShareToBinding
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.ui.dialog.viewmodels.ChooseAccountShareToViewModel
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import java.net.CookieManager
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ChooseAccountShareToDialogFragment : DialogFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var cookieManager: CookieManager

    private var binding: DialogChooseAccountShareToBinding? = null
    private var dialogView: View? = null

    private lateinit var viewModel: ChooseAccountShareToViewModel
    private lateinit var adapter: ChooseAccountShareToAdapter

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogChooseAccountShareToBinding.inflate(layoutInflater)
        dialogView = binding!!.root
        return MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)

        viewModel = ViewModelProvider(this, viewModelFactory)[ChooseAccountShareToViewModel::class.java]

        themeViews()
        setupAdapter()
        initObservers()

        viewModel.loadUsers()
    }

    private fun setupAdapter() {
        adapter = ChooseAccountShareToAdapter { user -> viewModel.switchToUser(user) }
        binding!!.accountsList.adapter = adapter
    }

    private fun initObservers() {
        viewModel.viewState.observe(this) { state ->
            when (state) {
                is ChooseAccountShareToViewModel.LoadUsersSuccessState -> {
                    setupCurrentUser()
                    adapter.submitList(state.users)
                }
                is ChooseAccountShareToViewModel.SwitchUserSuccessState -> {
                    cookieManager.cookieStore.removeAll()
                    activity?.recreate()
                    dismiss()
                }
                else -> {}
            }
        }
    }

    private fun setupCurrentUser() {
        val currentAccount = binding!!.currentAccount
        val user = viewModel.currentUser
        if (user != null) {
            currentAccount.userIcon.tag = ""
            currentAccount.userName.text = user.displayName
            currentAccount.ticker.visibility = View.GONE
            currentAccount.account.text = user.baseUrl!!.toUri().host
            viewThemeUtils.platform.colorImageView(currentAccount.accountMenu, ColorRole.PRIMARY)
            if (user.baseUrl != null &&
                (user.baseUrl!!.startsWith("http://") || user.baseUrl!!.startsWith("https://"))
            ) {
                currentAccount.userIcon.loadUserAvatar(user, user.userId!!, true, false)
            } else {
                currentAccount.userIcon.visibility = View.INVISIBLE
            }
        }
        currentAccount.root.setOnClickListener { dismiss() }
    }

    private fun themeViews() {
        viewThemeUtils.platform.themeDialog(binding!!.root)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        dialogView

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        val TAG = ChooseAccountShareToDialogFragment::class.java.simpleName
        fun newInstance(): ChooseAccountShareToDialogFragment = ChooseAccountShareToDialogFragment()
    }
}
