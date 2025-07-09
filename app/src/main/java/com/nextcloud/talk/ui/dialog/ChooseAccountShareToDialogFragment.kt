/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 + SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
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
import androidx.recyclerview.widget.LinearLayoutManager
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.adapters.items.AdvancedUserItem
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.DialogChooseAccountShareToBinding
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import java.net.CookieManager
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ChooseAccountShareToDialogFragment : DialogFragment() {
    @JvmField
    @Inject
    var userManager: UserManager? = null

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    @JvmField
    @Inject
    var cookieManager: CookieManager? = null

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils
    private var binding: DialogChooseAccountShareToBinding? = null
    private var dialogView: View? = null
    private var adapter: FlexibleAdapter<AdvancedUserItem>? = null
    private val userItems: MutableList<AdvancedUserItem> = ArrayList()

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogChooseAccountShareToBinding.inflate(layoutInflater)
        dialogView = binding!!.root
        return MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        val user = currentUserProvider.currentUser.blockingGet()
        themeViews()
        setupCurrentUser(user)
        setupListeners(user)
        setupAdapter()
        prepareViews()
    }

    private fun setupCurrentUser(user: User?) {
        binding!!.currentAccount.userIcon.tag = ""
        if (user != null) {
            binding!!.currentAccount.userName.text = user.displayName
            binding!!.currentAccount.ticker.visibility = View.GONE
            binding!!.currentAccount.account.text = user.baseUrl!!.toUri().host
            viewThemeUtils!!.platform.colorImageView(binding!!.currentAccount.accountMenu, ColorRole.PRIMARY)
            if (user.baseUrl != null &&
                (user.baseUrl!!.startsWith("http://") || user.baseUrl!!.startsWith("https://"))
            ) {
                binding!!.currentAccount.userIcon.loadUserAvatar(user, user.userId!!, true, false)
            } else {
                binding!!.currentAccount.userIcon.visibility = View.INVISIBLE
            }
        }
    }

    @Suppress("Detekt.NestedBlockDepth")
    private fun setupAdapter() {
        if (adapter == null) {
            adapter = FlexibleAdapter(userItems, activity, false)
            var userEntity: User
            var participant: Participant
            for (userItem in userManager!!.users.blockingGet()) {
                userEntity = userItem
                if (!userEntity.current) {
                    var userId: String?
                    userId = if (userEntity.userId != null) {
                        userEntity.userId
                    } else {
                        userEntity.username
                    }
                    participant = Participant()
                    participant.actorType = Participant.ActorType.USERS
                    participant.actorId = userId
                    participant.displayName = userEntity.displayName
                    userItems.add(AdvancedUserItem(participant, userEntity, null, viewThemeUtils, 0))
                }
            }
            adapter!!.addListener(onSwitchItemClickListener)
            adapter!!.updateDataSet(userItems, false)
        }
    }

    private fun setupListeners(user: User) {
        binding!!.currentAccount.root.setOnClickListener { v: View? -> dismiss() }
    }

    private fun themeViews() {
        viewThemeUtils!!.platform.themeDialog(binding!!.root)
    }

    private fun prepareViews() {
        if (activity != null) {
            val layoutManager: LinearLayoutManager = SmoothScrollLinearLayoutManager(activity)
            binding!!.accountsList.layoutManager = layoutManager
        }
        binding!!.accountsList.setHasFixedSize(true)
        binding!!.accountsList.adapter = adapter
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        dialogView

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private val onSwitchItemClickListener = FlexibleAdapter.OnItemClickListener { view, position ->
        if (userItems.size > position) {
            val user = userItems[position].user
            if (userManager!!.setUserAsActive(user!!).blockingGet()) {
                cookieManager!!.cookieStore.removeAll()
                activity?.recreate()
                dismiss()
            }
        }
        true
    }

    companion object {
        val TAG = ChooseAccountShareToDialogFragment::class.java.simpleName
        fun newInstance(): ChooseAccountShareToDialogFragment = ChooseAccountShareToDialogFragment()
    }
}
