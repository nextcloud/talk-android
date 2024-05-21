/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.invitation

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityInvitationsBinding
import com.nextcloud.talk.invitation.adapters.InvitationsAdapter
import com.nextcloud.talk.invitation.data.ActionEnum
import com.nextcloud.talk.invitation.data.Invitation
import com.nextcloud.talk.invitation.viewmodels.InvitationsViewModel
import com.nextcloud.talk.utils.bundle.BundleKeys
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class InvitationsActivity : BaseActivity() {

    private lateinit var binding: ActivityInvitationsBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var invitationsViewModel: InvitationsViewModel

    lateinit var adapter: InvitationsAdapter

    private lateinit var currentUser: User

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val intent = Intent(this@InvitationsActivity, ConversationsListActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        invitationsViewModel = ViewModelProvider(this, viewModelFactory)[InvitationsViewModel::class.java]

        currentUser = currentUserProvider.currentUser.blockingGet()
        invitationsViewModel.fetchInvitations(currentUser)

        binding = ActivityInvitationsBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        setupSystemColors()

        adapter = InvitationsAdapter(currentUser) { invitation, action ->
            handleInvitation(invitation, action)
        }

        binding.invitationsRecyclerView.adapter = adapter

        initObservers()

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    enum class InvitationAction {
        ACCEPT,
        REJECT
    }

    private fun handleInvitation(invitation: Invitation, action: InvitationAction) {
        when (action) {
            InvitationAction.ACCEPT -> {
                invitationsViewModel.acceptInvitation(currentUser, invitation)
            }

            InvitationAction.REJECT -> {
                invitationsViewModel.rejectInvitation(currentUser, invitation)
            }
        }
    }

    private fun initObservers() {
        invitationsViewModel.fetchInvitationsViewState.observe(this) { state ->
            when (state) {
                is InvitationsViewModel.FetchInvitationsStartState -> {
                    binding.invitationsRecyclerView.visibility = View.GONE
                    binding.progressBarWrapper.visibility = View.VISIBLE
                }

                is InvitationsViewModel.FetchInvitationsSuccessState -> {
                    binding.invitationsRecyclerView.visibility = View.VISIBLE
                    binding.progressBarWrapper.visibility = View.GONE
                    adapter.submitList(state.invitations)
                }

                is InvitationsViewModel.FetchInvitationsEmptyState -> {
                    binding.invitationsRecyclerView.visibility = View.GONE
                    binding.progressBarWrapper.visibility = View.GONE

                    binding.emptyList.emptyListView.visibility = View.VISIBLE
                    binding.emptyList.emptyListViewHeadline.text = getString(R.string.nc_federation_no_invitations)
                    binding.emptyList.emptyListIcon.setImageResource(R.drawable.baseline_info_24)
                    binding.emptyList.emptyListIcon.visibility = View.VISIBLE
                    binding.emptyList.emptyListViewText.visibility = View.VISIBLE
                }

                is InvitationsViewModel.FetchInvitationsErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }

        invitationsViewModel.invitationActionViewState.observe(this) { state ->
            when (state) {
                is InvitationsViewModel.InvitationActionStartState -> {
                }

                is InvitationsViewModel.InvitationActionSuccessState -> {
                    if (state.action == ActionEnum.ACCEPT) {
                        val bundle = Bundle()
                        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, state.invitation.localToken)
                        val chatIntent = Intent(context, ChatActivity::class.java)
                        chatIntent.putExtras(bundle)
                        chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(chatIntent)
                    } else {
                        // adapter.currentList.remove(state.invitation)
                        // adapter.notifyDataSetChanged()  // leads to UnsupportedOperationException ?!

                        // Just reload activity as lazy workaround to not deal with adapter for now.
                        // Might be fine until switching to jetpack compose.
                        finish()
                        startActivity(intent)
                    }
                }

                is InvitationsViewModel.InvitationActionErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.invitationsToolbar)
        binding.invitationsToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(ColorDrawable(resources!!.getColor(R.color.transparent, null)))
        viewThemeUtils.material.themeToolbar(binding.invitationsToolbar)
    }
}
