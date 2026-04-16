/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chooseaccount

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.ui.chooseaccount.model.LoadUsersSuccessStateChooseAccountShareTo
import com.nextcloud.talk.ui.chooseaccount.model.SwitchUserSuccessStateChooseAccountShareTo
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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

    private var composeView: ComposeView? = null
    private lateinit var viewModel: ChooseAccountShareToViewModel

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        composeView = ComposeView(requireContext())
        return MaterialAlertDialogBuilder(requireContext()).setView(composeView).create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        composeView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        NextcloudTalkApplication.Companion.sharedApplication!!.componentApplication.inject(this)

        viewModel = ViewModelProvider(this, viewModelFactory)[ChooseAccountShareToViewModel::class.java]

        val otherUsers = mutableStateOf<List<User>>(emptyList())

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.chooseAccountShareToViewState.collectLatest { state ->
                    when (state) {
                        is LoadUsersSuccessStateChooseAccountShareTo -> {
                            otherUsers.value = state.users
                        }
                        is SwitchUserSuccessStateChooseAccountShareTo -> {
                            cookieManager.cookieStore.removeAll()
                            activity?.recreate()
                            dismiss()
                        }
                        else -> {}
                    }
                }
            }
        }

        val colorScheme = viewThemeUtils.getColorScheme(requireActivity())

        composeView?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(colorScheme = colorScheme) {
                    ChooseAccountShareToContent(
                        currentUser = viewModel.currentUser,
                        otherUsers = otherUsers.value,
                        onCurrentUserClick = { dismiss() },
                        onOtherUserClick = { user -> viewModel.switchToUser(user) }
                    )
                }
            }
        }

        viewModel.loadUsers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        composeView = null
    }

    companion object {
        const val TAG = "ChooseAccountShareToDialogFragment"
        fun newInstance(): ChooseAccountShareToDialogFragment = ChooseAccountShareToDialogFragment()
    }
}
